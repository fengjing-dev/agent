package com.lark.agent;

import com.lark.agent.module.properties.AgentProperties;
import com.lark.agent.module.properties.GeminiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Spring Boot entry point for the local Lark agent verification application.
 */
@SpringBootApplication
@EnableConfigurationProperties({AgentProperties.class, GeminiProperties.class})
public class LarkAgentApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments passed to Spring Boot.
     */
    public static void main(String[] args) {
        SpringApplication.run(LarkAgentApplication.class, args);
    }
}
