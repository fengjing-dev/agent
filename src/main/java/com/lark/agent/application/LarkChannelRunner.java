package com.lark.agent.application;

import com.lark.agent.module.service.LarkChannelManager;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动 Lark 长连接并消费消息事件的运行器。
 * @Author: Fatina 2026/06/17
 */
@Component
public class LarkChannelRunner implements ApplicationRunner {

    private final LarkChannelManager larkChannelManager;

    public LarkChannelRunner(LarkChannelManager larkChannelManager) {
        this.larkChannelManager = larkChannelManager;
    }

    /**
     * 启动后建立 Lark 长连接并注册事件处理器。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args){
        larkChannelManager.connection();
    }
}
