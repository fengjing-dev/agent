package com.lark.agent.module.service;

import com.lark.oapi.core.utils.Strings;
import org.springframework.stereotype.Service;

/**
 * 识别和处理会话级文本指令。
 */
@Service
public class ConversationCommandService {

    /**
     * 判断文本是否是清除上下文指令。
     *
     * @param command 用户输入的纯文本指令。
     * @return 命中清除指令时返回 true。
     */
    public boolean isClearMemoryCommand(String command) {
        if (Strings.isEmpty(command)) {
            return false;
        }
        String normalizedCommand = command.trim();
        return "/clear".equalsIgnoreCase(normalizedCommand)
                || "clear".equalsIgnoreCase(normalizedCommand)
                || "清空上下文".equals(normalizedCommand)
                || "清除上下文".equals(normalizedCommand)
                || "忘记前文".equals(normalizedCommand)
                || "重置上下文".equals(normalizedCommand);
    }
}
