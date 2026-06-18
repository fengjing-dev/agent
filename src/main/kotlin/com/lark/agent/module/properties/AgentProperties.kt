package com.lark.agent.module.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Lark 智能体接入配置。
 *
 * 使用 `@ConfigurationProperties` 绑定配置时，属性需要可写，
 * 因此这里统一使用 `var`，由 Kotlin 自动生成 getter/setter。
 */
@ConfigurationProperties(prefix = "lark.agent")
class AgentProperties {

    /**
     * Lark 应用 App ID。
     */
    var appId: String? = null

    /**
     * Lark 应用 App Secret。
     */
    var appSecret: String? = null

    /**
     * Lark 开放平台域名。
     */
    var domain: String? = null

    /**
     * 群聊场景下是否要求必须 @ 机器人。
     */
    var requireMention: Boolean = true

    /**
     * 是否响应 @所有人 消息。
     */
    var respondToMentionAll: Boolean = false

    /**
     * 允许的组
     */
    var groupAllowList: MutableList<String>? = arrayListOf()

    /**
     * 群聊引用链向上追溯的最大层数
     */
    var groupReplyChainDepth: Int = 5

    /**
     * 私聊自动带入的历史消息条数
     */
    var privateChatContextSize: Int = 5

    /**
     * 私聊历史中是否包含机器人自己发送的消息
     */
    var includeBotHistory: Boolean = false
}
