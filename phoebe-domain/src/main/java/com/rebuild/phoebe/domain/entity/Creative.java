package com.rebuild.phoebe.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.rebuild.phoebe.domain.enums.AuditStatus;
import com.rebuild.phoebe.domain.enums.RunStatus;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 高级创意：企业产品图片 + 企业产品描述，适用于华表位 [P:A]。与 Phoebe“创意模块”对应关系 [I]；与问答为可选多对多关联。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pb_creative", autoResultMap = true)
public class Creative extends BaseEntity {
    private Long accountId;
    private Long planId;
    private Long unitId;
    private String title;
    private String description;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> imageUrls;
    private String targetUrl;
    private AuditStatus auditStatus;
    private RunStatus status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String rejectReason;
    private Integer auditVersion;
    @TableLogic
    private Integer deleted;
    /** 关联问答 id（非表字段）。 */
    @TableField(exist = false)
    private List<Long> qaIds;
}
