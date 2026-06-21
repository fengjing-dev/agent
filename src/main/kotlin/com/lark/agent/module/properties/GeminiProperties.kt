package com.lark.agent.module.properties

import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Gemini 模型接入配置。
 */
@Validated
@ConfigurationProperties(prefix = "gemini")
class GeminiProperties {

    /** Gemini API Key。 */
    var apiKey: String? = null

    /** Gemini 模型名称。 */
    var model: String? = null

    /** Gemini API 基础地址。 */
    var baseUrl: String? = null

    /** 每次模型请求前置的全局系统提示词。 */
    var systemPrompt: String? = null

    /** 瞬时模型错误的最大重试次数。 */
    @field:Min(value = 0, message = "gemini.max-retries must be greater than or equal to 0")
    var maxRetries: Int = 2

    /** 初始重试延迟，单位毫秒。 */
    @field:Min(value = 0, message = "gemini.initial-retry-delay-ms must be greater than or equal to 0")
    var initialRetryDelayMs: Long = 1000

    /** 两次 Gemini 请求之间的最小间隔，单位毫秒。 */
    @field:Min(value = 0, message = "gemini.min-request-interval-ms must be greater than or equal to 0")
    var minRequestIntervalMs: Long = 2000

    /** 触发限流后的本地冷却时间，单位毫秒。 */
    @field:Min(value = 0, message = "gemini.rate-limit-cooldown-ms must be greater than or equal to 0")
    var rateLimitCooldownMs: Long = 60000
}
