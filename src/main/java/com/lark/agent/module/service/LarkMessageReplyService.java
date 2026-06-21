package com.lark.agent.module.service;

import com.lark.oapi.channel.LarkChannel;
import com.lark.oapi.channel.model.SendInput;
import com.lark.oapi.channel.model.SendOptions;
import com.lark.oapi.channel.model.StreamInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 负责向 Lark 发送普通文本回复和流式卡片回复。
 */
@Service
public class LarkMessageReplyService {

    private static final Logger log = LoggerFactory.getLogger(LarkMessageReplyService.class);
    private static final long STREAM_UPDATE_INTERVAL_MS = 250;
    private static final long ANALYSIS_UPDATE_INTERVAL_MS = 80;
    private static final int STREAM_UPDATE_CHARS = 24;
    private static final int ANALYSIS_UPDATE_CHARS = 4;
    private static final String ANALYSIS_MARKER = "【分析摘要】";
    private static final String FINAL_MARKER = "【最终回答】";
    private static final String EMPTY_MODEL_RESPONSE = "模型没有返回内容。";
    private static final String STREAM_FAILED_RESPONSE = "生成回复时遇到错误，请稍后再试。";

    private LarkChannel channel;

    /**
     * 保存长连接建立后的可用 Lark 通道。
     *
     * @param channel 当前可用的 Lark 通道。
     */
    public void setChannel(LarkChannel channel) {
        this.channel = channel;
    }

    /**
     * 向指定消息发送普通文本回复。
     *
     * @param chatId 目标 Lark 会话 ID。
     * @param messageId 要回复的消息 ID。
     * @param content 回复文本内容。
     */
    public void replyText(String chatId, String messageId, String content) {
        if (channel == null) {
            log.warn("Lark channel is not ready. chatId={}, messageId={}", chatId, messageId);
            return;
        }
        try {
            channel.send(
                    chatId,
                    SendInput.text(content),
                    SendOptions.newBuilder().replyTo(messageId).build()
            );
        } catch (Exception e) {
            log.error("Failed to reply message: chatId={}, messageId={}", chatId, messageId, e);
        }
    }

    /**
     * 发送流式回答卡片，并返回模型最终生成的文本。
     *
     * @param chatId 目标 Lark 会话 ID。
     * @param messageId 要回复的消息 ID。
     * @param textProducer 接收分片消费者并写入模型输出的生产器。
     * @return 模型最终生成的文本；生成失败时返回 null。
     */
    public String replyStreamingCard(String chatId, String messageId, Consumer<Consumer<String>> textProducer) {
        if (channel == null) {
            throw new IllegalStateException("Lark channel is not ready.");
        }

        StringBuilder content = new StringBuilder();
        String[] generatedContent = new String[1];
        StreamUpdateState updateState = new StreamUpdateState();
        channel.streamSync(
                chatId,
                StreamInput.card(buildAnswerCard(grayText("正在分析..."), CardStatus.STREAMING), controller -> {
                    try {
                        textProducer.accept(chunk -> {
                            content.append(chunk);
                            if (updateState.shouldUpdate(content.toString(), chunk)) {
                                controller.update(buildAnswerCard(renderDisplayContent(content.toString()), CardStatus.STREAMING));
                            }
                        });
                        String finalContent = content.length() == 0 ? EMPTY_MODEL_RESPONSE : extractFinalAnswer(content.toString());
                        generatedContent[0] = finalContent;
                        controller.update(buildAnswerCard(renderDisplayContent(content.toString()), CardStatus.DONE));
                    } catch (Exception e) {
                        log.warn("Streaming card generation failed. chatId={}, messageId={}", chatId, messageId, e);
                        generatedContent[0] = null;
                        controller.update(buildAnswerCard(STREAM_FAILED_RESPONSE, CardStatus.FAILED));
                    }
                }),
                SendOptions.newBuilder().replyTo(messageId).build()
        );
        return generatedContent[0];
    }

    /**
     * 根据模型当前输出渲染卡片内容：最终回答出现前展示灰色分析摘要，出现后只展示正式回答。
     *
     * @param rawContent 模型已生成的原始内容。
     * @return 卡片中展示的 Markdown 内容。
     */
    String renderDisplayContent(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return grayText("正在分析...");
        }
        int finalStart = rawContent.indexOf(FINAL_MARKER);
        if (finalStart >= 0) {
            String finalAnswer = rawContent.substring(finalStart + FINAL_MARKER.length()).trim();
            if (finalAnswer.isEmpty()) {
                return grayText("正在整理最终回答...");
            }
            return finalAnswer;
        }
        int analysisStart = rawContent.indexOf(ANALYSIS_MARKER);
        String analysis = analysisStart >= 0
                ? rawContent.substring(analysisStart + ANALYSIS_MARKER.length()).trim()
                : rawContent.trim();
        if (analysis.isEmpty()) {
            analysis = "正在分析...";
        }
        return grayText(analysis);
    }

    /**
     * 从模型原始输出中提取正式回答，用于本地上下文记忆。
     *
     * @param rawContent 模型完整原始输出。
     * @return 正式回答；没有识别到正式回答标记时返回原始内容。
     */
    String extractFinalAnswer(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return EMPTY_MODEL_RESPONSE;
        }
        int finalStart = rawContent.indexOf(FINAL_MARKER);
        if (finalStart < 0) {
            return rawContent.trim();
        }
        String finalAnswer = rawContent.substring(finalStart + FINAL_MARKER.length()).trim();
        return finalAnswer.isEmpty() ? rawContent.trim() : finalAnswer;
    }

    /**
     * 使用 Lark Markdown 灰色字体包装文本。
     *
     * @param content 待展示内容。
     * @return 灰色 Markdown 文本。
     */
    private String grayText(String content) {
        return content.lines()
                .map(line -> line.isBlank() ? "" : "<font color='grey'>" + line + "</font>")
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    /**
     * 构建只包含 Markdown 正文的 Lark 交互卡片。
     *
     * @param content 卡片正文 Markdown 内容。
     * @param status 当前卡片状态。
     * @return Lark SDK 可接受的卡片载荷。
     */
    private Map<String, Object> buildAnswerCard(String content, CardStatus status) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("config", mapOf("wide_screen_mode", true));
        card.put("elements", buildElements(content));
        return card;
    }

    /**
     * 构建卡片 Markdown 正文元素。
     *
     * @param content 要展示的 Markdown 内容。
     * @return 卡片元素列表。
     */
    private List<Object> buildElements(String content) {
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("tag", "lark_md");
        text.put("content", content);

        Map<String, Object> div = new LinkedHashMap<>();
        div.put("tag", "div");
        div.put("text", text);

        List<Object> elements = new ArrayList<>();
        elements.add(div);
        return elements;
    }

    /**
     * 创建只包含一个键值对的有序 Map。
     *
     * @param key Map 键。
     * @param value Map 值。
     * @return 包含一个键值对的有序 Map。
     */
    private Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private enum CardStatus {
        STREAMING,
        DONE,
        FAILED
    }

    /**
     * 记录待刷新的流式分片，并判断是否需要更新卡片。
     */
    private static class StreamUpdateState {

        private long lastUpdateAt = System.currentTimeMillis();
        private int pendingChars;
        private int renderedLineCount;

        /**
         * 判断是否已经累积到足够的时间或文本长度，可以刷新卡片。
         *
         * @param fullContent 当前已生成完整内容。
         * @param chunk 最新分片。
         * @return 需要刷新卡片时返回 true。
         */
        private boolean shouldUpdate(String fullContent, String chunk) {
            int chunkLength = chunk == null ? 0 : chunk.length();
            pendingChars += chunkLength;
            long now = System.currentTimeMillis();

            if (isAnalysisStage(fullContent)) {
                int currentLineCount = countLines(fullContent);
                if (currentLineCount > renderedLineCount
                        || pendingChars >= ANALYSIS_UPDATE_CHARS
                        || now - lastUpdateAt >= ANALYSIS_UPDATE_INTERVAL_MS) {
                    renderedLineCount = currentLineCount;
                    pendingChars = 0;
                    lastUpdateAt = now;
                    return true;
                }
                return false;
            }

            if (pendingChars < STREAM_UPDATE_CHARS && now - lastUpdateAt < STREAM_UPDATE_INTERVAL_MS) {
                return false;
            }
            renderedLineCount = countLines(fullContent);
            pendingChars = 0;
            lastUpdateAt = now;
            return true;
        }

        /**
         * 判断当前是否还处于分析摘要阶段。
         *
         * @param fullContent 当前已生成完整内容。
         * @return 尚未出现最终回答标记时返回 true。
         */
        private boolean isAnalysisStage(String fullContent) {
            return fullContent == null || !fullContent.contains(FINAL_MARKER);
        }

        /**
         * 统计文本行数，用于分析阶段按行刷新。
         *
         * @param content 当前文本。
         * @return 文本行数。
         */
        private int countLines(String content) {
            if (content == null || content.isEmpty()) {
                return 0;
            }
            return content.split("\\R", -1).length;
        }
    }
}
