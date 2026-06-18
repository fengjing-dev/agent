package com.lark.agent.module.service;

import com.lark.oapi.channel.model.NormalizedMessage;

/**
 * 消息处理器
 * @Author: fatina 2026/06/18
 */
public interface MessageHandler {

    /**
     * 是否支持
     * @param message 消息
     * @return 结果
     */
    boolean support(NormalizedMessage message);

    /**
     * 处理消息
     * @param message 消息
     */
    void handle(NormalizedMessage message);
}
