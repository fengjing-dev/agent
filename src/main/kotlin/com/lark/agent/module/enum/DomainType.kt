package com.lark.agent.module.enum

/**
 * Prompt routing domain used to select a specialized prompt template.
 */
enum class DomainType {

    /** Payment, wallet, settlement, reconciliation, and transaction questions. */
    PAYMENT,

    /** Architecture, design pattern, refactoring, and scalability questions. */
    ARCH,

    /** Code, exception, log, database, and engineering troubleshooting questions. */
    TECH,

    /** User interface, interaction, layout, and visual design questions. */
    UI,

    /** Internal HR, finance, reimbursement, reporting, and process questions. */
    HR_FINANCE,

    /** General assistant fallback domain. */
    GENERAL;
}
