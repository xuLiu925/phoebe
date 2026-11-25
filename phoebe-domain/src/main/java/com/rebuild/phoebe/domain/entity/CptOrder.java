package com.rebuild.phoebe.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rebuild.phoebe.common.Evidence;
import com.rebuild.phoebe.common.EvidenceLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CPT 包词订单。
 * <p>
 * [C] 产品同时有 CPC 与 CPT 两种售卖方式，同一个词 CPT 广告的展示优先级高于 CPC；
 * [C] 按天包词、下单预付；同一词同一天只卖给一家；CPT 占第一个广告位、CPC 顺延；CPT 点击不扣费。
 * 表结构、定价规则 [U]。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pb_cpt_order")
@Evidence(level = EvidenceLevel.CONFIRMED, note = "CPT 存在且同词优先于 CPC；售卖细节为已确认的产品规则；表结构为新建实现")
public class CptOrder extends BaseEntity {
    public static final String ACTIVE = "ACTIVE";
    public static final String CANCELED = "CANCELED";

    private Long accountId;
    private Long planId;
    private Long unitId;
    private Long qaId;
    private String keyword;
    private String keywordNorm;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal dailyPrice;
    private BigDecimal totalPrice;
    private BigDecimal refundAmount;
    private String status;
    private LocalDateTime canceledAt;

    /** 展示用状态：待开始 / 投放中 / 已结束 / 已取消（非表字段）。 */
    @TableField(exist = false)
    private String phase;
    @TableField(exist = false)
    private String question;
    @TableField(exist = false)
    private Long impressions;
    @TableField(exist = false)
    private Long clicks;
}
