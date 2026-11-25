package com.rebuild.phoebe.domain.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 企业知识 [P:A]。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pb_enterprise_knowledge")
public class EnterpriseKnowledge extends BaseEntity {
    private Long accountId;
    private String title;
    private String content;
    @TableLogic
    private Integer deleted;
}
