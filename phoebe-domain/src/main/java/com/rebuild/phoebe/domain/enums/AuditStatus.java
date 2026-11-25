package com.rebuild.phoebe.domain.enums;

/** 审核状态。中间页有 DRAFT；问答/创意从 PENDING 开始。枚举值 [U] */
public enum AuditStatus {
    DRAFT, PENDING, APPROVED, REJECTED, CANCELED
}
