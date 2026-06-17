# lark-agent

本项目用于本地验证 Lark 智能体长连接链路是否打通。

## 目标

群里 `@机器人` 后，机器人固定回复：`收到，长连接链路正常`。

## 环境要求

- Java 17
- Maven 3.9+
- 已在 Lark 开放平台为应用开通消息相关权限
- 事件订阅方式选择 `长连接`

## 配置

建议使用环境变量：

```powershell
$env:LARK_AGENT_APP_ID="你的 App ID"
$env:LARK_AGENT_APP_SECRET="你的 App Secret"
$env:LARK_REPLY_TEXT="收到，长连接链路正常"
```

也可以直接修改 `src/main/resources/application.yml` 中的 `lark.agent.app-id`、`lark.agent.app-secret`。

注意：

- 不要再使用旧变量名 `LARK_APP_ID`、`LARK_APP_SECRET`
- 如果仍保留 `replace-with-your-app-id` / `replace-with-your-app-secret` 占位值，程序会在启动前直接报错

## 启动

```powershell
mvn spring-boot:run
```

启动成功后，日志会出现“Lark 长连接已建立”。

## 验证步骤

1. 把机器人拉进一个内部测试群。
2. 在群里发送 `@机器人 你好`。
3. 如果机器人回复固定文案，说明长连接链路已经打通。

## 后续扩展

当前回复逻辑位于 `LarkChannelRunner#handleMessage`，后续可在这里接入大模型调用。
