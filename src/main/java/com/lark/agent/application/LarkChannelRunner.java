package com.lark.agent.application;

import com.lark.agent.module.service.LarkChannelManager;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Starts the Lark long connection after the Spring application context is ready.
 */
@Component
public class LarkChannelRunner implements ApplicationRunner {

    private final LarkChannelManager larkChannelManager;

    /**
     * Creates a runner with the Lark channel manager.
     *
     * @param larkChannelManager manager responsible for the Lark long connection.
     */
    public LarkChannelRunner(LarkChannelManager larkChannelManager) {
        this.larkChannelManager = larkChannelManager;
    }

    /**
     * Opens the Lark channel after application startup.
     *
     * @param args startup arguments provided by Spring Boot.
     */
    @Override
    public void run(ApplicationArguments args) {
        larkChannelManager.connection();
    }
}
