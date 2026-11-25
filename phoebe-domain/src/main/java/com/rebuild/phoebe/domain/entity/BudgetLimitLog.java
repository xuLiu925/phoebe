package com.rebuild.phoebe.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 预算达限记录 [P:A 截图]：计划当日消费达到日预算时记录一条。 */
@Data
@TableName("pb_budget_limit_log")
public class BudgetLimitLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long accountId;
    private Long planId;
    private String planName;
    private BigDecimal budget;
    private BigDecimal cost;
    private LocalDate limitDate;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
