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
 * Assembles Lark messages into plain-text context suitable for model input.
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
     * Creates an assembler with context configuration and Lark query access.
     *
     * @param agentProperties context-related agent configuration.
     * @param larkMessageQueryService service used to query Lark message history.
     */
    public MessageContextAssembler(AgentProperties agentProperties, LarkMessageQueryService larkMessageQueryService) {
        this.agentProperties = agentProperties;
        this.larkMessageQueryService = larkMessageQueryService;
    }

    /**
     * Builds model context for the current message.
     *
     * @param currentMessage current Lark message.
     * @return assembled context text.
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
     * Builds group-chat context by walking the reply chain from older messages to the current one.
     *
     * @param currentMessage current group message.
     * @return context text containing quoted messages and the current message.
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
            if (isTextMessage(message)) {
                lines.add(0, formatQueriedMessage(message));
            }
            nextMessageId = resolveNextReplyMessageId(message);
            depth++;
        }
        lines.add(formatCurrentMessage(currentMessage));
        return joinContextLines(lines);
    }

    /**
     * Builds private-chat context from recent text messages in chronological order.
     *
     * @param currentMessage current private-chat message.
     * @return context text containing recent history and the current message.
     */
    private String buildPrivateChatContext(NormalizedMessage currentMessage) {
        int historySize = Math.max(agentProperties.getPrivateChatContextSize(), 0);
        List<Message> messages = larkMessageQueryService.listRecentMessages(currentMessage.getChatId(), historySize + 1);
        List<String> lines = messages.stream()
                .filter(this::isTextMessage)
                .filter(message -> !currentMessage.getMessageId().equals(message.getMessageId()))
                .filter(message -> shouldIncludeHistoryMessage(message))
                .sorted(Comparator.comparingLong(this::parseCreateTime))
                .limit(historySize)
                .map(this::formatQueriedMessage)
                .collect(Collectors.toCollection(ArrayList::new));
        lines.add(formatCurrentMessage(currentMessage));
        return joinContextLines(lines);
    }

    /**
     * Decides whether a historical private-chat message should be included.
     *
     * @param message historical message returned by Lark.
     * @return true when the message should be included in model context.
     */
    private boolean shouldIncludeHistoryMessage(Message message) {
        if (agentProperties.getIncludeBotHistory()) {
            return true;
        }
        Sender sender = message.getSender();
        if (sender == null) {
            return true;
        }
        return !"app".equalsIgnoreCase(sender.getSenderType())
                && !"bot".equalsIgnoreCase(sender.getSenderType());
    }

    /**
     * Checks whether a normalized message belongs to a private chat.
     *
     * @param message normalized Lark message.
     * @return true when the chat type is private chat.
     */
    private boolean isPrivateChat(NormalizedMessage message) {
        return PRIVATE_CHAT_TYPE.equalsIgnoreCase(message.getChatType());
    }

    /**
     * Checks whether a queried Lark message is text and contains readable content.
     *
     * @param message queried Lark message.
     * @return true when the message can be added to text context.
     */
    private boolean isTextMessage(Message message) {
        return message != null
                && TEXT_MESSAGE_TYPE.equalsIgnoreCase(message.getMsgType())
                && message.getBody() != null
                && !Strings.isEmpty(message.getBody().getContent());
    }

    /**
     * Resolves the parent message ID for the next reply-chain lookup.
     *
     * @param message current reply-chain node.
     * @return parent message ID, or null when the chain has no parent.
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
     * Formats the current normalized message as one context line.
     *
     * @param message current normalized message.
     * @return formatted context line.
     */
    private String formatCurrentMessage(NormalizedMessage message) {
        return formatLine(ROLE_USER, message.getSenderName(), extractText(message.getContent()));
    }

    /**
     * Formats a queried historical message as one context line.
     *
     * @param message queried historical message.
     * @return formatted context line.
     */
    private String formatQueriedMessage(Message message) {
        Sender sender = message.getSender();
        String senderName = sender == null ? null : sender.getSenderName();
        String senderType = sender == null ? null : sender.getSenderType();
        return formatLine(resolveRole(senderType), senderName, extractText(message.getBody().getContent()));
    }

    /**
     * Resolves display role from Lark sender type.
     *
     * @param senderType Lark sender type.
     * @return assistant role for bot senders, otherwise user role.
     */
    private String resolveRole(String senderType) {
        if ("app".equalsIgnoreCase(senderType) || "bot".equalsIgnoreCase(senderType)) {
            return ROLE_ASSISTANT;
        }
        return ROLE_USER;
    }

    /**
     * Formats one context line with role, sender name, and message content.
     *
     * @param role normalized role label.
     * @param senderName original sender display name.
     * @param content plain text content.
     * @return formatted context line.
     */
    private String formatLine(String role, String senderName, String content) {
        String displayName = Strings.isEmpty(senderName) ? role : senderName;
        return role + "(" + displayName + "): " + content;
    }

    /**
     * Extracts readable text from Lark text-message JSON content.
     *
     * @param rawContent raw content from Lark.
     * @return extracted text, or the raw content when it is not JSON.
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
     * Performs a cheap shape check before attempting JSON parsing.
     *
     * @param content candidate content.
     * @return true when the content looks like a JSON object, array, or string.
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
     * Parses Lark create-time values for chronological sorting.
     *
     * @param message historical message.
     * @return parsed timestamp, or {@link Long#MIN_VALUE} when parsing fails.
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
     * Joins non-empty context lines using newlines.
     *
     * @param lines context lines.
     * @return complete context string.
     */
    private String joinContextLines(List<String> lines) {
        return lines.stream()
                .filter(line -> !Strings.isEmpty(line))
                .collect(Collectors.joining("\n"));
    }
}
