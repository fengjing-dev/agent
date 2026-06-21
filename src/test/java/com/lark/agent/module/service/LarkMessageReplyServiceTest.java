package com.lark.agent.module.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LarkMessageReplyServiceTest {

    @Test
    void renderDisplayContentShowsAnalysisAsGrayBeforeFinalAnswer() {
        LarkMessageReplyService service = new LarkMessageReplyService();

        String content = service.renderDisplayContent("【分析摘要】\n- 先判断上下文\n- 再给结论");

        assertTrue(content.contains("<font color='grey'>- 先判断上下文</font>"));
        assertTrue(content.contains("<font color='grey'>- 再给结论</font>"));
    }

    @Test
    void renderDisplayContentShowsOnlyFinalAnswerAfterFinalMarker() {
        LarkMessageReplyService service = new LarkMessageReplyService();

        String content = service.renderDisplayContent("【分析摘要】\n- 临时分析\n【最终回答】\n正式内容");

        assertEquals("正式内容", content);
        assertFalse(content.contains("临时分析"));
    }

    @Test
    void extractFinalAnswerKeepsOnlyFinalAnswerForMemory() {
        LarkMessageReplyService service = new LarkMessageReplyService();

        String content = service.extractFinalAnswer("【分析摘要】\n- 临时分析\n【最终回答】\n正式内容");

        assertEquals("正式内容", content);
    }
}
