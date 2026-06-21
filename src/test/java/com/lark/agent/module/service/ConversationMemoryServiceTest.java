package com.lark.agent.module.service;

import com.lark.agent.module.properties.AgentProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationMemoryServiceTest {

    @Test
    void clearRemovesTurnsAndKeepsChatInitialized() {
        AgentProperties properties = new AgentProperties();
        properties.setPrivateChatContextSize(10);
        ConversationMemoryService memoryService = new ConversationMemoryService(properties);

        memoryService.append("chat-1", new ConversationTurn("msg-1", "用户", "张三", "第一条", 1L));
        memoryService.append("chat-1", new ConversationTurn("msg-2", "助手", "机器人", "回复", 2L));

        assertEquals(2, memoryService.recent("chat-1", 10).size());

        memoryService.clear("chat-1");

        assertTrue(memoryService.recent("chat-1", 10).isEmpty());
        assertTrue(memoryService.isInitialized("chat-1"));
    }
}
