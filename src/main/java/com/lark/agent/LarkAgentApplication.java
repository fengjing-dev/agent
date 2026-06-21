package com.lark.agent;

import com.lark.agent.module.properties.AgentProperties;
import com.lark.agent.module.properties.DeepSeekProperties;
import com.lark.agent.module.properties.GeminiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 本地 Lark 机器人验证应用的 Spring Boot 启动入口。
 */
@SpringBootApplication
@EnableConfigurationProperties({AgentProperties.class, GeminiProperties.class, DeepSeekProperties.class})
public class LarkAgentApplication {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 传给 Spring Boot 的命令行参数。
     */
    public static void main(String[] args) {
        SpringApplication.run(LarkAgentApplication.class, args);
    }
}
