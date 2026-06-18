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
 * 负责把当前消息扩展成适合提交给模型的上下文文本。
 *
 * <p>群聊场景优先追溯引用链，私聊场景默认补最近几条历史消息。</p>
 */
@Service
public class MessageContextAssembler {

    private static final String PRIVATE_CHAT_TYPE = "p2p";
    private static final String TEXT_MESSAGE_TYPE = "text";
    private static final String ROLE_USER = "用户";
    private static final String ROLE_ASSISTANT = "助手";

    private final AgentProperties agentProperties;
    private final LarkMessageQueryService larkMessageQueryService;

    /**
     * @param agentProperties 上下文相关配置
     * @param larkMessageQueryService Lark 消息查询服务
     */
    public MessageContextAssembler(AgentProperties agentProperties, LarkMessageQueryService larkMessageQueryService) {
        this.agentProperties = agentProperties;
        this.larkMessageQueryService = larkMessageQueryService;
    }

    /**
     * 群聊优先沿引用链补齐上下文；私聊默认补最近几条消息；其他情况只发当前消息。
     *
     * <p>最终发给模型的上下文格式示例：</p>
     * <p>用户(张三): 昨天那个支付失败是什么原因？</p>
     * <p>助手(机器人): 日志显示是渠道超时。</p>
     * <p>用户(张三): 那要怎么补偿？</p>
     *
     * @param currentMessage 当前收到的消息
     * @return 组装后的上下文文本
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
     * 按引用链从旧到新拼接群聊上下文。
     *
     * @param currentMessage 当前消息
     * @return 群聊引用链上下文
     */
    private String buildReplyChainContext(NormalizedMessage currentMessage) {
        List<String> lines = new ArrayList<>();
        String nextMessageId = currentMessage.getReplyToMessageId();
        int depth = 0;

        // 逐层向上追溯被引用消息，直到没有上级引用或达到配置的最大深度。
        while (!Strings.isEmpty(nextMessageId) && depth < agentProperties.getGroupReplyChainDepth()) {
            Optional<Message> optionalMessage = larkMessageQueryService.getMessageById(nextMessageId);
            if (optionalMessage.isEmpty()) {
                break;
            }
            Message message = optionalMessage.get();
            if (isTextMessage(message)) {
                // 引用链需要按时间正序给模型，所以每次把更早的消息插到最前面。
                lines.add(0, formatQueriedMessage(message));
            }
            nextMessageId = resolveNextReplyMessageId(message);
            depth++;
        }
        lines.add(formatCurrentMessage(currentMessage));
        return joinContextLines(lines);
    }

    /**
     * 读取私聊最近若干条历史消息并按时间顺序拼接上下文。
     *
     * @param currentMessage 当前消息
     * @return 私聊历史上下文
     */
    private String buildPrivateChatContext(NormalizedMessage currentMessage) {
        int historySize = Math.max(agentProperties.getPrivateChatContextSize(), 0);
        List<Message> messages = larkMessageQueryService.listRecentMessages(currentMessage.getChatId(), historySize + 1);
        List<String> lines = messages.stream()
                .filter(this::isTextMessage)
                .filter(message -> !currentMessage.getMessageId().equals(message.getMessageId()))
                .filter(message -> shouldIncludeHistoryMessage(currentMessage, message))
                // 查询接口按倒序返回，这里重新排成时间正序，便于模型理解上下文演进。
                .sorted(Comparator.comparingLong(this::parseCreateTime))
                .limit(historySize)
                .map(this::formatQueriedMessage)
                .collect(Collectors.toCollection(ArrayList::new));
        lines.add(formatCurrentMessage(currentMessage));
        return joinContextLines(lines);
    }

    /**
     * 判断某条历史消息是否需要继续带入模型上下文。
     *
     * @param currentMessage 当前消息
     * @param message 历史消息
     * @return true 表示应带入上下文
     */
    private boolean shouldIncludeHistoryMessage(NormalizedMessage currentMessage, Message message) {
        if (agentProperties.getIncludeBotHistory()) {
            return true;
        }
        Sender sender = message.getSender();
        if (sender == null) {
            return true;
        }
        // 默认不把机器人自己的历史回复继续喂回模型，避免上下文越来越像“自言自语”。
        if ("app".equalsIgnoreCase(sender.getSenderType()) || "bot".equalsIgnoreCase(sender.getSenderType())) {
            return false;
        }
        // 私聊上下文需要保留用户自己之前的提问，否则会把连续追问链条全部截断。
        return true;
    }

    /**
     * @param message 当前消息
     * @return 是否为私聊消息
     */
    private boolean isPrivateChat(NormalizedMessage message) {
        return PRIVATE_CHAT_TYPE.equalsIgnoreCase(message.getChatType());
    }

    /**
     * @param message 查询得到的消息
     * @return 是否为可直接投喂模型的文本消息
     */
    private boolean isTextMessage(Message message) {
        return message != null
                && TEXT_MESSAGE_TYPE.equalsIgnoreCase(message.getMsgType())
                && message.getBody() != null
                && !Strings.isEmpty(message.getBody().getContent());
    }

    /**
     * 在不同消息结构中定位上一层引用消息的 ID。
     *
     * @param message 当前引用链节点
     * @return 上一层引用消息 ID，不存在则返回 null
     */
    private String resolveNextReplyMessageId(Message message) {
        // Lark 不同消息结构里，上一级引用可能落在 parentId 或 upperMessageId。
        if (!Strings.isEmpty(message.getParentId())) {
            return message.getParentId();
        }
        if (!Strings.isEmpty(message.getUpperMessageId())) {
            return message.getUpperMessageId();
        }
        return null;
    }

    /**
     * @param message 当前消息
     * @return 当前消息格式化后的上下文行
     */
    private String formatCurrentMessage(NormalizedMessage message) {
        return formatLine(ROLE_USER, message.getSenderName(), extractText(message.getContent()));
    }

    /**
     * @param message 查询得到的历史消息
     * @return 历史消息格式化后的上下文行
     */
    private String formatQueriedMessage(Message message) {
        Sender sender = message.getSender();
        String senderName = sender == null ? null : sender.getSenderName();
        String senderType = sender == null ? null : sender.getSenderType();
        return formatLine(resolveRole(senderType), senderName, extractText(message.getBody().getContent()));
    }

    /**
     * @param senderType 发送者类型
     * @return 上下文展示用角色名
     */
    private String resolveRole(String senderType) {
        if ("app".equalsIgnoreCase(senderType) || "bot".equalsIgnoreCase(senderType)) {
            return ROLE_ASSISTANT;
        }
        return ROLE_USER;
    }

    /**
     * @param role 角色
     * @param senderName 发送者名称
     * @param content 文本内容
     * @return 标准化后的单行上下文
     */
    private String formatLine(String role, String senderName, String content) {
        String displayName = Strings.isEmpty(senderName) ? role : senderName;
        return role + "(" + displayName + "): " + content;
    }

    /**
     * 从 Lark 文本消息 JSON 中提纯纯文本内容。
     *
     * @param rawContent 原始消息内容
     * @return 尽量可读的文本
     */
    private String extractText(String rawContent) {
        if (Strings.isEmpty(rawContent)) {
            return "";
        }
        String normalizedContent = rawContent.trim();
        if (!looksLikeJson(normalizedContent)) {
            return normalizedContent;
        }
        try {
            // 文本消息内容通常是 JSON 包裹的 {"text":"..."}，这里尽量只提纯可读文本。
            JsonNode jsonNode = JsonUtils.parseTree(normalizedContent);
            JsonNode textNode = jsonNode.path("text");
            if (!textNode.isMissingNode() && !textNode.isNull()) {
                return textNode.asText();
            }
        } catch (RuntimeException ignored) {
            // Keep the raw content when it is already normalized plain text.
        }
        return normalizedContent;
    }

    /**
     * 避免把普通文本误当成 JSON 解析，从而打出无意义的错误日志。
     *
     * @param content 待判断内容
     * @return true 表示值得尝试按 JSON 解析
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
     * @param message 历史消息
     * @return 创建时间戳，解析失败时返回最小值
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
     * @param lines 上下文行列表
     * @return 换行拼接后的完整上下文
     */
    private String joinContextLines(List<String> lines) {
        return lines.stream()
                .filter(line -> !Strings.isEmpty(line))
                .collect(Collectors.joining("\n"));
    }
}
