package com.lark.agent.module.component

/**
 * 模型侧或本地冷却窗口触发的限流异常。
 */
class ModelRateLimitException : RuntimeException {

    /**
     * 创建限流异常。
     *
     * @param message 异常说明。
     */
    constructor(message: String) : super(message)

    /**
     * 创建带原始异常的限流异常。
     *
     * @param message 异常说明。
     * @param cause 原始异常。
     */
    constructor(message: String, cause: Throwable) : super(message, cause)
}
