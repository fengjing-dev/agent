package com.lark.agent.module.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for Gemini model access.
 */
@ConfigurationProperties(prefix = "gemini")
class GeminiProperties {

    /** Gemini API key. */
    var apiKey: String? = null

    /** Gemini model name. */
    var model: String? = null

    /** Gemini API base URL. */
    var baseUrl: String? = null

    /** Global system prompt prepended to every model request. */
    var systemPrompt: String? = null

    /** Maximum retry attempts for transient model errors. */
    var maxRetries: Int = 2

    /** Initial retry delay in milliseconds. */
    var initialRetryDelayMs: Long = 1000
}
