# lark-agent

`lark-agent` 是一个基于 Lark 长连接的智能助手验证项目。它负责接收 Lark 群聊或私聊消息，组装上下文，调用大模型，并把结果流式回写到 Lark。

当前已支持：

- Lark 长连接事件接入。
- 群聊 `@机器人` 后回复。
- 群聊引用链上下文追溯。
- 私聊 rolling memory 多轮上下文。
- `/clear` 等指令清空当前私聊上下文。
- Gemini 和 DeepSeek 两种模型供应商。
- Lark 卡片流式输出。
- 可见的灰色“分析摘要”和正式回答分段展示。

核心链路：

```text
Lark 收消息
-> 按消息类型分发
-> 组装上下文
-> 调用模型
-> 流式更新 Lark 卡片
```

## 环境要求

- Java 17
- Maven 3.9+
- 已创建 Lark 自建应用
- 已开启机器人和消息相关权限
- 事件订阅方式使用长连接
- 已准备至少一个模型供应商的 API Key

## 快速开始

### 使用 Gemini

```powershell
$env:LARK_AGENT_APP_ID="你的 App ID"
$env:LARK_AGENT_APP_SECRET="你的 App Secret"
$env:MODEL_PROVIDER="gemini"
$env:GEMINI_API_KEY="你的 Gemini API Key"
$env:GEMINI_MODEL="gemini-2.5-flash-lite"

mvn spring-boot:run
```

### 使用 DeepSeek

```powershell
$env:LARK_AGENT_APP_ID="你的 App ID"
$env:LARK_AGENT_APP_SECRET="你的 App Secret"
$env:MODEL_PROVIDER="deepseek"
$env:DEEPSEEK_API_KEY="你的 DeepSeek API Key"
$env:DEEPSEEK_MODEL="deepseek-v4-flash"

mvn spring-boot:run
```

默认配置文件位于 [application.yml](src/main/resources/application.yml)。

## 常用配置

### Lark 配置

```powershell
$env:LARK_AGENT_DOMAIN="https://open.larksuite.com"
$env:LARK_AGENT_REQUIRE_MENTION="true"
$env:LARK_AGENT_RESPOND_TO_MENTION_ALL="false"
$env:LARK_AGENT_GROUP_REPLY_CHAIN_DEPTH="5"
$env:LARK_AGENT_PRIVATE_CHAT_CONTEXT_SIZE="10"
```

- `LARK_AGENT_APP_ID`：Lark 应用 ID。
- `LARK_AGENT_APP_SECRET`：Lark 应用密钥。
- `LARK_AGENT_DOMAIN`：Lark 开放平台域名，默认 `https://open.larksuite.com`。
- `LARK_AGENT_REQUIRE_MENTION`：群聊中是否必须 `@机器人` 才响应，默认 `true`。
- `LARK_AGENT_RESPOND_TO_MENTION_ALL`：是否响应 `@所有人`，默认 `false`。
- `LARK_AGENT_GROUP_REPLY_CHAIN_DEPTH`：群聊引用链最大追溯层数，默认 `5`。
- `LARK_AGENT_PRIVATE_CHAT_CONTEXT_SIZE`：私聊带入模型的最近消息条数，默认 `10`。

### 模型配置

```powershell
$env:MODEL_PROVIDER="gemini"
```

- `MODEL_PROVIDER`：模型供应商，可选 `gemini` 或 `deepseek`，默认 `gemini`。

Gemini：

```powershell
$env:GEMINI_API_KEY="你的 Gemini API Key"
$env:GEMINI_MODEL="gemini-2.5-flash-lite"
$env:GEMINI_BASE_URL="https://generativelanguage.googleapis.com"
$env:GEMINI_MAX_RETRIES="2"
$env:GEMINI_INITIAL_RETRY_DELAY_MS="1000"
$env:GEMINI_MIN_REQUEST_INTERVAL_MS="2000"
$env:GEMINI_RATE_LIMIT_COOLDOWN_MS="60000"
```

DeepSeek：

```powershell
$env:DEEPSEEK_API_KEY="你的 DeepSeek API Key"
$env:DEEPSEEK_MODEL="deepseek-v4-flash"
$env:DEEPSEEK_BASE_URL="https://api.deepseek.com"
$env:DEEPSEEK_MAX_RETRIES="2"
$env:DEEPSEEK_INITIAL_RETRY_DELAY_MS="1000"
$env:DEEPSEEK_MIN_REQUEST_INTERVAL_MS="1000"
$env:DEEPSEEK_RATE_LIMIT_COOLDOWN_MS="60000"
$env:DEEPSEEK_TIMEOUT_SECONDS="120"
```

说明：

- 只有当前 `MODEL_PROVIDER` 对应的 API Key 会被强校验。
- Gemini 免费额度较低，频繁 429 时可以切换到 DeepSeek 或调大请求间隔。
- 触发限流后会进入本地冷却窗口，冷却期内直接提示用户稍后再试。

## 消息处理规则

### 群聊

- 当前只处理文本消息。
- 默认需要 `@机器人` 才会回复。
- 如果消息是引用回复，会沿引用链向上查询历史消息。
- 上下文会按时间正序拼接后发给模型。
- 群聊当前没有全局 rolling memory，建议通过引用回复保持上下文准确。

示例上下文：

```text
用户(张三): 昨天那个支付失败是什么原因？
助手(机器人): 日志显示是渠道超时。
用户(张三): 那要怎么补偿？
```

### 私聊

- 当前只处理文本消息。
- 首次会话会从 Lark 历史消息冷启动上下文。
- 后续优先使用本地 rolling memory，避免每次都查历史。
- 本地 memory 会保留用户消息和助手回复。
- 冷启动也会保留助手历史消息。
- 如果历史里存在最后一次清除指令，只保留清除之后的消息。
- 助手历史如果是 Lark 卡片 JSON，会提取纯文本再进入上下文。

支持的清除指令：

```text
/clear
clear
清空上下文
清除上下文
忘记前文
重置上下文
```

清除后，当前私聊的本地上下文会被移除，并且本次运行内不会再把旧 Lark 历史重新带入 memory。

## 流式输出

模型 prompt 要求输出两段：

```text
【分析摘要】
用 2-5 条简短要点说明判断依据、关键约束或推理方向。

【最终回答】
给出结论、方案或可执行步骤。
```

Lark 卡片展示规则：

- `【分析摘要】` 会以灰色文本流式展示。
- 进入 `【最终回答】` 后，只继续流式展示正式回答。
- 卡片顶部不显示“正在生成”或“回答完成”标题。
- 写入 memory 时只保存最终回答，不保存分析摘要。

## 当前架构

```text
LarkChannelRunner
-> LarkChannelManager
-> ManagerEventDispatcher
-> TextMessageHandler
-> MessageContextAssembler
-> ModelClient
   -> GeminiClient
   -> DeepSeekClient
-> LarkMessageReplyService
```

组件说明：

- `LarkChannelRunner`：应用启动后建立 Lark 长连接。
- `LarkChannelManager`：管理长连接生命周期。
- `ManagerEventDispatcher`：把标准化消息分发给匹配的处理器。
- `TextMessageHandler`：文本消息主流程，包含清空上下文、模型调用和 memory 写入。
- `MessageContextAssembler`：组装群聊引用链和私聊上下文。
- `ConversationMemoryService`：本地 rolling memory。
- `ConversationCommandService`：识别上下文清除指令。
- `ModelClient`：模型调用抽象。
- `GeminiClient`：Gemini SDK 实现。
- `DeepSeekClient`：DeepSeek OpenAI-compatible SSE 实现。
- `LarkMessageReplyService`：回复文本和流式卡片。

## 主要代码位置

- [LarkAgentApplication.java](src/main/java/com/lark/agent/LarkAgentApplication.java)
- [LarkChannelRunner.java](src/main/java/com/lark/agent/application/LarkChannelRunner.java)
- [LarkChannelManager.java](src/main/java/com/lark/agent/module/service/LarkChannelManager.java)
- [ManagerEventDispatcher.java](src/main/java/com/lark/agent/module/service/ManagerEventDispatcher.java)
- [TextMessageHandler.java](src/main/java/com/lark/agent/module/service/TextMessageHandler.java)
- [MessageContextAssembler.java](src/main/java/com/lark/agent/module/service/MessageContextAssembler.java)
- [ConversationMemoryService.java](src/main/java/com/lark/agent/module/service/ConversationMemoryService.java)
- [ConversationCommandService.java](src/main/java/com/lark/agent/module/service/ConversationCommandService.java)
- [LarkMessageReplyService.java](src/main/java/com/lark/agent/module/service/LarkMessageReplyService.java)
- [ModelClient.kt](src/main/kotlin/com/lark/agent/module/component/ModelClient.kt)
- [GeminiClient.kt](src/main/kotlin/com/lark/agent/module/component/GeminiClient.kt)
- [DeepSeekClient.kt](src/main/kotlin/com/lark/agent/module/component/DeepSeekClient.kt)
- [PromptRouter.kt](src/main/kotlin/com/lark/agent/module/constants/PromptRouter.kt)
- [PromptTemplates.kt](src/main/kotlin/com/lark/agent/module/constants/PromptTemplates.kt)

项目分析记录见 [.claude/analysis.md](.claude/analysis.md)。

## 联调验证

### 启动验证

```powershell
mvn clean test
mvn spring-boot:run
```

启动后确认日志中没有配置校验错误，并且长连接能正常建立。

### 群聊验证

1. 将机器人加入测试群。
2. 在群里发送 `@机器人 你好`。
3. 对机器人或其他人的上一条消息做引用回复提问。
4. 确认机器人能结合引用链内容回复。

### 私聊验证

1. 与机器人私聊连续发送几条文本消息。
2. 再发送一个需要依赖上文才能回答的问题。
3. 确认机器人能结合最近上下文回复。
4. 发送 `/clear`。
5. 再发送依赖旧上下文的问题，确认旧上下文不再生效。

### 模型切换验证

1. 设置 `MODEL_PROVIDER=gemini`，只配置 Gemini Key，确认能启动并回复。
2. 设置 `MODEL_PROVIDER=deepseek`，只配置 DeepSeek Key，确认能启动并回复。
3. 确认切换 provider 时，未启用 provider 的空 API Key 不会阻塞启动。

## 测试

```powershell
mvn clean test
```

当前已有测试覆盖：

- 私聊 rolling memory 截断。
- 冷启动历史上下文组装。
- `/clear` 后历史截断。
- 助手卡片 JSON 文本提取。
- Lark 卡片流式展示和最终回答提取。

最近一次验证结果：

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 注意事项

- 当前上下文聚合主要处理文本消息；图片、文件等消息暂未纳入模型上下文。
- 私聊 memory 当前保存在本地内存，应用重启后会丢失。
- 应用重启后会从 Lark 历史冷启动，并会跳过最后一次清除指令之前的历史。
- 多实例部署时，本地 memory 会导致实例间上下文不一致，后续建议替换为 Redis。
- Gemini 免费额度较低，频繁 429 时建议使用 DeepSeek 或启用付费额度。
- 当前会记录模型 prompt 长度，部分路径仍可能打印完整 prompt；生产环境建议后续增加日志开关和脱敏。

## 后续计划

- 用 DeepSeek 真实 Key 做端到端联调。
- 给 DeepSeek SSE 解析补单元测试。
- 增加模型请求日志开关。
- 将 `ConversationMemoryService` 替换为 Redis 实现。
- 用户端增加“清空上下文”菜单或卡片按钮。
- 增加群聊 thread 级短期记忆。
- 支持图片 OCR、文件摘要等更多上下文来源。
