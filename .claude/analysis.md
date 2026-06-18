# 项目状态快照

更新时间：2026-06-18

## 当前分支

- `master`

## 最近提交

- `ddd25df feat: gemini`

## 当前工作区状态

- `README.md` 已重写，内容与当前实现对齐。
- `LarkChannelRunner` 已迁移到 `application` 包，启动入口职责已收窄。
- `Gemini.java` 旧实现已删除，模型调用统一收口到 `GeminiClient`。
- 消息处理链已拆出：
  - `LarkChannelManager`
  - `ManagerEventDispatcher`
  - `MessageHandler`
  - `TextMessageHandler`
- 已新增上下文能力：
  - `LarkMessageQueryService`
  - `MessageContextAssembler`
- `target/` 仍在工作区中，尚未通过 `.gitignore` 清理。

## 当前结构判断

- 项目仍处于“验证型原型”阶段，但已从单类直连逻辑，演进到“渠道管理 + 分发 + Handler + 上下文组装 + 模型调用”的基础分层。
- 当前主链路已经是：
  - `Lark 长连接收消息`
  - `按消息类型分发`
  - `群聊引用链 / 私聊历史消息组装上下文`
  - `Gemini 生成回复`
  - `Lark 回写消息`
- 当前只处理文本消息，图片、文件、卡片等消息尚未进入上下文处理链。
- `src/test` 仍不存在，缺少自动化测试保障。

## 本轮已完成的关键改造

- 敏感配置已改成环境变量绑定，避免密钥继续明文落盘。
- `dispatch` 已改成命中单个 `MessageHandler` 后停止，避免未来多 Handler 时重复处理。
- `TextMessageHandler` 已改成按 `rawContentType == text` 判断。
- 群聊场景已支持沿引用链向上追溯上下文。
- 私聊场景已支持自动带入最近若干条历史消息。
- 关键上下文相关类已补齐 Javadoc 和中文注释，便于后续维护。

## 主要风险

- `PromptRouter` 仍是关键词匹配方案，适合当前验证，但不适合复杂能力扩展。
- 消息上下文聚合当前仅处理文本消息，富文本和其他消息类型后续仍需明确策略。
- `LarkMessageQueryService` 依赖真实 Lark 消息查询权限，若权限不足会降级，但实际效果仍需联调验证。
- 工作区存在较多未提交改动，且 `target/` 未清理，后续提交前需要整理。

## 下一步建议

- 为 `MessageContextAssembler` 和 `TextMessageHandler` 补最小单元测试。
- 补 `.gitignore`，避免 `target/` 持续进入工作区。
- 明确非文本消息的处理策略，是忽略、提示不支持，还是做结构化降级。
- 若后续接入多模型，新增统一模型接口和工厂，避免在调用方继续扩散分支判断。
