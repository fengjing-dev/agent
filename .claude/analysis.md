# lark-agent 项目分析

## 当前定位

`lark-agent` 是一个基于 Lark 长连接的智能助手验证项目。当前目标不是做复杂业务系统，而是把消息接收、上下文组装、模型调用、流式回写这条链路跑通，并逐步沉淀成可扩展的 Agent 基座。

当前主要场景：

- 群聊中通过 `@机器人` 触发回复。
- 群聊引用回复时，沿引用链补充上下文。
- 私聊中维护最近对话上下文，支持多轮问答。
- 支持 Gemini 和 DeepSeek 两类模型供应商。

## 当前架构

核心链路：

```text
Lark 长连接事件
-> ManagerEventDispatcher 分发事件
-> TextMessageHandler 处理文本消息
-> MessageContextAssembler 组装上下文
-> ModelClient 调用模型
-> LarkMessageReplyService 流式更新 Lark 卡片
```

关键组件：

- `LarkChannelRunner`：应用启动后建立 Lark 长连接。
- `LarkChannelManager`：负责长连接生命周期。
- `ManagerEventDispatcher`：接收标准化消息并选择处理器。
- `TextMessageHandler`：文本消息主流程，包含清空上下文、模型调用、记忆写入。
- `MessageContextAssembler`：负责群聊引用链和私聊上下文组装。
- `ConversationMemoryService`：本地滚动记忆。
- `ConversationCommandService`：识别 `/clear` 等上下文清除指令。
- `ModelClient`：模型调用抽象。
- `GeminiClient`：Gemini 实现。
- `DeepSeekClient`：DeepSeek OpenAI-compatible 实现。
- `LarkMessageReplyService`：回复文本和流式卡片。

## 已完成优化

### 依赖和启动

- 移除了不需要的 Web Starter，当前应用按非 Web 长连接服务运行。
- 增加了配置校验，避免关键 Lark 配置为空时静默失败。
- 恢复 Maven 默认源码布局，使 Java 测试能被正常发现。
- 已有基础单测覆盖私聊记忆、上下文组装和卡片输出。

### 模型抽象

- 从直接依赖 `GeminiClient` 改为依赖 `ModelClient`。
- 新增 `MODEL_PROVIDER` 配置，可选 `gemini` 或 `deepseek`。
- Gemini 和 DeepSeek 都保留独立配置。
- 模型 API Key 不再做全局强校验，只在对应 provider 启用时校验，避免切换 provider 后另一个 provider 的空 key 阻塞启动。

### Gemini 限流处理

- 增加请求间隔配置：
  - `GEMINI_MIN_REQUEST_INTERVAL_MS`
  - `GEMINI_RATE_LIMIT_COOLDOWN_MS`
- 触发 429 或配额错误后进入本地冷却窗口。
- 冷却期间直接返回“当前模型调用较频繁，请稍后再试”，避免持续打爆免费额度。

### DeepSeek 接入

- 使用 OpenAI-compatible `/chat/completions` 流式接口。
- 通过 SSE 解析 `choices[0].delta.content`。
- 支持本地请求间隔和限流冷却：
  - `DEEPSEEK_MIN_REQUEST_INTERVAL_MS`
  - `DEEPSEEK_RATE_LIMIT_COOLDOWN_MS`
- 默认模型为 `deepseek-v4-flash`。

### 私聊上下文

- 私聊首次冷启动从 Lark 历史消息补上下文。
- 后续优先使用本地滚动记忆，避免每次都查询历史。
- 本地记忆会保留用户消息和助手回复。
- 冷启动也会保留助手历史消息。
- 如果历史消息里存在最后一次清除指令，只保留清除之后的消息。
- 支持清除指令：
  - `/clear`
  - `clear`
  - `清空上下文`
  - `清除上下文`
  - `忘记前文`
  - `重置上下文`

### 助手卡片消息提取

- 冷启动历史中如果助手回复是 Lark 卡片 JSON，会提取可读文本，而不是把 JSON 原文塞进 prompt。
- 当前支持提取：
  - `tag=lark_md` 的 `content`
  - `tag=plain_text` 的 `content`
  - `tag=text` 的 `text`

### 流式展示

- 模型输出要求分为：
  - `【分析摘要】`
  - `【最终回答】`
- 卡片展示时，分析摘要用灰色字体展示。
- 进入最终回答后，不再继续显示分析摘要区域的新内容。
- 卡片顶部不再显示“正在生成”或“回答完成”标题。

## 当前配置入口

基础配置：

```powershell
$env:LARK_AGENT_APP_ID="你的 App ID"
$env:LARK_AGENT_APP_SECRET="你的 App Secret"
$env:MODEL_PROVIDER="gemini"
$env:GEMINI_API_KEY="你的 Gemini API Key"
```

切换 DeepSeek：

```powershell
$env:MODEL_PROVIDER="deepseek"
$env:DEEPSEEK_API_KEY="你的 DeepSeek API Key"
$env:DEEPSEEK_MODEL="deepseek-v4-flash"
```

上下文和限流：

```powershell
$env:LARK_AGENT_PRIVATE_CHAT_CONTEXT_SIZE="10"
$env:GEMINI_MIN_REQUEST_INTERVAL_MS="2000"
$env:GEMINI_RATE_LIMIT_COOLDOWN_MS="60000"
$env:DEEPSEEK_MIN_REQUEST_INTERVAL_MS="1000"
$env:DEEPSEEK_RATE_LIMIT_COOLDOWN_MS="60000"
```

## 当前风险点

### 本地记忆不是持久化存储

当前 `ConversationMemoryService` 使用本地内存。应用重启后，本地记忆会丢失，冷启动会重新从 Lark 历史拉取。虽然已经处理了 `/clear` 之后不带旧消息，但如果未来多实例部署，本地内存会导致不同实例上下文不一致。

后续建议改成 Redis：

- key 按 `chatId` 区分。
- value 存最近 N 条 `ConversationTurn`。
- 清除指令直接删除 key，并记录清除时间或清除标记。
- 可以设置 TTL，避免长期占用内存。

### 可见分析摘要依赖模型按格式输出

当前通过 prompt 要求模型输出 `【分析摘要】` 和 `【最终回答】`。如果模型不按格式输出，卡片解析仍能显示内容，但分析区和最终回答区的切换可能不理想。

后续可优化：

- 在 `LarkMessageReplyService` 中增强容错解析。
- 对没有 `【最终回答】` 的内容设置兜底策略。
- 不把分析摘要写入 memory，只存最终回答，这一点当前已经在做。

### DeepSeek 流式错误处理仍较基础

当前 DeepSeek 只处理 HTTP 状态码和标准 SSE content 增量。后续如果 DeepSeek 返回非标准错误体、网络中断或中途限流，需要更细的异常分类。

建议后续补充：

- 超时异常分类。
- 网络异常提示。
- DeepSeek 错误码解析。
- 中途流断开时的用户可见提示。

### 模型请求日志可能过长

当前 Gemini 和 DeepSeek 流式调用会记录完整 prompt。排查上下文问题时有用，但生产环境可能暴露敏感消息，也可能导致日志过大。

后续建议：

- 默认只记录 prompt 长度。
- 通过配置开关控制是否打印完整 prompt。
- 对用户消息做脱敏或截断。

### 群聊上下文仍偏弱

群聊目前主要依赖 `@机器人` 当前消息和引用链。没有做群聊级 rolling memory，这是合理的保守设计，但如果群聊里频繁多轮追问，用户必须引用回复才能稳定带上上下文。

后续可以考虑：

- 只对机器人参与过的 thread 做短期记忆。
- 群聊记忆按 `chatId + rootMessageId` 或 `chatId + threadId` 隔离。
- 避免全群消息混入一个上下文。

## 后续路线建议

### 优先级 P0

- 用 DeepSeek 真实 key 做一次端到端联调。
- 验证 Lark 卡片流式更新在 DeepSeek 输出下是否稳定。
- 验证 `MODEL_PROVIDER=deepseek` 时没有 Gemini API Key 也能启动。
- 验证 `MODEL_PROVIDER=gemini` 时没有 DeepSeek API Key 也能启动。

### 优先级 P1

- 增加模型请求日志开关。
- 给 DeepSeekClient 加单元测试，使用假 SSE 输入验证增量解析。
- 给 provider 切换加 Spring 上下文测试，避免条件 Bean 回归。
- Redis 化 `ConversationMemoryService`。

### 优先级 P2

- 用户端增加“清空上下文”菜单或快捷入口。
- 卡片上增加“清空上下文”按钮。
- 支持更多消息类型进入上下文，例如图片 OCR、文件摘要。
- 增加群聊 thread 级短期记忆。

## 验证记录

最近一次验证命令：

```powershell
mvn clean test
```

结果：

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 当前结论

项目已经从单一 Gemini 验证链路，演进为可配置模型供应商的 Lark Agent 基座。当前最值得继续投入的方向是：

1. 用 DeepSeek 做真实联调，解决 Gemini 免费额度过低的问题。
2. 把本地 rolling memory 抽到 Redis，解决重启和多实例一致性。
3. 加强流式输出和错误处理，让用户端体验更稳定。
