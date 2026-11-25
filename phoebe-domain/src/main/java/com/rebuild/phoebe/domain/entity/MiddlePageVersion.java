package com.rebuild.phoebe.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.rebuild.phoebe.domain.enums.AuditStatus;
import com.rebuild.phoebe.domain.enums.PcStyle;
import com.rebuild.phoebe.domain.middlepage.PageContent;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 中间页版本。修改已启用中间页时，新版审核通过前展示旧版 [P:A]。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pb_middle_page_version", autoResultMap = true)
public class MiddlePageVersion extends BaseEntity {
    private Long pageId;
    private Integer versionNo;
    private PcStyle pcStyle;
    @TableField(typeHandler = JacksonTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    private PageContent pcContent;
    @TableField(typeHandler = JacksonTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    private PageContent mobileContent;
    private AuditStatus auditStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String rejectReason;
    private LocalDateTime submittedAt;
    private LocalDateTime auditedAt;
}
