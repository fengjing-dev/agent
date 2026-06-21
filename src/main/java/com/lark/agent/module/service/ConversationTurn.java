package com.lark.agent.module.service;

/**
 * 私聊本地记忆中的一条文本轮次。
 */
public class ConversationTurn {

    private final String messageId;
    private final String role;
    private final String senderName;
    private final String content;
    private final long createTime;

    public ConversationTurn(String messageId, String role, String senderName, String content, long createTime) {
        this.messageId = messageId;
        this.role = role;
        this.senderName = senderName;
        this.content = content;
        this.createTime = createTime;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getRole() {
        return role;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getContent() {
        return content;
    }

    public long getCreateTime() {
        return createTime;
    }
}
