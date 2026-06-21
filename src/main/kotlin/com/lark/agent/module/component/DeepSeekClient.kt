package com.lark.agent.module.component

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.lark.agent.module.constants.PromptRouter
import com.lark.agent.module.properties.DeepSeekProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * 负责提示词路由和模型调用的 DeepSeek 客户端。
 */
@Component
@ConditionalOnProperty(name = ["model.provider"], havingValue = "deepseek")
class DeepSeekClient(
    private val deepSeekProperties: DeepSeekProperties
) : ModelClient {

    private val log: Logger = LoggerFactory.getLogger(DeepSeekClient::class.java)
    private val objectMapper: ObjectMapper = ObjectMapper()
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(deepSeekProperties.timeoutSeconds))
        .build()
    private val requestLock = Any()
    private val nextAllowedRequestAt = AtomicLong(0)
    private val rateLimitedUntil = AtomicLong(0)

    init {
        require(!deepSeekProperties.apiKey.isNullOrBlank()) { "deepseek.api-key must not be blank when model.provider=deepseek" }
        require(!deepSeekProperties.model.isNullOrBlank()) { "deepseek.model must not be blank when model.provider=deepseek" }
        require(!deepSeekProperties.baseUrl.isNullOrBlank()) { "deepseek.base-url must not be blank when model.provider=deepseek" }
    }

    override fun call(message: String, routeText: String): String? {
        val prompt = buildPrompt(message, routeText)
        log.debug("DeepSeek prompt length={}", prompt.length)
        val fullText = StringBuilder()
        generateContentStreamWithRetry(prompt) { chunk -> fullText.append(chunk) }
        return fullText.toString()
    }

    override fun stream(message: String, routeText: String, chunkConsumer: Consumer<String>): String? {
        val prompt = buildPrompt(message, routeText)
        log.info("DeepSeek streaming prompt length={}, prompt:{}", prompt.length, prompt)
        val fullText = StringBuilder()
        generateContentStreamWithRetry(prompt) { chunk ->
            fullText.append(chunk)
            chunkConsumer.accept(chunk)
        }
        return fullText.toString()
    }

    /**
     * 根据全局系统提示词、领域提示词和用户上下文构建最终 prompt。
     */
    private fun buildPrompt(message: String, routeText: String): String {
        val domainType = PromptRouter.detectDomain(routeText)
        val resolvePrompt = PromptRouter.resolvePrompt(domainType)
        val systemPrompt = deepSeekProperties.systemPrompt?.takeIf { it.isNotBlank() }
        return listOfNotNull(systemPrompt, resolvePrompt, "用户消息:$message").joinToString("\n")
    }

    /**
     * 生成流式内容；在尚未输出任何分片前支持限流重试。
     */
    private fun generateContentStreamWithRetry(prompt: String, chunkConsumer: Consumer<String>) {
        var attempt = 0
        var delayMs = deepSeekProperties.initialRetryDelayMs.coerceAtLeast(0)
        val maxRetries = deepSeekProperties.maxRetries.coerceAtLeast(0)

        while (true) {
            var emitted = false
            try {
                waitForRequestSlot()
                streamOnce(prompt) { chunk ->
                    emitted = true
                    chunkConsumer.accept(chunk)
                }
                return
            } catch (exception: DeepSeekClientException) {
                if (emitted || !exception.rateLimited || attempt >= maxRetries) {
                    if (exception.rateLimited) {
                        startRateLimitCooldown()
                    }
                    if (exception.rateLimited) {
                        throw ModelRateLimitException(exception.message ?: "DeepSeek rate limited.", exception)
                    }
                    throw exception
                }
                attempt++
                log.warn(
                    "DeepSeek stream rate limited. retryAttempt={}, maxRetries={}, delayMs={}",
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
     * 发起一次 OpenAI 兼容的流式请求并解析 SSE 分片。
     */
    private fun streamOnce(prompt: String, chunkConsumer: Consumer<String>) {
        val response = httpClient.send(
            buildRequest(prompt),
            HttpResponse.BodyHandlers.ofInputStream()
        )
        if (response.statusCode() >= 400) {
            val errorText = response.body().use { String(it.readAllBytes(), StandardCharsets.UTF_8) }
            throw DeepSeekClientException(
                "DeepSeek request failed. status=${response.statusCode()}, body=$errorText",
                response.statusCode() == 429
            )
        }

        BufferedReader(InputStreamReader(response.body(), StandardCharsets.UTF_8)).use { reader ->
            reader.lineSequence()
                .filter { it.startsWith("data:") }
                .map { it.removePrefix("data:").trim() }
                .takeWhile { it != "[DONE]" }
                .forEach { data -> emitDelta(data, chunkConsumer) }
        }
    }

    /**
     * 构建 DeepSeek OpenAI 兼容流式请求。
     */
    private fun buildRequest(prompt: String): HttpRequest {
        val endpoint = deepSeekProperties.baseUrl!!.trimEnd('/') + "/chat/completions"
        val body = objectMapper.writeValueAsString(
            mapOf(
                "model" to deepSeekProperties.model,
                "stream" to true,
                "messages" to listOf(
                    mapOf("role" to "user", "content" to prompt)
                )
            )
        )
        return HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(Duration.ofSeconds(deepSeekProperties.timeoutSeconds))
            .header("Authorization", "Bearer ${deepSeekProperties.apiKey}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()
    }

    /**
     * 从 SSE JSON 分片中提取增量文本。
     */
    private fun emitDelta(data: String, chunkConsumer: Consumer<String>) {
        val root: JsonNode = objectMapper.readTree(data)
        val choices = root.path("choices")
        if (!choices.isArray || choices.isEmpty) {
            return
        }
        val content = choices[0].path("delta").path("content").asText("")
        if (content.isNotEmpty()) {
            chunkConsumer.accept(content)
        }
    }

    /**
     * 等待本地限流窗口，避免短时间连续请求打爆 DeepSeek 配额。
     */
    private fun waitForRequestSlot() {
        synchronized(requestLock) {
            val now = System.currentTimeMillis()
            val cooldownUntil = rateLimitedUntil.get()
            if (now < cooldownUntil) {
                throw DeepSeekClientException(
                    "DeepSeek local cooldown active after rate limit. retryAfterMs=${cooldownUntil - now}",
                    true
                )
            }

            val requestAt = nextAllowedRequestAt.get()
            if (now < requestAt) {
                sleepBeforeRetry(requestAt - now)
            }

            val intervalMs = deepSeekProperties.minRequestIntervalMs.coerceAtLeast(0)
            nextAllowedRequestAt.set(System.currentTimeMillis() + intervalMs)
        }
    }

    /**
     * 记录 DeepSeek 限流后的本地冷却窗口。
     */
    private fun startRateLimitCooldown() {
        val cooldownMs = deepSeekProperties.rateLimitCooldownMs.coerceAtLeast(0)
        if (cooldownMs <= 0) {
            return
        }
        val until = System.currentTimeMillis() + cooldownMs
        rateLimitedUntil.updateAndGet { current -> maxOf(current, until) }
        log.warn("DeepSeek local cooldown started. cooldownMs={}", cooldownMs)
    }

    /**
     * 在重试模型请求前等待。
     */
    private fun sleepBeforeRetry(delayMs: Long) {
        if (delayMs <= 0) {
            return
        }
        try {
            Thread.sleep(delayMs)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted while waiting to retry DeepSeek request.", exception)
        }
    }

    /**
     * 计算下一次指数退避延迟。
     */
    private fun nextDelay(delayMs: Long): Long {
        if (delayMs <= 0) {
            return 0
        }
        return (delayMs * 2).coerceAtMost(10_000)
    }

    /**
     * DeepSeek 调用异常。
     */
    class DeepSeekClientException(message: String, val rateLimited: Boolean) : RuntimeException(message)
}
