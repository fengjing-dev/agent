package com.lark.agent.module.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for Lark long-connection access and context assembly.
 */
@ConfigurationProperties(prefix = "lark.agent")
class AgentProperties {

    /** Lark application ID. */
    var appId: String? = null

    /** Lark application secret. */
    var appSecret: String? = null

    /** Lark Open Platform domain. */
    var domain: String? = null

    /** Whether group messages must mention the bot before being handled. */
    var requireMention: Boolean = true

    /** Whether the bot should respond to mention-all messages. */
    var respondToMentionAll: Boolean = false

    /** Allowed group IDs. Empty means no additional allow-list restriction. */
    var groupAllowList: MutableList<String>? = arrayListOf()

    /** Maximum depth for walking a group reply chain. */
    var groupReplyChainDepth: Int = 5

    /** Number of recent private-chat messages to include as model context. */
    var privateChatContextSize: Int = 5

    /** Whether private-chat context should include the bot's own previous replies. */
    var includeBotHistory: Boolean = false
}
