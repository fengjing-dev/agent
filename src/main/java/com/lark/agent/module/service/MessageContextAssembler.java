package com.lark.agent.module.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lark.agent.module.properties.AgentProperties;
import com.lark.agent.module.utils.JsonUtils;
import com.lark.oapi.channel.model.NormalizedMessage;
import com.lark.oapi.core.utils.Strings;
import com.lark.oapi.service.im.v1.model.Message;
import com.lark.oapi.service.im.v1.model.Sender;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 将 Lark 消息组装成适合模型输入的纯文本上下文。
 */
@Service
public class MessageContextAssembler {

    private static final String PRIVATE_CHAT_TYPE = "p2p";
    private static final String TEXT_MESSAGE_TYPE = "text";
    private static final String INTERACTIVE_MESSAGE_TYPE = "interactive";
    private static final String CARD_MESSAGE_TYPE = "card";
    private static final String ROLE_USER = "用户";
    private static final String ROLE_ASSISTANT = "助手";
    private static final int PRIVATE_CHAT_HISTORY_FETCH_MULTIPLIER = 3;

    private final AgentProperties agentProperties;
    private final LarkMessageQueryService larkMessageQueryService;
    private final ConversationMemoryService conversationMemoryService;
    private final ConversationCommandService conversationCommandService;

    /**
     * 创建上下文组装器。
     *
     * @param agentProperties 上下文相关配置。
     * @param larkMessageQueryService 用于查询 Lark 历史消息的服务。
     * @param conversationMemoryService 用于缓存私聊轮次的本地记忆服务。
     * @param conversationCommandService 用于识别会话级文本指令的服务。
     */
    public MessageContextAssembler(
            AgentProperties agentProperties,
            LarkMessageQueryService larkMessageQueryService,
            ConversationMemoryService conversationMemoryService,
            ConversationCommandService conversationCommandService
    ) {
        this.agentProperties = agentProperties;
        this.larkMessageQueryService = larkMessageQueryService;
        this.conversationMemoryService = conversationMemoryService;
        this.conversationCommandService = conversationCommandService;
    }

    /**
     * 为当前消息构建模型上下文。
     *
     * @param currentMessage 当前 Lark 消息。
     * @return 组装后的上下文文本。
     */
    public String assemble(NormalizedMessage currentMessage) {
        if (isPrivateChat(currentMessage)) {
            return buildPrivateChatContext(currentMessage);
        }
        if (!Strings.isEmpty(currentMessage.getReplyToMessageId())) {
            return buildReplyChainContext(currentMessage);
        }
        return formatCurrentMessage(currentMessage);
    }

    /**
     * 沿群聊引用链向上追溯，并按从旧到新的顺序构建上下文。
     *
     * @param currentMessage 当前群聊消息。
     * @return 包含引用消息和当前消息的上下文文本。
     */
    private String buildReplyChainContext(NormalizedMessage currentMessage) {
        List<String> lines = new ArrayList<>();
        String nextMessageId = currentMessage.getReplyToMessageId();
        int depth = 0;

        while (!Strings.isEmpty(nextMessageId) && depth < agentProperties.getGroupReplyChainDepth()) {
            Optional<Message> optionalMessage = larkMessageQueryService.getMessageById(nextMessageId);
            if (optionalMessage.isEmpty()) {
                break;
            }
            Message message = optionalMessage.get();
            if (isReadableContextMessage(message)) {
                lines.add(0, formatQueriedMessage(message));
            }
            nextMessageId = resolveNextReplyMessageId(message);
            depth++;
        }
        lines.add(formatCurrentMessage(currentMessage));
        return joinContextLines(lines);
    }

    /**
     * 构建私聊上下文：优先使用本地滚动记忆，首次会话用 Lark 历史冷启动。
     *
     * @param currentMessage 当前私聊消息。
     * @return 包含最近历史和当前消息的上下文文本。
     */
    private String buildPrivateChatContext(NormalizedMessage currentMessage) {
        int historySize = Math.max(agentProperties.getPrivateChatContextSize(), 0);
        if (historySize == 0) {
            return formatCurrentMessage(currentMessage);
        }
        if (conversationMemoryService.isInitialized(currentMessage.getChatId())) {
            return buildPrivateChatContextFromMemory(currentMessage, historySize);
        }
        return buildPrivateChatContextFromLarkHistory(currentMessage, historySize);
    }

    /**
     * 从本地滚动记忆构建私聊上下文。
     *
     * @param currentMessage 当前私聊消息。
     * @param historySize 最多带入的历史轮次数量。
     * @return 包含本地记忆和当前消息的上下文文本。
     */
    private String buildPrivateChatContextFromMemory(NormalizedMessage currentMessage, int historySize) {
        List<String> lines = conversationMemoryService.recent(currentMessage.getChatId(), historySize).stream()
                .map(this::formatMemoryTurn)
                .collect(Collectors.toCollection(ArrayList::new));
        lines.add(formatCurrentMessage(currentMessage));
        return joinContextLines(lines);
    }

    /**
     * 从 Lark 历史消息冷启动私聊上下文，并初始化本地记忆。
     *
     * @param currentMessage 当前私聊消息。
     * @param historySize 最多带入的历史轮次数量。
     * @return 包含冷启动历史和当前消息的上下文文本。
     */
    private String buildPrivateChatContextFromLarkHistory(NormalizedMessage currentMessage, int historySize) {
        int querySize = historySize * PRIVATE_CHAT_HISTORY_FETCH_MULTIPLIER + 1;
        List<Message> messages = larkMessageQueryService.listRecentMessages(currentMessage.getChatId(), querySize);
        List<Message> historyAfterLastClearCommand = filterAfterLastClearCommand(messages);
        List<Message> recentHistory = historyAfterLastClearCommand.stream()
                .filter(this::isReadableContextMessage)
                .filter(message -> !currentMessage.getMessageId().equals(message.getMessageId()))
                .filter(message -> !isClearMemoryCommand(message))
                .sorted(Comparator.comparingLong(this::parseCreateTime).reversed())
                .limit(historySize)
                .sorted(Comparator.comparingLong(this::parseCreateTime))
                .collect(Collectors.toCollection(ArrayList::new));
        conversationMemoryService.seed(
                currentMessage.getChatId(),
                recentHistory.stream()
                        .map(this::toConversationTurn)
                        .collect(Collectors.toCollection(ArrayList::new))
        );
        List<String> lines = recentHistory.stream()
                .map(this::formatQueriedMessage)
                .collect(Collectors.toCollection(ArrayList::new));
        lines.add(formatCurrentMessage(currentMessage));
        return joinContextLines(lines);
    }

    /**
     * 只保留最后一次清除指令之后的历史消息，避免应用重启后把已清空的旧上下文重新带入模型。
     *
     * @param messages Lark 返回的历史消息，通常为倒序。
     * @return 最后一次清除指令之后的历史消息。
     */
    private List<Message> filterAfterLastClearCommand(List<Message> messages) {
        Optional<Long> latestClearTime = messages.stream()
                .filter(this::isTextMessage)
                .filter(this::isClearMemoryCommand)
                .map(this::parseCreateTime)
                .max(Long::compareTo);
        if (latestClearTime.isEmpty()) {
            return messages;
        }
        long clearTime = latestClearTime.get();
        return messages.stream()
                .filter(message -> parseCreateTime(message) > clearTime)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 判断历史消息是否是清除上下文指令。
     *
     * @param message Lark 历史消息。
     * @return 是清除上下文指令时返回 true。
     */
    private boolean isClearMemoryCommand(Message message) {
        if (!isTextMessage(message)) {
            return false;
        }
        return conversationCommandService.isClearMemoryCommand(extractText(message.getBody().getContent()));
    }

    /**
     * 判断标准化消息是否来自私聊。
     *
     * @param message 标准化后的 Lark 消息。
     * @return 会话类型是私聊时返回 true。
     */
    private boolean isPrivateChat(NormalizedMessage message) {
        return PRIVATE_CHAT_TYPE.equalsIgnoreCase(message.getChatType());
    }

    /**
     * 判断查询到的 Lark 消息是否能作为上下文读取。
     *
     * @param message 查询到的 Lark 消息。
     * @return 可以加入文本上下文时返回 true。
     */
    private boolean isReadableContextMessage(Message message) {
        return message != null
                && isReadableMessageType(message.getMsgType())
                && message.getBody() != null
                && !Strings.isEmpty(message.getBody().getContent());
    }

    /**
     * 判断消息类型是否能提取为文本上下文。
     *
     * @param messageType Lark 消息类型。
     * @return 文本消息或卡片消息返回 true。
     */
    private boolean isReadableMessageType(String messageType) {
        return TEXT_MESSAGE_TYPE.equalsIgnoreCase(messageType)
                || INTERACTIVE_MESSAGE_TYPE.equalsIgnoreCase(messageType)
                || CARD_MESSAGE_TYPE.equalsIgnoreCase(messageType);
    }

    /**
     * 判断查询到的 Lark 消息是否是文本消息。
     *
     * @param message 查询到的 Lark 消息。
     * @return 是文本消息且内容可读取时返回 true。
     */
    private boolean isTextMessage(Message message) {
        return message != null
                && TEXT_MESSAGE_TYPE.equalsIgnoreCase(message.getMsgType())
                && message.getBody() != null
                && !Strings.isEmpty(message.getBody().getContent());
    }

    /**
     * 解析引用链上一级消息 ID。
     *
     * @param message 当前引用链节点。
     * @return 上一级消息 ID；没有上级时返回 null。
     */
    private String resolveNextReplyMessageId(Message message) {
        if (!Strings.isEmpty(message.getParentId())) {
            return message.getParentId();
        }
        if (!Strings.isEmpty(message.getUpperMessageId())) {
            return message.getUpperMessageId();
        }
        return null;
    }

    /**
     * 将当前标准化消息格式化为一行上下文。
     *
     * @param message 当前标准化消息。
     * @return 格式化后的上下文行。
     */
    private String formatCurrentMessage(NormalizedMessage message) {
        return formatLine(ROLE_USER, message.getSenderName(), extractText(message.getContent()));
    }

    /**
     * 将查询到的历史消息格式化为一行上下文。
     *
     * @param message 查询到的历史消息。
     * @return 格式化后的上下文行。
     */
    private String formatQueriedMessage(Message message) {
        Sender sender = message.getSender();
        String senderName = sender == null ? null : sender.getSenderName();
        String senderType = sender == null ? null : sender.getSenderType();
        return formatLine(resolveRole(senderType), senderName, extractMessageContent(message));
    }

    /**
     * 将本地记忆轮次格式化为一行上下文。
     *
     * @param turn 本地对话轮次。
     * @return 格式化后的上下文行。
     */
    private String formatMemoryTurn(ConversationTurn turn) {
        return formatLine(turn.getRole(), turn.getSenderName(), turn.getContent());
    }

    /**
     * 将查询到的 Lark 消息转换为本地记忆轮次。
     *
     * @param message 查询到的历史消息。
     * @return 本地对话轮次。
     */
    private ConversationTurn toConversationTurn(Message message) {
        Sender sender = message.getSender();
        String senderName = sender == null ? null : sender.getSenderName();
        String senderType = sender == null ? null : sender.getSenderType();
        return new ConversationTurn(
                message.getMessageId(),
                resolveRole(senderType),
                senderName,
                extractMessageContent(message),
                parseCreateTime(message)
        );
    }

    /**
     * 根据消息类型提取可进入上下文的文本内容。
     *
     * @param message Lark 历史消息。
     * @return 可读文本内容。
     */
    private String extractMessageContent(Message message) {
        if (TEXT_MESSAGE_TYPE.equalsIgnoreCase(message.getMsgType())) {
            return extractText(message.getBody().getContent());
        }
        return extractCardText(message.getBody().getContent());
    }

    /**
     * 根据 Lark 发送方类型解析展示角色。
     *
     * @param senderType Lark 发送方类型。
     * @return 机器人发送方返回助手角色，否则返回用户角色。
     */
    private String resolveRole(String senderType) {
        if ("app".equalsIgnoreCase(senderType) || "bot".equalsIgnoreCase(senderType)) {
            return ROLE_ASSISTANT;
        }
        return ROLE_USER;
    }

    /**
     * 使用角色、发送人名称和消息内容格式化一行上下文。
     *
     * @param role 归一化后的角色标签。
     * @param senderName 原始发送人展示名称。
     * @param content 纯文本内容。
     * @return 格式化后的上下文行。
     */
    private String formatLine(String role, String senderName, String content) {
        String displayName = Strings.isEmpty(senderName) ? role : senderName;
        return role + "(" + displayName + "): " + content;
    }

    /**
     * 从 Lark 文本消息 JSON 内容中提取可读文本。
     *
     * @param rawContent Lark 原始内容。
     * @return 提取后的文本；不是 JSON 时返回原始内容。
     */
    public String extractText(String rawContent) {
        if (Strings.isEmpty(rawContent)) {
            return "";
        }
        String normalizedContent = rawContent.trim();
        if (!looksLikeJson(normalizedContent)) {
            return normalizedContent;
        }
        try {
            JsonNode jsonNode = JsonUtils.parseTree(normalizedContent);
            JsonNode textNode = jsonNode.path("text");
            if (!textNode.isMissingNode() && !textNode.isNull()) {
                return textNode.asText();
            }
        } catch (RuntimeException ignored) {
            // 已经是普通文本时保留原始内容。
        }
        return normalizedContent;
    }

    /**
     * 从 Lark 卡片 JSON 中提取 Markdown 或纯文本内容。
     *
     * @param rawContent Lark 卡片原始内容。
     * @return 提取后的卡片文本；提取失败时返回原始内容。
     */
    public String extractCardText(String rawContent) {
        if (Strings.isEmpty(rawContent)) {
            return "";
        }
        String normalizedContent = rawContent.trim();
        if (!looksLikeJson(normalizedContent)) {
            return normalizedContent;
        }
        try {
            JsonNode root = JsonUtils.parseTree(normalizedContent);
            List<String> texts = new ArrayList<>();
            collectCardText(root, texts);
            if (!texts.isEmpty()) {
                return joinContextLines(texts);
            }
        } catch (RuntimeException ignored) {
            // 卡片结构不可解析时保留原始内容。
        }
        return normalizedContent;
    }

    /**
     * 递归收集卡片中的可读文本节点。
     *
     * @param node 当前 JSON 节点。
     * @param texts 已收集文本。
     */
    private void collectCardText(JsonNode node, List<String> texts) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            JsonNode tagNode = node.path("tag");
            JsonNode contentNode = node.path("content");
            if (contentNode.isTextual() && isReadableCardTextTag(tagNode.asText())) {
                texts.add(contentNode.asText());
            }
            JsonNode textNode = node.path("text");
            if (textNode.isTextual() && isReadableCardTextTag(tagNode.asText())) {
                texts.add(textNode.asText());
            }
            node.fields().forEachRemaining(entry -> collectCardText(entry.getValue(), texts));
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectCardText(child, texts));
        }
    }

    /**
     * 判断卡片节点标签是否承载可读文本。
     *
     * @param tag 卡片节点标签。
     * @return 可读文本标签返回 true。
     */
    private boolean isReadableCardTextTag(String tag) {
        return "lark_md".equalsIgnoreCase(tag)
                || "plain_text".equalsIgnoreCase(tag)
                || "text".equalsIgnoreCase(tag);
    }

    /**
     * 在尝试 JSON 解析前做轻量格式判断。
     *
     * @param content 待判断内容。
     * @return 内容看起来像 JSON 对象、数组或字符串时返回 true。
     */
    private boolean looksLikeJson(String content) {
        if (Strings.isEmpty(content)) {
            return false;
        }
        char firstChar = content.charAt(0);
        char lastChar = content.charAt(content.length() - 1);
        return (firstChar == '{' && lastChar == '}')
                || (firstChar == '[' && lastChar == ']')
                || (firstChar == '"' && lastChar == '"');
    }

    /**
     * 解析 Lark 创建时间，用于按时间排序。
     *
     * @param message 历史消息。
     * @return 解析后的时间戳；解析失败时返回 {@link Long#MIN_VALUE}。
     */
    private long parseCreateTime(Message message) {
        if (message == null || Strings.isEmpty(message.getCreateTime())) {
            return Long.MIN_VALUE;
        }
        try {
            return Long.parseLong(message.getCreateTime());
        } catch (NumberFormatException ignored) {
            return Long.MIN_VALUE;
        }
    }

    /**
     * 使用换行符拼接非空上下文行。
     *
     * @param lines 上下文行。
     * @return 完整上下文字符串。
     */
    private String joinContextLines(List<String> lines) {
        return lines.stream()
                .filter(line -> !Strings.isEmpty(line))
                .collect(Collectors.joining("\n"));
    }
}
