package com.lark.agent.module.service;

import com.lark.agent.module.properties.AgentProperties;
import com.lark.oapi.core.utils.Strings;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 在本地内存中保存最近的私聊对话轮次。
 */
@Service
public class ConversationMemoryService {

    private static final int MEMORY_CAPACITY_MULTIPLIER = 4;
    private static final int MIN_MEMORY_CAPACITY = 20;

    private final AgentProperties agentProperties;
    private final ConcurrentMap<String, Deque<ConversationTurn>> memoryByChatId = new ConcurrentHashMap<>();
    private final Set<String> initializedChatIds = ConcurrentHashMap.newKeySet();

    public ConversationMemoryService(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    /**
     * 判断当前会话是否已经从 Lark 历史消息冷启动过。
     *
     * @param chatId Lark 会话 ID。
     * @return 当前进程生命周期内已初始化时返回 true。
     */
    public boolean isInitialized(String chatId) {
        return initializedChatIds.contains(chatId);
    }

    /**
     * 将会话标记为已初始化，即使没有可用历史消息。
     *
     * @param chatId Lark 会话 ID。
     */
    public void markInitialized(String chatId) {
        if (!Strings.isEmpty(chatId)) {
            initializedChatIds.add(chatId);
        }
    }

    /**
     * 使用历史轮次初始化本地记忆，并避免重复写入相同消息 ID。
     *
     * @param chatId Lark 会话 ID。
     * @param turns 按时间正序排列的历史轮次。
     */
    public void seed(String chatId, List<ConversationTurn> turns) {
        if (Strings.isEmpty(chatId) || turns == null || turns.isEmpty()) {
            markInitialized(chatId);
            return;
        }
        Deque<ConversationTurn> memory = memoryByChatId.computeIfAbsent(chatId, ignored -> new ArrayDeque<>());
        synchronized (memory) {
            Set<String> existingMessageIds = collectMessageIds(memory);
            for (ConversationTurn turn : turns) {
                if (turn == null || isDuplicate(existingMessageIds, turn)) {
                    continue;
                }
                memory.addLast(turn);
                if (!Strings.isEmpty(turn.getMessageId())) {
                    existingMessageIds.add(turn.getMessageId());
                }
            }
            trim(memory);
        }
        markInitialized(chatId);
    }

    /**
     * 向本地记忆追加一条对话轮次。
     *
     * @param chatId Lark 会话 ID。
     * @param turn 要追加的对话轮次。
     */
    public void append(String chatId, ConversationTurn turn) {
        if (Strings.isEmpty(chatId) || turn == null || Strings.isEmpty(turn.getContent())) {
            return;
        }
        Deque<ConversationTurn> memory = memoryByChatId.computeIfAbsent(chatId, ignored -> new ArrayDeque<>());
        synchronized (memory) {
            if (isDuplicate(collectMessageIds(memory), turn)) {
                return;
            }
            memory.addLast(turn);
            trim(memory);
        }
        markInitialized(chatId);
    }

    /**
     * 按时间正序返回最近的对话轮次。
     *
     * @param chatId Lark 会话 ID。
     * @param limit 最多返回的轮次数量。
     * @return 最近的对话轮次。
     */
    public List<ConversationTurn> recent(String chatId, int limit) {
        if (Strings.isEmpty(chatId) || limit <= 0) {
            return List.of();
        }
        Deque<ConversationTurn> memory = memoryByChatId.get(chatId);
        if (memory == null) {
            return List.of();
        }
        synchronized (memory) {
            int skip = Math.max(memory.size() - limit, 0);
            List<ConversationTurn> turns = new ArrayList<>(Math.min(memory.size(), limit));
            int index = 0;
            for (ConversationTurn turn : memory) {
                if (index++ >= skip) {
                    turns.add(turn);
                }
            }
            return turns;
        }
    }

    /**
     * 清除指定会话的本地记忆，并标记为已初始化，避免下一条消息立刻重新从 Lark 捞回旧历史。
     *
     * @param chatId Lark 会话 ID。
     */
    public void clear(String chatId) {
        if (Strings.isEmpty(chatId)) {
            return;
        }
        memoryByChatId.remove(chatId);
        initializedChatIds.add(chatId);
    }

    private Set<String> collectMessageIds(Deque<ConversationTurn> memory) {
        Set<String> messageIds = new HashSet<>();
        for (ConversationTurn turn : memory) {
            if (!Strings.isEmpty(turn.getMessageId())) {
                messageIds.add(turn.getMessageId());
            }
        }
        return messageIds;
    }

    private boolean isDuplicate(Set<String> existingMessageIds, ConversationTurn turn) {
        return !Strings.isEmpty(turn.getMessageId()) && existingMessageIds.contains(turn.getMessageId());
    }

    private void trim(Deque<ConversationTurn> memory) {
        int capacity = Math.max(agentProperties.getPrivateChatContextSize() * MEMORY_CAPACITY_MULTIPLIER, MIN_MEMORY_CAPACITY);
        while (memory.size() > capacity) {
            memory.removeFirst();
        }
    }
}
