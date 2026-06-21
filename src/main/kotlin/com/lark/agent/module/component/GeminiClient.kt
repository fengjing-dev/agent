package com.lark.agent.module.component

import com.google.genai.Client
import com.google.genai.errors.ClientException
import com.lark.agent.module.constants.PromptRouter
import com.lark.agent.module.properties.GeminiProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.function.Consumer

/**
 * Gemini client responsible for prompt routing and model invocation.
 */
@Component
class GeminiClient(private val geminiProperties: GeminiProperties) {

    private val log: Logger = LoggerFactory.getLogger(GeminiClient::class.java)
    private val client: Client by lazy {
        Client.builder().apiKey(geminiProperties.apiKey).build()
    }

    /**
     * Calls Gemini once and returns the complete response text.
     *
     * @param message full model context.
     * @param routeText current user question used only for prompt routing.
     * @return generated response text, or null when the SDK returns no text.
     */
    fun call(message: String, routeText: String = message): String? {
        val prompt = buildPrompt(message, routeText)
        log.debug("Gemini prompt length={}", prompt.length)
        return generateContentWithRetry(prompt)
    }

    /**
     * Calls Gemini with streaming output and forwards each text chunk to the caller.
     *
     * @param message full model context.
     * @param routeText current user question used only for prompt routing.
     * @param chunkConsumer callback that receives generated text chunks.
     * @return complete generated response text assembled from all chunks.
     */
    fun stream(message: String, routeText: String = message, chunkConsumer: Consumer<String>): String? {
        val prompt = buildPrompt(message, routeText)
        log.debug("Gemini streaming prompt length={}", prompt.length)
        return generateContentStreamWithRetry(prompt, chunkConsumer)
    }

    /**
     * Builds the final prompt from the global system prompt, domain prompt, and user context.
     *
     * @param message full model context.
     * @param routeText current user question used only for prompt routing.
     * @return final prompt sent to Gemini.
     */
    private fun buildPrompt(message: String, routeText: String): String {
        val domainType = PromptRouter.detectDomain(routeText)
        val resolvePrompt = PromptRouter.resolvePrompt(domainType)
        val systemPrompt = geminiProperties.systemPrompt?.takeIf { it.isNotBlank() }
        return listOfNotNull(systemPrompt, resolvePrompt, "用户消息:$message").joinToString("\n")
    }

    /**
     * Generates non-streaming content with retry support for transient rate-limit responses.
     *
     * @param prompt final prompt sent to Gemini.
     * @return generated response text.
     */
    private fun generateContentWithRetry(prompt: String): String? {
        var attempt = 0
        var delayMs = geminiProperties.initialRetryDelayMs.coerceAtLeast(0)
        val maxRetries = geminiProperties.maxRetries.coerceAtLeast(0)

        while (true) {
            try {
                val response = client.models.generateContent(geminiProperties.model, prompt, null)
                return response?.text()
            } catch (exception: ClientException) {
                if (!isRateLimited(exception) || attempt >= maxRetries) {
                    throw exception
                }
                attempt++
                log.warn(
                    "Gemini rate limited. retryAttempt={}, maxRetries={}, delayMs={}",
                    attempt,
                    maxRetries,
                    delayMs,
                    exception
                )
                sleepBeforeRetry(delayMs)
                delayMs = nextDelay(delayMs)
            }
        }
    }

    /**
     * Generates streaming content with retry support before any chunk has been emitted.
     *
     * @param prompt final prompt sent to Gemini.
     * @param chunkConsumer callback that receives generated text chunks.
     * @return generated response text assembled from all chunks.
     */
    private fun generateContentStreamWithRetry(prompt: String, chunkConsumer: Consumer<String>): String? {
        var attempt = 0
        var delayMs = geminiProperties.initialRetryDelayMs.coerceAtLeast(0)
        val maxRetries = geminiProperties.maxRetries.coerceAtLeast(0)

        while (true) {
            var emitted = false
            val fullText = StringBuilder()
            try {
                client.models.generateContentStream(geminiProperties.model, prompt, null).use { stream ->
                    for (response in stream) {
                        val chunk = response?.text()
                        if (!chunk.isNullOrEmpty()) {
                            emitted = true
                            fullText.append(chunk)
                            chunkConsumer.accept(chunk)
                        }
                    }
                }
                return fullText.toString()
            } catch (exception: ClientException) {
                if (emitted || !isRateLimited(exception) || attempt >= maxRetries) {
                    throw exception
                }
                attempt++
                log.warn(
                    "Gemini stream rate limited. retryAttempt={}, maxRetries={}, delayMs={}",
                    attempt,
                    maxRetries,
                    delayMs,
                    exception
                )
                sleepBeforeRetry(delayMs)
                delayMs = nextDelay(delayMs)
            }
        }
    }

    /**
     * Checks whether a Gemini client exception indicates quota or rate limiting.
     *
     * @param exception Gemini SDK client exception.
     * @return true when the exception looks like a rate-limit response.
     */
    private fun isRateLimited(exception: ClientException): Boolean {
        val message = exception.message ?: return false
        return message.contains("429")
                || message.contains("Quota exceeded", ignoreCase = true)
                || message.contains("RESOURCE_EXHAUSTED", ignoreCase = true)
    }

    /**
     * Sleeps before retrying a model request.
     *
     * @param delayMs delay duration in milliseconds.
     */
    private fun sleepBeforeRetry(delayMs: Long) {
        if (delayMs <= 0) {
            return
        }
        try {
            Thread.sleep(delayMs)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted while waiting to retry Gemini request.", exception)
        }
    }

    /**
     * Calculates the next exponential retry delay.
     *
     * @param delayMs current delay in milliseconds.
     * @return next delay, capped at ten seconds.
     */
    private fun nextDelay(delayMs: Long): Long {
        if (delayMs <= 0) {
            return 0
        }
        return (delayMs * 2).coerceAtMost(10_000)
    }
}
