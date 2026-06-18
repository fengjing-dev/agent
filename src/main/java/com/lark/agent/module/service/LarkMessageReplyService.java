package com.lark.agent.module.service;

import com.lark.oapi.channel.LarkChannel;
import com.lark.oapi.channel.model.SendInput;
import com.lark.oapi.channel.model.SendOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 负责向 Lark 回写消息，避免消息处理器反向依赖长连接管理器。
 */
@Service
public class LarkMessageReplyService {

    private static final Logger log = LoggerFactory.getLogger(LarkMessageReplyService.class);

    private LarkChannel channel;

    /**
     * 在长连接建立后注入可用的 channel。
     *
     * @param channel Lark 长连接对象
     */
    public void setChannel(LarkChannel channel) {
        this.channel = channel;
    }

    /**
     * 回复文本消息。
     *
     * @param chatId 聊天 ID
     * @param messageId 消息 ID
     * @param content 回复内容
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
}
