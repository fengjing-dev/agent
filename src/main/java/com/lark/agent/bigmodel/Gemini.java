package com.lark.agent.bigmodel;

import com.lark.agent.module.properties.GeminiProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;

/**
 * Gemini
 * @Author: fatina 2026/06/17
 */
@Slf4j
@Component
public class Gemini {

    @Resource
    private GeminiProperties geminiProperties;

    public String callGemini(String message) {
        Client client = Client.builder().apiKey(geminiProperties.getApiKey()).build();
        String systemPrompt = geminiProperties.getSystemPrompt();
        message = systemPrompt + "\n用户消息:" + message;
        log.info("提问: {}", message);
        GenerateContentResponse generateContentResponse = client.models.generateContent(geminiProperties.getModel(), message, null);
        return generateContentResponse.text();
    }
}
