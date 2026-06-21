package com.lark.agent.application;

import com.lark.agent.module.service.LarkChannelManager;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Spring 应用上下文就绪后启动 Lark 长连接。
 */
@Component
public class LarkChannelRunner implements ApplicationRunner {

    private final LarkChannelManager larkChannelManager;

    /**
     * 使用 Lark 通道管理器创建启动器。
     *
     * @param larkChannelManager 负责 Lark 长连接的管理器。
     */
    public LarkChannelRunner(LarkChannelManager larkChannelManager) {
        this.larkChannelManager = larkChannelManager;
    }

    /**
     * 应用启动完成后打开 Lark 通道。
     *
     * @param args Spring Boot 提供的启动参数。
     */
    @Override
    public void run(ApplicationArguments args) {
        larkChannelManager.connection();
    }
}
