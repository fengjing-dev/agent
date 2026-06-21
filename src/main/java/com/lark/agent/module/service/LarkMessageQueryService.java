package com.lark.agent.module.service;

import com.lark.agent.module.properties.AgentProperties;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.model.GetMessageReq;
import com.lark.oapi.service.im.v1.model.GetMessageResp;
import com.lark.oapi.service.im.v1.model.ListMessageReq;
import com.lark.oapi.service.im.v1.model.ListMessageResp;
import com.lark.oapi.service.im.v1.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 封装上下文组装所需的 Lark IM 消息查询接口。
 */
@Service
public class LarkMessageQueryService {

    private static final Logger log = LoggerFactory.getLogger(LarkMessageQueryService.class);
    private static final String CHAT_CONTAINER_ID_TYPE = "chat";
    private static final String SORT_TYPE_DESC = "ByCreateTimeDesc";

    private final Client client;

    /**
     * 根据应用凭据创建 Lark OpenAPI 客户端。
     *
     * @param agentProperties Lark 应用配置。
     */
    public LarkMessageQueryService(AgentProperties agentProperties) {
        this.client = Client.newBuilder(agentProperties.getAppId(), agentProperties.getAppSecret()).build();
    }

    /**
     * 根据消息 ID 查询单条消息。
     *
     * @param messageId Lark 消息 ID。
     * @return 查询到的消息；接口失败或消息不存在时返回空。
     */
    public Optional<Message> getMessageById(String messageId) {
        try {
            GetMessageReq request = GetMessageReq.newBuilder()
                    .messageId(messageId)
                    .build();
            GetMessageResp response = client.im().message().get(request);
            if (!response.success() || response.getData() == null || response.getData().getItems() == null) {
                log.warn("Query message failed. messageId={}, code={}, msg={}", messageId, response.getCode(), response.getMsg());
                return Optional.empty();
            }
            return Arrays.stream(response.getData().getItems()).findFirst();
        } catch (Exception e) {
            log.warn("Query message exception. messageId={}", messageId, e);
            return Optional.empty();
        }
    }

    /**
     * 查询会话中的最近消息。
     *
     * @param chatId Lark 会话 ID。
     * @param pageSize 请求的最近消息数量。
     * @return 按接口顺序返回的最近消息；接口失败时返回空列表。
     */
    public List<Message> listRecentMessages(String chatId, int pageSize) {
        try {
            ListMessageReq request = ListMessageReq.newBuilder()
                    .containerIdType(CHAT_CONTAINER_ID_TYPE)
                    .containerId(chatId)
                    .sortType(SORT_TYPE_DESC)
                    .pageSize(pageSize)
                    .build();
            ListMessageResp response = client.im().message().list(request);
            if (!response.success() || response.getData() == null || response.getData().getItems() == null) {
                log.warn("List messages failed. chatId={}, code={}, msg={}", chatId, response.getCode(), response.getMsg());
                return Collections.emptyList();
            }
            return Arrays.asList(response.getData().getItems());
        } catch (Exception e) {
            log.warn("List messages exception. chatId={}", chatId, e);
            return Collections.emptyList();
        }
    }
}
