package com.lark.agent.module.service;

import com.lark.agent.module.properties.AgentProperties;
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
 * 负责管理 Lark 长连接生命周期，并把消息事件交给分发器处理。
 *
 * @Author: Fatina 2026/06/17
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
     * @param properties Lark 应用配置
     */
    public LarkChannelManager(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * 建立 Lark 长连接并注册事件处理器。
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
     * 应用退出时关闭 Lark 长连接。
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
     * @return Lark 长连接配置
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
     * 注册消息、错误和重连事件。
     */
    private void registerHandlers() {
        channel.on("message", message -> dispatcher.handleMessage((NormalizedMessage) message));
        channel.on("error", new ChannelEventHandler<ChannelErrorEvent>() {
            @Override
            public void handle(ChannelErrorEvent event) {
                log.error("Lark channel error: {}", event);
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
}
