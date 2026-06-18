package com.lark.agent.module.component

import com.google.genai.Client
import com.lark.agent.module.constants.PromptRouter
import com.lark.agent.module.properties.GeminiProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Gemini 客户端，负责根据当前问题选择 prompt 并调用模型。
 *
 * @Author: Fatina 2026/06/17
 */
@Component
class GeminiClient(val geminiProperties: GeminiProperties) {

    val log: Logger = LoggerFactory.getLogger(GeminiClient::class.java)

    /**
     * 调用模型生成回复。
     *
     * @param message 真实提交给模型的上下文文本
     * @param routeText 仅用于 prompt 路由判断的当前提问文本
     * @return 模型返回文本
     */
    fun call(message: String, routeText: String = message): String? {
        val client = Client.builder().apiKey(geminiProperties.apiKey).build()
        val domainType = PromptRouter.detectDomain(routeText)
        val resolvePrompt = PromptRouter.resolvePrompt(domainType)
        val prompt = resolvePrompt + "\n 用户消息:" + message
        val response = client.models.generateContent(geminiProperties.model, prompt, null)
        return response?.text()
    }
}
