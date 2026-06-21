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
 * Manages the Lark long-connection lifecycle and registers channel event handlers.
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
     * Creates a channel manager with Lark agent configuration.
     *
     * @param properties Lark agent configuration properties.
     */
    public LarkChannelManager(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates the Lark channel, registers handlers, and opens the websocket connection.
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
     * Disconnects the Lark channel when the Spring application is shutting down.
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
     * Builds Lark channel options from application configuration.
     *
     * @return channel options for websocket transport.
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
     * Registers message, error, and reconnect event handlers on the channel.
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
     * Safely serializes an event object for diagnostic logging.
     *
     * @param event raw event object from the SDK.
     * @return JSON string when serialization succeeds, otherwise {@link String#valueOf(Object)}.
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
