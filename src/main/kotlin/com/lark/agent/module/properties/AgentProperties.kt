package com.lark.agent.module.properties

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Lark 长连接接入和上下文组装配置。
 */
@Validated
@ConfigurationProperties(prefix = "lark.agent")
class AgentProperties {

    /** Lark 应用 ID。 */
    @field:NotBlank(message = "lark.agent.app-id must not be blank")
    var appId: String? = null

    /** Lark 应用密钥。 */
    @field:NotBlank(message = "lark.agent.app-secret must not be blank")
    var appSecret: String? = null

    /** Lark 开放平台域名。 */
    @field:NotBlank(message = "lark.agent.domain must not be blank")
    var domain: String? = null

    /** 群聊消息是否必须提到机器人后才处理。 */
    var requireMention: Boolean = true

    /** 机器人是否响应 @所有人 消息。 */
    var respondToMentionAll: Boolean = false

    /** 允许响应的群 ID 列表；为空表示不额外限制。 */
    var groupAllowList: MutableList<String>? = arrayListOf()

    /** 群聊引用链向上追溯的最大深度。 */
    @field:Min(value = 0, message = "lark.agent.group-reply-chain-depth must be greater than or equal to 0")
    var groupReplyChainDepth: Int = 5

    /** 私聊中带入模型上下文的最近消息条数。 */
    @field:Min(value = 0, message = "lark.agent.private-chat-context-size must be greater than or equal to 0")
    var privateChatContextSize: Int = 10

}
