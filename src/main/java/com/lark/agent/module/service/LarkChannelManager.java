package com.lark.agent.module.service;

import com.lark.agent.module.properties.AgentProperties;
import com.lark.agent.module.utils.JsonUtils;
import com.lark.oapi.channel.ChannelEventHandler;
import com.lark.oapi.channel.LarkChannel;
import com.lark.oapi.channel.LarkChannelFactory;
import com.lark.oapi.channel.config.LarkChannelOptions;
import com.lark.oapi.channel.config.LarkChannelOptions.PolicyConfig;
import com.lark.oapi.channel.model.ChannelErrorEvent;
import com.lark.oapi.channel.model.NormalizedMessage;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * 管理 Lark 长连接生命周期，并注册通道事件处理器。
 */
@Component
public class LarkChannelManager {

    private static final Logger log = LoggerFactory.getLogger(LarkChannelManager.class);

    private final AgentProperties properties;

    private LarkChannel channel;

    @Resource
    private ManagerEventDispatcher dispatcher;

    @Resource
    private LarkMessageReplyService larkMessageReplyService;

    /**
     * 使用 Lark 机器人配置创建通道管理器。
     *
     * @param properties Lark 机器人配置。
     */
    public LarkChannelManager(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * 创建 Lark 通道，注册处理器，并打开 websocket 连接。
     */
    public void connection() {
        try {
            this.channel = LarkChannelFactory.createLarkChannel(buildOptions());
            larkMessageReplyService.setChannel(this.channel);
            registerHandlers();
            this.channel.connect().get();
            log.info("Lark websocket connected. domain={}", properties.getDomain());
        } catch (Exception e) {
            log.error("Lark websocket connect fail. domain={}", properties.getDomain(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Spring 应用关闭时断开 Lark 通道。
     */
    @PreDestroy
    public void shutdown() {
        if (channel == null) {
            return;
        }
        try {
            channel.disconnect().get();
            log.info("Lark websocket disconnected.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while disconnecting Lark websocket.", e);
        } catch (ExecutionException e) {
            log.warn("Failed to disconnect Lark websocket.", e);
        }
    }

    /**
     * 根据应用配置构建 Lark 通道选项。
     *
     * @return websocket 传输使用的通道选项。
     */
    private LarkChannelOptions buildOptions() {
        PolicyConfig policyConfig = new PolicyConfig();
        policyConfig.setRequireMention(properties.getRequireMention());
        policyConfig.setRespondToMentionAll(properties.getRespondToMentionAll());
        policyConfig.setGroupAllowlist(properties.getGroupAllowList());

        return LarkChannelOptions.newBuilder(properties.getAppId(), properties.getAppSecret())
                .domain(properties.getDomain())
                .transport("websocket")
                .policy(policyConfig)
                .build();
    }

    /**
     * 在通道上注册消息、错误和重连事件处理器。
     */
    private void registerHandlers() {
        channel.on("message", message -> dispatcher.handleMessage((NormalizedMessage) message));
        channel.on("error", new ChannelEventHandler<ChannelErrorEvent>() {
            @Override
            public void handle(ChannelErrorEvent event) {
                log.error(
                        "Lark channel error. eventName={}, event={}, error={}",
                        event.getEventName(),
                        safeToJson(event.getEvent()),
                        event.getError() == null ? null : event.getError().getMessage(),
                        event.getError()
                );
            }
        });
        channel.on("reconnecting", new ChannelEventHandler<Object>() {
            @Override
            public void handle(Object ignored) {
                log.warn("Lark websocket reconnecting.");
            }
        });
        channel.on("reconnected", new ChannelEventHandler<Object>() {
            @Override
            public void handle(Object ignored) {
                log.info("Lark websocket reconnected.");
            }
        });
    }

    /**
     * 安全序列化事件对象，用于诊断日志。
     *
     * @param event SDK 返回的原始事件对象。
     * @return 序列化成功时返回 JSON 字符串，否则返回 {@link String#valueOf(Object)}。
     */
    private String safeToJson(Object event) {
        if (event == null) {
            return null;
        }
        try {
            return JsonUtils.toJsonString(event);
        } catch (RuntimeException ignored) {
            return String.valueOf(event);
        }
    }
}
