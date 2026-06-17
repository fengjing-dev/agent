package com.lark.agent.module.component

import com.google.genai.Client
import com.lark.agent.module.constants.PromptRouter
import com.lark.agent.module.properties.GeminiProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * gemini客户端
 * @Author: Fatina 2026/06/17
 */
@Component
class GeminiClient(val geminiProperties: GeminiProperties) {

    val log: Logger = LoggerFactory.getLogger(GeminiClient::class.java)

    /**
     * 调用
     */
    fun call(message: String): String?{
        val client = Client.builder().apiKey(geminiProperties.apiKey).build()
        val domainType = PromptRouter.detectDomain(message)
        val resolvePrompt = PromptRouter.resolvePrompt(domainType)
        val prompt = resolvePrompt + "\n 用户消息:" + message
        val response = client.models.generateContent(geminiProperties.model, prompt, null)
        return response?.text();
    }

}