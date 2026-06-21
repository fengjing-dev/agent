package com.lark.agent.module.service;

import com.lark.agent.module.properties.AgentProperties;
import com.lark.oapi.channel.model.NormalizedMessage;
import com.lark.oapi.service.im.v1.model.Message;
import com.lark.oapi.service.im.v1.model.MessageBody;
import com.lark.oapi.service.im.v1.model.Sender;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessageContextAssemblerTest {

    @Test
    void privateChatContextKeepsMostRecentMessagesInChronologicalOrder() {
        AgentProperties properties = new AgentProperties();
        properties.setPrivateChatContextSize(2);

        LarkMessageQueryService queryService = mock(LarkMessageQueryService.class);
        MessageContextAssembler assembler = new MessageContextAssembler(
                properties,
                queryService,
                new ConversationMemoryService(properties),
                new ConversationCommandService()
        );
        NormalizedMessage currentMessage = currentPrivateMessage("msg-current", "{\"text\":\"现在呢？\"}");

        when(queryService.listRecentMessages("chat-1", 7)).thenReturn(Arrays.asList(
                textMessage("msg-current", "5000", "user", "张三", "{\"text\":\"现在呢？\"}"),
                textMessage("msg-newest", "4000", "user", "张三", "{\"text\":\"第二条\"}"),
                cardMessage("msg-bot", "3500", "app", "机器人", "机器人历史"),
                textMessage("msg-recent", "3000", "user", "张三", "{\"text\":\"第一条\"}"),
                textMessage("msg-old", "1000", "user", "张三", "{\"text\":\"旧消息\"}")
        ));

        String context = assembler.assemble(currentMessage);

        assertEquals("助手(机器人): 机器人历史\n用户(张三): 第二条\n用户(张三): 现在呢？", context);
        verify(queryService).listRecentMessages("chat-1", 7);
    }

    @Test
    void privateChatContextSkipsHistoryLookupWhenContextSizeIsZero() {
        AgentProperties properties = new AgentProperties();
        properties.setPrivateChatContextSize(0);

        LarkMessageQueryService queryService = mock(LarkMessageQueryService.class);
        MessageContextAssembler assembler = new MessageContextAssembler(
                properties,
                queryService,
                new ConversationMemoryService(properties),
                new ConversationCommandService()
        );

        String context = assembler.assemble(currentPrivateMessage("msg-current", "{\"text\":\"只看当前\"}"));

        assertEquals("用户(张三): 只看当前", context);
        verifyNoInteractions(queryService);
    }

    @Test
    void privateChatColdStartIgnoresMessagesBeforeLatestClearCommand() {
        AgentProperties properties = new AgentProperties();
        properties.setPrivateChatContextSize(3);

        LarkMessageQueryService queryService = mock(LarkMessageQueryService.class);
        MessageContextAssembler assembler = new MessageContextAssembler(
                properties,
                queryService,
                new ConversationMemoryService(properties),
                new ConversationCommandService()
        );
        NormalizedMessage currentMessage = currentPrivateMessage("msg-current", "{\"text\":\"继续\"}");

        when(queryService.listRecentMessages("chat-1", 10)).thenReturn(Arrays.asList(
                textMessage("msg-current", "5000", "user", "张三", "{\"text\":\"继续\"}"),
                textMessage("msg-after-clear", "4000", "user", "张三", "{\"text\":\"清除之后\"}"),
                textMessage("msg-clear", "3000", "user", "张三", "{\"text\":\"/clear\"}"),
                textMessage("msg-before-clear", "2000", "user", "张三", "{\"text\":\"清除之前\"}")
        ));

        String context = assembler.assemble(currentMessage);

        assertEquals("用户(张三): 清除之后\n用户(张三): 继续", context);
        verify(queryService).listRecentMessages("chat-1", 10);
    }

    private NormalizedMessage currentPrivateMessage(String messageId, String content) {
        return new NormalizedMessage(
                messageId,
                "chat-1",
                "p2p",
                "user-1",
                "张三",
                content,
                "text",
                Collections.emptyList(),
                Collections.emptyList(),
                false,
                false,
                null,
                null,
                null,
                5000L,
                null
        );
    }

    private Message textMessage(String messageId, String createTime, String senderType, String senderName, String content) {
        Message message = new Message();
        message.setMessageId(messageId);
        message.setMsgType("text");
        message.setCreateTime(createTime);
        message.setSender(sender(senderType, senderName));
        message.setBody(body(content));
        return message;
    }

    private Message cardMessage(String messageId, String createTime, String senderType, String senderName, String content) {
        Message message = new Message();
        message.setMessageId(messageId);
        message.setMsgType("interactive");
        message.setCreateTime(createTime);
        message.setSender(sender(senderType, senderName));
        message.setBody(body("{\"title\":\"回答完成\",\"elements\":[[{\"tag\":\"text\",\"text\":\"" + content + "\"}]]}"));
        return message;
    }

    private Sender sender(String senderType, String senderName) {
        Sender sender = new Sender();
        sender.setSenderType(senderType);
        sender.setSenderName(senderName);
        return sender;
    }

    private MessageBody body(String content) {
        MessageBody body = new MessageBody();
        body.setContent(content);
        return body;
    }
}
