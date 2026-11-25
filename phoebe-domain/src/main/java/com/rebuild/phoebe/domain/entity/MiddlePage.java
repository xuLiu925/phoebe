package com.rebuild.phoebe.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 中间页：每计划≤2套，同一时间启用1套 [P:A,B]。版本指针设计 [I]。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pb_middle_page")
public class MiddlePage extends BaseEntity {
    private Long accountId;
    private Long planId;
    private String name;
    private Boolean enabled;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long onlineVersionId;
    private Long latestVersionId;
    @TableLogic
    private Integer deleted;
}
