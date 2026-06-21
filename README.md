# lark-agent

`lark-agent` 是一个基于 `Lark 长连接 + 可配置大模型` 的群聊/私聊智能助手验证项目。

当前实现重点不是复杂业务，而是先打通这条链路：

`Lark 收消息 -> 按消息类型分发 -> 组装上下文 -> 调用模型 -> 回写消息`

## 目标

- 在群聊中 `@机器人` 后，机器人能够收到消息并返回模型回复。
- 在群聊引用回复场景下，机器人能够把整条引用链整理后再提问模型。
- 在私聊场景下，机器人能够自动带上最近几条历史消息作为上下文。

## 环境要求

- Java 17
- Maven 3.9+
- 已在 Lark 开放平台完成应用配置
- 已开启消息相关权限
- 事件订阅方式使用长连接

## 配置

建议通过环境变量注入敏感配置：

```powershell
$env:LARK_AGENT_APP_ID="你的 App ID"
$env:LARK_AGENT_APP_SECRET="你的 App Secret"
$env:MODEL_PROVIDER="gemini"
$env:GEMINI_API_KEY="你的 Gemini API Key"
```

如果要改用 DeepSeek：

```powershell
$env:MODEL_PROVIDER="deepseek"
$env:DEEPSEEK_API_KEY="你的 DeepSeek API Key"
$env:DEEPSEEK_MODEL="deepseek-v4-flash"
```

常用可选配置：

```powershell
$env:LARK_AGENT_DOMAIN="https://open.larksuite.com"
$env:LARK_AGENT_REQUIRE_MENTION="true"
$env:LARK_AGENT_RESPOND_TO_MENTION_ALL="false"
$env:LARK_AGENT_GROUP_REPLY_CHAIN_DEPTH="5"
$env:LARK_AGENT_PRIVATE_CHAT_CONTEXT_SIZE="10"
$env:GEMINI_MODEL="gemini-2.5-flash-lite"
$env:GEMINI_BASE_URL="https://generativelanguage.googleapis.com"
$env:GEMINI_MIN_REQUEST_INTERVAL_MS="2000"
$env:GEMINI_RATE_LIMIT_COOLDOWN_MS="60000"
$env:DEEPSEEK_BASE_URL="https://api.deepseek.com"
$env:DEEPSEEK_MIN_REQUEST_INTERVAL_MS="1000"
$env:DEEPSEEK_RATE_LIMIT_COOLDOWN_MS="60000"
```

配置说明：

- `LARK_AGENT_GROUP_REPLY_CHAIN_DEPTH`
  群聊中沿引用链向上追溯的最大层数。
- `LARK_AGENT_PRIVATE_CHAT_CONTEXT_SIZE`
  私聊中自动带入模型的历史消息条数。
- `MODEL_PROVIDER`
  模型供应商，可选 `gemini` 或 `deepseek`，默认 `gemini`。
- `GEMINI_MIN_REQUEST_INTERVAL_MS`
  两次 Gemini 请求之间的最小间隔，用于降低 429 频率。
- `GEMINI_RATE_LIMIT_COOLDOWN_MS`
  Gemini 触发限流后的本地冷却时间，冷却期内不继续请求模型。
- `DEEPSEEK_MIN_REQUEST_INTERVAL_MS`
  两次 DeepSeek 请求之间的最小间隔。
- `DEEPSEEK_RATE_LIMIT_COOLDOWN_MS`
  DeepSeek 触发限流后的本地冷却时间，冷却期内不继续请求模型。

默认配置文件位于 [application.yml](src/main/resources/application.yml)。

## 启动

```powershell
mvn spring-boot:run
```

## 消息处理规则

### 群聊

- 当前只处理文本消息。
- 如果消息是对上一条消息的引用回复，会沿引用链继续向上追溯。
- 最终按时间正序整理成上下文，再把当前问题一起发给模型。

示例：

```text
用户(张三): 昨天那个支付失败是什么原因？
助手(机器人): 日志显示是渠道超时。
用户(张三): 那要怎么补偿？
```

### 私聊

- 当前只处理文本消息。
- 首次会话会读取最近若干条历史消息，后续优先使用本地滚动记忆。
- 冷启动和本地滚动记忆都会保留助手回复，并会从卡片 JSON 中提取纯文本。
- 发送 `/clear`、`清空上下文`、`清除上下文`、`忘记前文` 或 `重置上下文` 可清空当前私聊上下文。

## 当前架构

- 入口启动：`LarkChannelRunner`
- 长连接管理：`LarkChannelManager`
- 消息分发：`ManagerEventDispatcher`
- 文本消息处理：`TextMessageHandler`
- Lark 消息查询：`LarkMessageQueryService`
- 上下文组装：`MessageContextAssembler`
- 模型调用：`ModelClient`，当前实现包括 `GeminiClient` 和 `DeepSeekClient`
- Prompt 路由：`PromptRouter + PromptTemplates`

## 主要代码位置

- [LarkChannelRunner.java](src/main/java/com/lark/agent/application/LarkChannelRunner.java)
- [LarkChannelManager.java](src/main/java/com/lark/agent/module/service/LarkChannelManager.java)
- [ManagerEventDispatcher.java](src/main/java/com/lark/agent/module/service/ManagerEventDispatcher.java)
- [TextMessageHandler.java](src/main/java/com/lark/agent/module/service/TextMessageHandler.java)
- [MessageContextAssembler.java](src/main/java/com/lark/agent/module/service/MessageContextAssembler.java)
- [LarkMessageQueryService.java](src/main/java/com/lark/agent/module/service/LarkMessageQueryService.java)
- [ModelClient.kt](src/main/kotlin/com/lark/agent/module/component/ModelClient.kt)
- [GeminiClient.kt](src/main/kotlin/com/lark/agent/module/component/GeminiClient.kt)
- [DeepSeekClient.kt](src/main/kotlin/com/lark/agent/module/component/DeepSeekClient.kt)

## 联调验证

### 群聊验证

1. 将机器人加入测试群。
2. 在群里发送 `@机器人 你好`。
3. 再对机器人或其他人的上一条消息做引用回复提问。
4. 观察机器人是否能结合引用链内容回复。

### 私聊验证

1. 与机器人私聊连续发送几条文本消息。
2. 再发送一个需要依赖上文才能回答的问题。
3. 观察机器人是否结合最近历史消息回复。

## 注意事项

- 当前上下文聚合只处理文本消息，图片、文件、卡片等消息类型暂未纳入模型上下文。
- 群聊引用链和私聊历史消息都做了长度限制，避免 prompt 无限制膨胀。
- 私聊上下文当前保存在本地内存中，应用重启后会丢失；后续可替换为 Redis 等外部存储。
- 如果 Lark 消息查询权限不足，运行时会降级为尽量少带上下文，而不会中断主流程。
- 当前已有基础自动化测试覆盖私聊记忆、卡片渲染和上下文组装。
