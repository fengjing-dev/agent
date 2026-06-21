package com.lark.agent.module.enum

/**
 * 用于选择专用提示词模板的提示词路由领域。
 */
enum class DomainType {

    /** 支付、钱包、清结算、对账和交易问题。 */
    PAYMENT,

    /** 架构、设计模式、重构和扩展性问题。 */
    ARCH,

    /** 代码、异常、日志、数据库和工程排障问题。 */
    TECH,

    /** 用户界面、交互、布局和视觉设计问题。 */
    UI,

    /** 企业内部人事、财务、报销、汇报和流程问题。 */
    HR_FINANCE,

    /** 通用助手兜底领域。 */
    GENERAL;
}
