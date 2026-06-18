package com.lark.agent.module.service;

import com.lark.agent.module.utils.JsonUtils;
import com.lark.oapi.channel.model.NormalizedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动 Lark 长连接并消费消息事件的运行器。
 * @Author: Fatina 2026/06/17
 */
@Component
public class ManagerEventDispatcher{

    private static final Logger log = LoggerFactory.getLogger(ManagerEventDispatcher.class);

    private final List<MessageHandler> messageHandlers;

    public ManagerEventDispatcher(List<MessageHandler> messageHandlers) {
        this.messageHandlers = messageHandlers;
    }

    /**
     * 处理消息
     * @param message 消息
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
