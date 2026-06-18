package com.lark.agent.module.service;

import com.lark.agent.module.component.GeminiClient;
import com.lark.oapi.channel.model.NormalizedMessage;
import com.lark.oapi.core.utils.Strings;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 文本消息处理器，负责筛选文本消息、组装上下文并调用模型回复。
 *
 * @Author: fatina 2026/06/18
 */
@Service
public class TextMessageHandler implements MessageHandler {

    private static final String TEXT_MESSAGE_TYPE = "text";

    @Resource
    private GeminiClient geminiClient;
    @Resource
    private LarkChannelManager larkChannelManager;
    @Resource
    private MessageContextAssembler messageContextAssembler;

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
        // 先把群聊引用链或私聊最近几条消息整理成上下文，再一并交给模型。
        String context = messageContextAssembler.assemble(message);
        // 路由仍只基于当前提问判断领域，避免历史上下文把 prompt 分类带偏。
        String reply = geminiClient.call(context, message.getContent());
        larkChannelManager.replyText(message.getChatId(), message.getMessageId(), reply);
    }
}
