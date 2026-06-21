package com.lark.agent.module.service;

import com.lark.oapi.channel.model.NormalizedMessage;

/**
 * Strategy interface for handling normalized Lark messages.
 */
public interface MessageHandler {

    /**
     * Checks whether this handler supports the provided message.
     *
     * @param message normalized Lark message.
     * @return true when this handler should process the message.
     */
    boolean support(NormalizedMessage message);

    /**
     * Processes a supported message.
     *
     * @param message normalized Lark message.
     */
    void handle(NormalizedMessage message);
}
