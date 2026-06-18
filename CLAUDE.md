# lark-agent 项目规范

## 1. 项目定位
- 本项目是 `Lark + Gemini` 的群聊智能助手验证工程。
- 当前目标是保持消息收发链路稳定，并为后续能力扩展预留清晰边界。

## 2. 开发约束
- 严格遵守全局规范文件 `D:\develop\config\CLAUDE.md`。
- 任何新增渠道、模型、业务分支扩展，优先采用策略模式和工厂模式解耦，禁止堆叠 `if-else`。
- 敏感配置必须通过环境变量或外部配置注入，禁止明文提交密钥、口令、令牌。
- 变更前先读目标文件，变更后至少执行一次编译验证。

## 3. 当前架构
- 入口：`LarkAgentApplication`
- 渠道接入：`LarkChannelRunner`
- 模型接入：`GeminiClient`
- 领域提示词路由：`PromptRouter + PromptTemplates`
- 配置绑定：`AgentProperties + GeminiProperties`

## 4. 后续演进要求
- 渠道层、消息分发层、模型层逐步拆分，避免单类承担连接、消费、回复全流程。
- 模型接入保持单一实现，避免并行维护多套 Gemini 调用代码。
- 如需新增多模型支持，必须引入统一模型接口与工厂，不允许直接在调用方按模型名分支判断。
