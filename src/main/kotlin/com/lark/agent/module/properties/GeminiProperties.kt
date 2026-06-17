package com.lark.agent.module.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Gemini 大模型配置。
 *
 * 使用 `@ConfigurationProperties` 绑定配置时，属性需要可写，
 * 因此这里统一使用 `var`，由 Kotlin 自动生成 getter/setter。
 */
@ConfigurationProperties(prefix = "gemini")
class GeminiProperties {

    /**
     * Gemini API Key。
     */
    var apiKey: String? = null

    /**
     * Gemini 模型名称。
     */
    var model: String? = null

    /**
     * Gemini 接口基础地址。
     */
    var baseUrl: String? = null

    /**
     * 发送给模型的系统提示词。
     */
    var systemPrompt: String? = null
}
