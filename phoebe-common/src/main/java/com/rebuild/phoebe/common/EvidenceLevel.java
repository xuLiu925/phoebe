package com.rebuild.phoebe.common;

/** 证据等级。 */
public enum EvidenceLevel {
    /** 已确认：已知的项目事实。 */
    CONFIRMED,
    /** 公开资料支持：百度公开的知识营销产品资料写明（证明产品有此规则，不证明 Phoebe 代码如此实现）。 */
    PUBLIC,
    /** 合理推测。 */
    INFERRED,
    /** 完全未知，重建时自行设计。 */
    UNKNOWN
}
