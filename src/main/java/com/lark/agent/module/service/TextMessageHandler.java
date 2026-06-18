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
 * 文本消息处理器，负责筛选文本消息、组装上下文并调用模型回复。
 *
 * @Author: fatina 2026/06/18
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
     * 判断当前消息是否由文本消息处理器处理。
     *
     * @param message 当前消息
     * @return true 表示支持处理
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
     * 组装上下文并调用模型生成回复，再回写到当前消息线程。
     *
     * @param message 当前消息
     */
    @Override
    public void handle(NormalizedMessage message) {
        try {
            // 先把群聊引用链或私聊最近几条消息整理成上下文，再一并交给模型。
            String context = messageContextAssembler.assemble(message);
            // 路由仍只基于当前提问判断领域，避免历史上下文把 prompt 分类带偏。
            String reply = geminiClient.call(context, message.getContent());
            larkMessageReplyService.replyText(message.getChatId(), message.getMessageId(), reply);
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
     * 识别 Gemini 的额度超限和限流错误，便于给用户返回更明确的提示。
     *
     * @param exception Gemini 客户端异常
     * @return true 表示额度超限或触发限流
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
