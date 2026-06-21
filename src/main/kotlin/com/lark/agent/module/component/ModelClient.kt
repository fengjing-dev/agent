package com.lark.agent.module.component

import java.util.function.Consumer

/**
 * 统一模型调用入口。
 */
interface ModelClient {

    /**
     * 调用模型一次并返回完整响应文本。
     *
     * @param message 完整模型上下文。
     * @param routeText 当前用户问题，仅用于提示词路由。
     * @return 生成的响应文本。
     */
    fun call(message: String, routeText: String = message): String?

    /**
     * 以流式方式调用模型，并把文本分片转发给调用方。
     *
     * @param message 完整模型上下文。
     * @param routeText 当前用户问题，仅用于提示词路由。
     * @param chunkConsumer 接收生成文本分片的回调。
     * @return 由所有分片拼成的完整生成文本。
     */
    fun stream(message: String, routeText: String = message, chunkConsumer: Consumer<String>): String?
}
