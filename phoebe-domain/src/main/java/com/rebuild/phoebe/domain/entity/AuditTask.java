package com.rebuild.phoebe.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.rebuild.phoebe.domain.enums.AuditStatus;
import com.rebuild.phoebe.domain.enums.AuditTargetType;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 审核任务（快照式）。审核存在 [P:B]，结构 [U]。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pb_audit_task")
public class AuditTask extends BaseEntity {
    private Long accountId;
    private AuditTargetType targetType;
    /** QA/创意 id；中间页为版本 id。 */
    private Long targetId;
    private Integer targetVersion;
    private String title;
    private String snapshot;
    private AuditStatus status;
    private String reason;
    private String preCheckResult;
    private Long auditorId;
    private LocalDateTime auditedAt;
}
