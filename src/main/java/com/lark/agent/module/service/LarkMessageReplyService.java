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
 * Sends normal text replies and streaming card replies to Lark.
 */
@Service
public class LarkMessageReplyService {

    private static final Logger log = LoggerFactory.getLogger(LarkMessageReplyService.class);
    private static final long STREAM_UPDATE_INTERVAL_MS = 400;
    private static final int STREAM_UPDATE_CHARS = 80;
    private static final String EMPTY_MODEL_RESPONSE = "模型没有返回内容。";
    private static final String STREAM_FAILED_RESPONSE = "生成回复时遇到错误，请稍后再试。";

    private LarkChannel channel;

    /**
     * Stores the active Lark channel after the long connection is established.
     *
     * @param channel active Lark channel.
     */
    public void setChannel(LarkChannel channel) {
        this.channel = channel;
    }

    /**
     * Sends a plain text reply to a message thread.
     *
     * @param chatId target Lark chat ID.
     * @param messageId message ID to reply to.
     * @param content reply text content.
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
     * Sends a streaming answer card and lets the producer append generated text chunks.
     *
     * @param chatId target Lark chat ID.
     * @param messageId message ID to reply to.
     * @param textProducer producer that receives a chunk consumer and streams model text into it.
     */
    public void replyStreamingCard(String chatId, String messageId, Consumer<Consumer<String>> textProducer) {
        if (channel == null) {
            throw new IllegalStateException("Lark channel is not ready.");
        }

        StringBuilder content = new StringBuilder();
        StreamUpdateState updateState = new StreamUpdateState();
        channel.streamSync(
                chatId,
                StreamInput.card(buildAnswerCard("正在思考...", CardStatus.STREAMING), controller -> {
                    try {
                        textProducer.accept(chunk -> {
                            content.append(chunk);
                            if (updateState.shouldUpdate(chunk.length())) {
                                controller.update(buildAnswerCard(content.toString(), CardStatus.STREAMING));
                            }
                        });
                        String finalContent = content.length() == 0 ? EMPTY_MODEL_RESPONSE : content.toString();
                        controller.update(buildAnswerCard(finalContent, CardStatus.DONE));
                    } catch (Exception e) {
                        log.warn("Streaming card generation failed. chatId={}, messageId={}", chatId, messageId, e);
                        controller.update(buildAnswerCard(STREAM_FAILED_RESPONSE, CardStatus.FAILED));
                    }
                }),
                SendOptions.newBuilder().replyTo(messageId).build()
        );
    }

    /**
     * Builds a Lark interactive card with a status header and markdown body.
     *
     * @param content card body markdown content.
     * @param status current card status.
     * @return card payload map accepted by the Lark SDK.
     */
    private Map<String, Object> buildAnswerCard(String content, CardStatus status) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("config", mapOf("wide_screen_mode", true));
        card.put("header", buildHeader(status));
        card.put("elements", buildElements(content));
        return card;
    }

    /**
     * Builds the card header for the current status.
     *
     * @param status current card status.
     * @return card header payload.
     */
    private Map<String, Object> buildHeader(CardStatus status) {
        Map<String, Object> title = new LinkedHashMap<>();
        title.put("tag", "plain_text");
        title.put("content", status.title);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("template", status.template);
        header.put("title", title);
        return header;
    }

    /**
     * Builds the markdown body elements of the card.
     *
     * @param content markdown content to display.
     * @return card element list.
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
     * Creates a single-entry ordered map.
     *
     * @param key map key.
     * @param value map value.
     * @return ordered map containing one entry.
     */
    private Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    /**
     * Header status values used by the streaming answer card.
     */
    private enum CardStatus {
        STREAMING("正在生成", "blue"),
        DONE("回答完成", "green"),
        FAILED("生成失败", "red");

        private final String title;
        private final String template;

        /**
         * Creates a card status definition.
         *
         * @param title card header title.
         * @param template Lark header color template.
         */
        CardStatus(String title, String template) {
            this.title = title;
            this.template = template;
        }
    }

    /**
     * Tracks pending stream chunks and decides when the card should be updated.
     */
    private static class StreamUpdateState {

        private long lastUpdateAt = System.currentTimeMillis();
        private int pendingChars;

        /**
         * Decides whether enough time or text has accumulated to update the card.
         *
         * @param chunkLength length of the latest streamed chunk.
         * @return true when the card should be updated.
         */
        private boolean shouldUpdate(int chunkLength) {
            pendingChars += chunkLength;
            long now = System.currentTimeMillis();
            if (pendingChars < STREAM_UPDATE_CHARS && now - lastUpdateAt < STREAM_UPDATE_INTERVAL_MS) {
                return false;
            }
            pendingChars = 0;
            lastUpdateAt = now;
            return true;
        }
    }
}
