package com.lark.agent.module.service;

import com.lark.agent.module.utils.JsonUtils;
import com.lark.oapi.channel.model.NormalizedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Dispatches normalized Lark messages to the first matching message handler.
 */
@Component
public class ManagerEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ManagerEventDispatcher.class);

    private final List<MessageHandler> messageHandlers;

    /**
     * Creates a dispatcher with all registered message handlers.
     *
     * @param messageHandlers ordered Spring beans that can process messages.
     */
    public ManagerEventDispatcher(List<MessageHandler> messageHandlers) {
        this.messageHandlers = messageHandlers;
    }

    /**
     * Finds a supported handler and delegates the message to it.
     *
     * @param message normalized Lark message received from the long connection.
     */
    public void handleMessage(NormalizedMessage message) {
        log.info("Received message: chatId={}, messageId={}, content={}",
                message.getChatId(), message.getMessageId(), JsonUtils.toJsonString(message));
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
