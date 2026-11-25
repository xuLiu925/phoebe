package com.rebuild.phoebe.domain.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rebuild.phoebe.domain.enums.RunStatus;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 推广单元。字段 [P:A]：名称、出价、订阅词。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pb_unit")
public class Unit extends BaseEntity {
    private Long accountId;
    private Long planId;
    private String name;
    /** 单元出价，即问答默认出价 [P:A]。 */
    private BigDecimal bid;
    private RunStatus status;
    @TableLogic
    private Integer deleted;
}
