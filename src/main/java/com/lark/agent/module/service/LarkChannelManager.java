package com.lark.agent.module.service;

import com.lark.agent.module.properties.AgentProperties;
import com.lark.oapi.channel.ChannelEventHandler;
import com.lark.oapi.channel.LarkChannel;
import com.lark.oapi.channel.LarkChannelFactory;
import com.lark.oapi.channel.config.LarkChannelOptions;
import com.lark.oapi.channel.config.LarkChannelOptions.PolicyConfig;
import com.lark.oapi.channel.model.ChannelErrorEvent;
import com.lark.oapi.channel.model.NormalizedMessage;
import com.lark.oapi.channel.model.SendInput;
import com.lark.oapi.channel.model.SendOptions;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * 启动 Lark 长连接并消费消息事件的运行器。
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

    /**
     * @param properties Lark 智能体配置
     */
    public LarkChannelManager(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * 启动后建立 Lark 长连接并注册事件处理器。
     *
     */
    public void connection() {
        // 本地验证阶段固定使用长连接模式，先确认 Lark 入站事件链路可用。
        try {
            this.channel = LarkChannelFactory.createLarkChannel(buildOptions());
            registerHandlers();
            this.channel.connect().get();
            log.info("Lark websocket connected. domain={}", properties.getDomain());
        } catch (Exception e) {
            log.error("Lark websocket connect fail. domain={}", properties.getDomain());
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
     * 注册
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

    /**
     * 回复文本消息
     * @param chatId 聊天ID
     * @param messageId 消息ID
     * @param content 文本内容
     */
    public void replyText(String chatId, String messageId, String content) {
        try {
            channel.send(
                    chatId,
                    SendInput.text(content),
                    SendOptions.newBuilder().replyTo(messageId).build()
            );
        } catch (Exception e) {
            log.error("Failed to reply message: chatId={}, messageId={}",
                    chatId, messageId, e);
        }
    }
}
