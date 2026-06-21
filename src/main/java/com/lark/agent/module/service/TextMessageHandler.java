package com.lark.agent.module.service;

import com.google.genai.errors.ClientException;
import com.lark.agent.module.component.ModelClient;
import com.lark.agent.module.component.ModelRateLimitException;
import com.lark.oapi.channel.model.NormalizedMessage;
import com.lark.oapi.core.utils.Strings;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 处理文本消息：组装模型上下文、调用模型，并回写 Lark。
 */
@Service
public class TextMessageHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(TextMessageHandler.class);

    private static final String TEXT_MESSAGE_TYPE = "text";
    private static final String MODEL_QUOTA_EXCEEDED_REPLY = "当前模型调用较频繁，请稍后再试。";
    private static final String MODEL_CALL_FAILED_REPLY = "当前服务暂时不可用，请稍后再试。";
    private static final String CLEAR_MEMORY_REPLY = "已清空当前私聊上下文。";

    @Resource
    private ModelClient modelClient;

    @Resource
    private MessageContextAssembler messageContextAssembler;

    @Resource
    private LarkMessageReplyService larkMessageReplyService;

    @Resource
    private ConversationMemoryService conversationMemoryService;

    @Resource
    private ConversationCommandService conversationCommandService;

    /**
     * 判断当前处理器是否支持这条标准化消息。
     *
     * @param message 标准化后的 Lark 消息。
     * @return 消息是非空文本消息，且包含会话 ID 与消息 ID 时返回 true。
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
     * 处理文本消息，并把模型响应流式写入 Lark 卡片。
     *
     * @param message 标准化后的 Lark 文本消息。
     */
    @Override
    public void handle(NormalizedMessage message) {
        try {
            if (tryHandleClearMemoryCommand(message)) {
                return;
            }
            String context = messageContextAssembler.assemble(message);
            String assistantReply = larkMessageReplyService.replyStreamingCard(
                    message.getChatId(),
                    message.getMessageId(),
                    chunkConsumer -> modelClient.stream(context, message.getContent(), chunkConsumer)
            );
            rememberPrivateChatTurns(message, assistantReply);
        } catch (ModelRateLimitException e) {
            log.warn("Model quota exceeded. chatId={}, messageId={}", message.getChatId(), message.getMessageId(), e);
            larkMessageReplyService.replyText(message.getChatId(), message.getMessageId(), MODEL_QUOTA_EXCEEDED_REPLY);
        } catch (ClientException e) {
            if (isQuotaExceeded(e)) {
                log.warn("Model quota exceeded. chatId={}, messageId={}", message.getChatId(), message.getMessageId(), e);
                larkMessageReplyService.replyText(message.getChatId(), message.getMessageId(), MODEL_QUOTA_EXCEEDED_REPLY);
                return;
            }
            log.error("Model client exception. chatId={}, messageId={}", message.getChatId(), message.getMessageId(), e);
            larkMessageReplyService.replyText(message.getChatId(), message.getMessageId(), MODEL_CALL_FAILED_REPLY);
        } catch (Exception e) {
            log.error("Handle text message failed. chatId={}, messageId={}", message.getChatId(), message.getMessageId(), e);
            larkMessageReplyService.replyText(message.getChatId(), message.getMessageId(), MODEL_CALL_FAILED_REPLY);
        }
    }

    /**
     * 判断 Gemini 客户端异常是否表示配额耗尽或限流。
     *
     * @param exception Gemini SDK 客户端异常。
     * @return 异常看起来是配额或限流问题时返回 true。
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

    /**
     * 识别并处理私聊上下文清除指令。
     *
     * @param message 当前用户消息。
     * @return 已处理清除指令时返回 true。
     */
    private boolean tryHandleClearMemoryCommand(NormalizedMessage message) {
        if (!"p2p".equalsIgnoreCase(message.getChatType())) {
            return false;
        }
        String command = messageContextAssembler.extractText(message.getContent());
        if (!conversationCommandService.isClearMemoryCommand(command)) {
            return false;
        }
        conversationMemoryService.clear(message.getChatId());
        larkMessageReplyService.replyText(message.getChatId(), message.getMessageId(), CLEAR_MEMORY_REPLY);
        return true;
    }

    /**
     * 把私聊中的用户消息和助手回复写入本地滚动记忆。
     *
     * @param message 当前用户消息。
     * @param assistantReply 助手完整回复。
     */
    private void rememberPrivateChatTurns(NormalizedMessage message, String assistantReply) {
        if (!"p2p".equalsIgnoreCase(message.getChatType()) || Strings.isEmpty(assistantReply)) {
            return;
        }
        conversationMemoryService.append(
                message.getChatId(),
                new ConversationTurn(
                        message.getMessageId(),
                        "用户",
                        message.getSenderName(),
                        messageContextAssembler.extractText(message.getContent()),
                        message.getCreateTime()
                )
        );
        conversationMemoryService.append(
                message.getChatId(),
                new ConversationTurn(
                        null,
                        "助手",
                        "机器人",
                        assistantReply,
                        System.currentTimeMillis()
                )
        );
    }
}
