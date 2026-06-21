package com.lark.agent.module.service;

import com.google.genai.errors.ClientException;
import com.lark.agent.module.component.GeminiClient;
import com.lark.oapi.channel.model.NormalizedMessage;
import com.lark.oapi.core.utils.Strings;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Handles text messages by assembling model context, invoking Gemini, and replying to Lark.
 */
@Service
public class TextMessageHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(TextMessageHandler.class);

    private static final String TEXT_MESSAGE_TYPE = "text";
    private static final String MODEL_QUOTA_EXCEEDED_REPLY = "当前模型调用较频繁，请稍后再试。";
    private static final String MODEL_CALL_FAILED_REPLY = "当前服务暂时不可用，请稍后再试。";

    @Resource
    private GeminiClient geminiClient;

    @Resource
    private MessageContextAssembler messageContextAssembler;

    @Resource
    private LarkMessageReplyService larkMessageReplyService;

    /**
     * Checks whether this handler can process the normalized message.
     *
     * @param message normalized Lark message.
     * @return true when the message is a non-empty text message with chat and message IDs.
     */
    @Override
    public boolean support(NormalizedMessage message) {
        return message != null
                && TEXT_MESSAGE_TYPE.equalsIgnoreCase(message.getRawContentType())
                && !Strings.isEmpty(message.getChatId())
                && !Strings.isEmpty(message.getMessageId())
                && !Strings.isEmpty(message.getContent());
    }

    /**
     * Processes a text message by streaming a model response into a Lark card.
     *
     * @param message normalized Lark text message.
     */
    @Override
    public void handle(NormalizedMessage message) {
        try {
            String context = messageContextAssembler.assemble(message);
            larkMessageReplyService.replyStreamingCard(
                    message.getChatId(),
                    message.getMessageId(),
                    chunkConsumer -> geminiClient.stream(context, message.getContent(), chunkConsumer)
            );
        } catch (ClientException e) {
            if (isQuotaExceeded(e)) {
                log.warn("Gemini quota exceeded. chatId={}, messageId={}", message.getChatId(), message.getMessageId(), e);
                larkMessageReplyService.replyText(message.getChatId(), message.getMessageId(), MODEL_QUOTA_EXCEEDED_REPLY);
                return;
            }
            log.error("Gemini client exception. chatId={}, messageId={}", message.getChatId(), message.getMessageId(), e);
            larkMessageReplyService.replyText(message.getChatId(), message.getMessageId(), MODEL_CALL_FAILED_REPLY);
        } catch (Exception e) {
            log.error("Handle text message failed. chatId={}, messageId={}", message.getChatId(), message.getMessageId(), e);
            larkMessageReplyService.replyText(message.getChatId(), message.getMessageId(), MODEL_CALL_FAILED_REPLY);
        }
    }

    /**
     * Checks whether a Gemini client exception indicates quota exhaustion or rate limiting.
     *
     * @param exception Gemini SDK client exception.
     * @return true when the exception looks like quota or rate limiting.
     */
    private boolean isQuotaExceeded(ClientException exception) {
        String errorMessage = exception.getMessage();
        if (Strings.isEmpty(errorMessage)) {
            return false;
        }
        return errorMessage.contains("429")
                || errorMessage.contains("Quota exceeded")
                || errorMessage.contains("RESOURCE_EXHAUSTED");
    }
}
