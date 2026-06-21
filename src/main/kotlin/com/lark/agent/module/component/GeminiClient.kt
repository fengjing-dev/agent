package com.lark.agent.module.component

import com.google.genai.Client
import com.google.genai.errors.ClientException
import com.google.genai.types.HttpOptions
import com.lark.agent.module.constants.PromptRouter
import com.lark.agent.module.properties.GeminiProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * 负责提示词路由和模型调用的 Gemini 客户端。
 */
@Component
@ConditionalOnProperty(name = ["model.provider"], havingValue = "gemini", matchIfMissing = true)
class GeminiClient(private val geminiProperties: GeminiProperties) : ModelClient {

    private val log: Logger = LoggerFactory.getLogger(GeminiClient::class.java)
    private val client: Client by lazy {
        buildClient()
    }
    private val requestLock = Any()
    private val nextAllowedRequestAt = AtomicLong(0)
    private val rateLimitedUntil = AtomicLong(0)

    /**
     * 调用 Gemini 一次并返回完整响应文本。
     *
     * @param message 完整模型上下文。
     * @param routeText 当前用户问题，仅用于提示词路由。
     * @return 生成的响应文本；SDK 没有返回文本时为 null。
     */
    override fun call(message: String, routeText: String): String? {
        val prompt = buildPrompt(message, routeText)
        log.debug("Gemini prompt length={}", prompt.length)
        return generateContentWithRetry(prompt)
    }

    /**
     * 以流式方式调用 Gemini，并把文本分片转发给调用方。
     *
     * @param message 完整模型上下文。
     * @param routeText 当前用户问题，仅用于提示词路由。
     * @param chunkConsumer 接收生成文本分片的回调。
     * @return 由所有分片拼成的完整生成文本。
     */
    override fun stream(message: String, routeText: String, chunkConsumer: Consumer<String>): String? {
        val prompt = buildPrompt(message, routeText)
        log.info("Gemini streaming prompt length={}, prompt:{}", prompt.length, prompt)
        return generateContentStreamWithRetry(prompt, chunkConsumer)
    }

    /**
     * 根据全局系统提示词、领域提示词和用户上下文构建最终 prompt。
     *
     * @param message 完整模型上下文。
     * @param routeText 当前用户问题，仅用于提示词路由。
     * @return 发送给 Gemini 的最终 prompt。
     */
    private fun buildPrompt(message: String, routeText: String): String {
        val domainType = PromptRouter.detectDomain(routeText)
        val resolvePrompt = PromptRouter.resolvePrompt(domainType)
        val systemPrompt = geminiProperties.systemPrompt?.takeIf { it.isNotBlank() }
        return listOfNotNull(systemPrompt, resolvePrompt, "用户消息:$message").joinToString("\n")
    }

    /**
     * 根据凭据和 HTTP 配置构建 Gemini SDK 客户端。
     *
     * @return Gemini SDK 客户端。
     */
    private fun buildClient(): Client {
        require(!geminiProperties.apiKey.isNullOrBlank()) { "gemini.api-key must not be blank when model.provider=gemini" }
        require(!geminiProperties.model.isNullOrBlank()) { "gemini.model must not be blank when model.provider=gemini" }
        val builder = Client.builder().apiKey(geminiProperties.apiKey)
        val baseUrl = geminiProperties.baseUrl?.takeIf { it.isNotBlank() }
        if (baseUrl != null) {
            builder.httpOptions(HttpOptions.builder().baseUrl(baseUrl).build())
        }
        return builder.build()
    }

    /**
     * 生成非流式内容，并对瞬时限流响应做重试。
     *
     * @param prompt 发送给 Gemini 的最终 prompt。
     * @return 生成的响应文本。
     */
    private fun generateContentWithRetry(prompt: String): String? {
        var attempt = 0
        var delayMs = geminiProperties.initialRetryDelayMs.coerceAtLeast(0)
        val maxRetries = geminiProperties.maxRetries.coerceAtLeast(0)

        while (true) {
            try {
                waitForRequestSlot()
                val response = client.models.generateContent(geminiProperties.model, prompt, null)
                return response?.text()
            } catch (exception: ClientException) {
                if (!isRateLimited(exception) || attempt >= maxRetries) {
                    if (isRateLimited(exception)) {
                        startRateLimitCooldown()
                    }
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
     * 生成流式内容；在尚未输出任何分片前支持限流重试。
     *
     * @param prompt 发送给 Gemini 的最终 prompt。
     * @param chunkConsumer 接收生成文本分片的回调。
     * @return 由所有分片拼成的完整生成文本。
     */
    private fun generateContentStreamWithRetry(prompt: String, chunkConsumer: Consumer<String>): String? {
        var attempt = 0
        var delayMs = geminiProperties.initialRetryDelayMs.coerceAtLeast(0)
        val maxRetries = geminiProperties.maxRetries.coerceAtLeast(0)

        while (true) {
            var emitted = false
            val fullText = StringBuilder()
            try {
                waitForRequestSlot()
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
                    if (isRateLimited(exception)) {
                        startRateLimitCooldown()
                    }
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
     * 等待本地限流窗口，避免短时间连续请求打爆 Gemini 配额。
     */
    private fun waitForRequestSlot() {
        synchronized(requestLock) {
            val now = System.currentTimeMillis()
            val cooldownUntil = rateLimitedUntil.get()
            if (now < cooldownUntil) {
                throw ModelRateLimitException("Gemini local cooldown active after rate limit. retryAfterMs=${cooldownUntil - now}")
            }

            val requestAt = nextAllowedRequestAt.get()
            if (now < requestAt) {
                sleepBeforeRetry(requestAt - now)
            }

            val intervalMs = geminiProperties.minRequestIntervalMs.coerceAtLeast(0)
            nextAllowedRequestAt.set(System.currentTimeMillis() + intervalMs)
        }
    }

    /**
     * 记录 Gemini 限流后的本地冷却窗口。
     */
    private fun startRateLimitCooldown() {
        val cooldownMs = geminiProperties.rateLimitCooldownMs.coerceAtLeast(0)
        if (cooldownMs <= 0) {
            return
        }
        val until = System.currentTimeMillis() + cooldownMs
        rateLimitedUntil.updateAndGet { current -> maxOf(current, until) }
        log.warn("Gemini local cooldown started. cooldownMs={}", cooldownMs)
    }

    /**
     * 判断 Gemini 客户端异常是否表示配额或限流问题。
     *
     * @param exception Gemini SDK 客户端异常。
     * @return 异常看起来像限流响应时返回 true。
     */
    private fun isRateLimited(exception: ClientException): Boolean {
        val message = exception.message ?: return false
        return message.contains("429")
                || message.contains("Quota exceeded", ignoreCase = true)
                || message.contains("RESOURCE_EXHAUSTED", ignoreCase = true)
    }

    /**
     * 在重试模型请求前等待。
     *
     * @param delayMs 等待时长，单位毫秒。
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
     * 计算下一次指数退避延迟。
     *
     * @param delayMs 当前延迟，单位毫秒。
     * @return 下一次延迟，最大十秒。
     */
    private fun nextDelay(delayMs: Long): Long {
        if (delayMs <= 0) {
            return 0
        }
        return (delayMs * 2).coerceAtMost(10_000)
    }
}
