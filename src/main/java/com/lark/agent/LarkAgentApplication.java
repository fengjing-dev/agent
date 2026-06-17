package com.lark.agent;

import com.lark.agent.module.properties.AgentProperties;
import com.lark.agent.module.properties.GeminiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Lark 智能体本地验证项目启动入口。
 * @Author: Fatina 2026/06/17
 */
@SpringBootApplication
@EnableConfigurationProperties({AgentProperties.class, GeminiProperties.class})
public class LarkAgentApplication {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(LarkAgentApplication.class, args);
    }
}
