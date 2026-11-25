package com.rebuild.phoebe.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rebuild.phoebe.domain.enums.RunStatus;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 推广计划。字段 [P:A]：名称、预算(可不限)、移动出价比例、推广地域、移动推广电话。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pb_plan")
public class Plan extends BaseEntity {
    private Long accountId;
    private String name;
    /** 日预算；null = 不限预算。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal budget;
    private BigDecimal mobileBidRatio;
    /** 逗号分隔省份；空 = 全部地域。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String regions;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String mobilePhone;
    /** 投放时段：168 位 0/1（周一 0 点起每小时一位）；null = 不限时间 [P:A 截图]。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String schedule;
    /** IP 排除：逗号分隔，支持精确 IP 与 x.x.x.* 段；null = 不限 IP [P:A 截图]。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String excludedIps;
    private RunStatus status;
    @TableLogic
    private Integer deleted;
}
