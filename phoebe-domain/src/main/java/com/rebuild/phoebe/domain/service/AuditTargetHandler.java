package com.rebuild.phoebe.domain.service;

import com.rebuild.phoebe.domain.entity.AuditTask;
import com.rebuild.phoebe.domain.enums.AuditTargetType;

/** 审核结果回写。各物料服务实现此接口，日后接入真实审核系统时只需替换 AuditService 的触发方式。 */
public interface AuditTargetHandler {
    AuditTargetType type();

    void onApproved(AuditTask task);

    void onRejected(AuditTask task, String reason);
}
