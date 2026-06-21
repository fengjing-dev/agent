package com.lark.agent.module.properties

import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * DeepSeek OpenAI 兼容接口配置。
 */
@Validated
@ConfigurationProperties(prefix = "deepseek")
class DeepSeekProperties {

    /** DeepSeek API Key。 */
    var apiKey: String? = null

    /** DeepSeek 模型名称。 */
    var model: String? = null

    /** DeepSeek API 基础地址。 */
    var baseUrl: String? = null

    /** 每次模型请求前置的全局系统提示词。 */
    var systemPrompt: String? = null

    /** 请求超时时间，单位秒。 */
    @field:Min(value = 1, message = "deepseek.timeout-seconds must be greater than or equal to 1")
    var timeoutSeconds: Long = 120

    /** 瞬时模型错误的最大重试次数。 */
    @field:Min(value = 0, message = "deepseek.max-retries must be greater than or equal to 0")
    var maxRetries: Int = 2

    /** 初始重试延迟，单位毫秒。 */
    @field:Min(value = 0, message = "deepseek.initial-retry-delay-ms must be greater than or equal to 0")
    var initialRetryDelayMs: Long = 1000

    /** 两次 DeepSeek 请求之间的最小间隔，单位毫秒。 */
    @field:Min(value = 0, message = "deepseek.min-request-interval-ms must be greater than or equal to 0")
    var minRequestIntervalMs: Long = 1000

    /** 触发限流后的本地冷却时间，单位毫秒。 */
    @field:Min(value = 0, message = "deepseek.rate-limit-cooldown-ms must be greater than or equal to 0")
    var rateLimitCooldownMs: Long = 60000
}
