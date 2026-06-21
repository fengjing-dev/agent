package com.lark.agent.module.service;

import com.lark.agent.module.utils.JsonUtils;
import com.lark.oapi.channel.model.NormalizedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 将标准化后的 Lark 消息分发给第一个匹配的消息处理器。
 */
@Component
public class ManagerEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ManagerEventDispatcher.class);

    private final List<MessageHandler> messageHandlers;

    /**
     * 使用所有已注册的消息处理器创建分发器。
     *
     * @param messageHandlers 可处理消息的有序 Spring Bean 列表。
     */
    public ManagerEventDispatcher(List<MessageHandler> messageHandlers) {
        this.messageHandlers = messageHandlers;
    }

    /**
     * 查找支持当前消息的处理器并转交处理。
     *
     * @param message 从长连接收到的标准化 Lark 消息。
     */
    public void handleMessage(NormalizedMessage message) {
        if (message == null) {
            log.warn("Received null message from Lark channel.");
            return;
        }
        log.info("Received message: chatId={}, messageId={}, chatType={}, rawContentType={}, senderId={}, mentionedBot={}, mentionAll={}",
                message.getChatId(),
                message.getMessageId(),
                message.getChatType(),
                message.getRawContentType(),
                message.getSenderId(),
                message.isMentionedBot(),
                message.isMentionAll());
        log.debug("Received message detail: {}", JsonUtils.toJsonString(message));
        for (MessageHandler messageHandler : messageHandlers) {
            if (messageHandler.support(message)) {
                messageHandler.handle(message);
                return;
            }
        }
        log.warn("No message handler matched. chatId={}, messageId={}",
                message.getChatId(), message.getMessageId());
    }
}
