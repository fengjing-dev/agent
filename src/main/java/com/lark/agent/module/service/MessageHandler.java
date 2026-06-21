package com.lark.agent.module.service;

import com.lark.oapi.channel.model.NormalizedMessage;

/**
 * 处理标准化 Lark 消息的策略接口。
 */
public interface MessageHandler {

    /**
     * 判断当前处理器是否支持给定消息。
     *
     * @param message 标准化后的 Lark 消息。
     * @return 当前处理器应处理该消息时返回 true。
     */
    boolean support(NormalizedMessage message);

    /**
     * 处理一条已确认支持的消息。
     *
     * @param message 标准化后的 Lark 消息。
     */
    void handle(NormalizedMessage message);
}
