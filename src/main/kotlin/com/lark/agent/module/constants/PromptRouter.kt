package com.lark.agent.module.constants

import com.lark.agent.module.enum.DomainType

object PromptRouter {

    private val paymentKeywords = listOf(
        "支付", "wallet", "card", "卡", "钱包", "渠道", "提现", "充值", "风控", "清结算", "对账", "幂等", "资金", "交易"
    )

    private val archKeywords = listOf(
        "架构", "设计模式", "工厂模式", "策略模式", "解耦", "重构", "扩展性", "可维护性", "高并发", "事件驱动", "分层"
    )

    private val techKeywords = listOf(
        "exception", "报错", "异常", "堆栈", "sql", "java", "spring", "接口", "代码", "bug", "日志", "trace", "redis", "mq", "数据库", "前端", "后端", "kotlin", "typescript"
    )

    private val uiKeywords = listOf(
        "ui", "页面", "交互", "布局", "按钮", "弹窗", "表单", "视觉", "设计稿", "用户体验"
    )

    private val hrFinanceKeywords = listOf(
        "人事", "招聘", "绩效", "制度", "考勤", "汇报", "财务", "报销", "报表", "预算", "费用", "流程"
    )

    fun detectDomain(text: String): DomainType {
        val content = text.lowercase()

        return when {
            paymentKeywords.any { content.contains(it.lowercase()) } -> DomainType.PAYMENT
            archKeywords.any { content.contains(it.lowercase()) } -> DomainType.ARCH
            techKeywords.any { content.contains(it.lowercase()) } -> DomainType.TECH
            uiKeywords.any { content.contains(it.lowercase()) } -> DomainType.UI
            hrFinanceKeywords.any { content.contains(it.lowercase()) } -> DomainType.HR_FINANCE
            else -> DomainType.GENERAL
        }
    }

    fun resolvePrompt(domainType: DomainType): String {
        return when (domainType) {
            DomainType.PAYMENT -> PromptTemplates.PAYMENT
            DomainType.ARCH -> PromptTemplates.ARCH
            DomainType.TECH -> PromptTemplates.TECH
            DomainType.UI -> PromptTemplates.UI
            DomainType.HR_FINANCE -> PromptTemplates.HR_FINANCE
            DomainType.GENERAL -> PromptTemplates.GENERAL
        }
    }
}