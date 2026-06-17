package com.lark.agent.module.service;

import com.lark.agent.module.component.GeminiClient;
import com.lark.agent.module.properties.AgentProperties;
import com.lark.agent.module.utils.JsonUtils;
import com.lark.oapi.channel.ChannelEventHandler;
import com.lark.oapi.channel.LarkChannel;
import com.lark.oapi.channel.LarkChannelFactory;
import com.lark.oapi.channel.config.LarkChannelOptions;
import com.lark.oapi.channel.config.LarkChannelOptions.PolicyConfig;
import com.lark.oapi.channel.model.ChannelErrorEvent;
import com.lark.oapi.channel.model.NormalizedMessage;
import com.lark.oapi.channel.model.SendInput;
import com.lark.oapi.channel.model.SendOptions;
import com.lark.oapi.core.utils.Strings;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutionException;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动 Lark 长连接并消费消息事件的运行器。
 * @Author: Fatina 2026/06/17
 */
@Component
public class LarkChannelRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LarkChannelRunner.class);

    private final AgentProperties properties;

    private LarkChannel channel;

    @Resource
    private GeminiClient geminiClient;

    /**
     * @param properties Lark 智能体配置
     */
    public LarkChannelRunner(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * 启动后建立 Lark 长连接并注册事件处理器。
     *
     * @param args 启动参数
     * @throws Exception 建立连接失败时抛出异常
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        validateProperties();
        // 本地验证阶段固定使用长连接模式，先确认 Lark 入站事件链路可用。
        this.channel = LarkChannelFactory.createLarkChannel(buildOptions());
        registerHandlers();
        this.channel.connect().get();
        log.info("Lark websocket connected. domain={}", properties.getDomain());
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

    private void validateProperties() {
        String appId = trimToNull(properties.getAppId());
        String appSecret = trimToNull(properties.getAppSecret());
        String domain = trimToNull(properties.getDomain());

        // 启动前直接拦截空值和占位值，避免走到 SDK 内部才得到模糊报错。
        if (appId == null || appSecret == null) {
            throw new IllegalStateException(buildMissingCredentialMessage());
        }
        if (isPlaceholder(appId) || isPlaceholder(appSecret)) {
            throw new IllegalStateException(buildPlaceholderMessage());
        }
        if (domain == null) {
            throw new IllegalStateException("Missing lark.agent.domain.");
        }
    }

    private String trimToNull(String value) {
        if (Strings.isEmpty(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isPlaceholder(String value) {
        String normalized = value.trim().toLowerCase();
        return normalized.contains("replace-with-your-app-id")
                || normalized.contains("replace-with-your-app-secret");
    }

    private String buildMissingCredentialMessage() {
        boolean oldEnvPresent = trimToNull(System.getenv("LARK_APP_ID")) != null
                || trimToNull(System.getenv("LARK_APP_SECRET")) != null;
        StringBuilder builder = new StringBuilder(
                "Missing lark.agent.app-id or lark.agent.app-secret. " +
                        "Use application.yml or Spring environment keys LARK_AGENT_APP_ID and LARK_AGENT_APP_SECRET.");
        if (oldEnvPresent) {
            builder.append(" Detected old env names LARK_APP_ID/LARK_APP_SECRET. ")
                    .append("Spring Boot will not bind them to lark.agent.*.");
        }
        return builder.toString();
    }

    private String buildPlaceholderMessage() {
        return "Invalid lark.agent.app-id or lark.agent.app-secret. " +
                "application.yml still contains placeholder values. " +
                "Replace them with the real App ID and App Secret, " +
                "or provide LARK_AGENT_APP_ID and LARK_AGENT_APP_SECRET.";
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

    private void registerHandlers() {
        channel.on("message", new ChannelEventHandler<NormalizedMessage>() {
            @Override
            public void handle(NormalizedMessage message) {
                // 当前只验证“收到消息 -> 回复消息”闭环，后续接大模型也从这里切入。
                handleMessage(message);
            }
        });
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

    private void handleMessage(NormalizedMessage message) {
        log.info("Received message: chatId={}, messageId={}, content={}",
                message.getChatId(), message.getMessageId(), JsonUtils.toJsonString(message));
        try {
            // 先回复固定文案，确认机器人在群里被 @ 后能够正常出声。
            String reply = geminiClient.call(message.getContent());
            channel.send(
                    message.getChatId(),
                    SendInput.text(reply),
                    SendOptions.newBuilder().replyTo(message.getMessageId()).build()
            );
        } catch (Exception e) {
            log.error("Failed to reply message: chatId={}, messageId={}",
                    message.getChatId(), message.getMessageId(), e);
        }
    }
}
