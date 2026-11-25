package com.rebuild.phoebe.simulator.cpt;

import com.rebuild.phoebe.common.Evidence;
import com.rebuild.phoebe.common.EvidenceLevel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * CPT 包词参数。定价与期限规则均 [U]（原产品未知），全部可配置。
 * 日单价 = clamp(基础价 + 词相关问题月检索量/1000 × 每千 PV 加价, 最低价, 最高价)，取整。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "phoebe.cpt")
@Evidence(level = EvidenceLevel.UNKNOWN, note = "定价公式、最长天数、可否当天开始均为示例")
public class CptProperties {
    private BigDecimal baseDailyPrice = new BigDecimal("100");
    private BigDecimal pricePerThousandPv = new BigDecimal("1.0");
    private BigDecimal minDailyPrice = new BigDecimal("50");
    private BigDecimal maxDailyPrice = new BigDecimal("3000");
    /** 单个订单最长天数。 */
    private int maxDays = 90;
    /** 最早可提前多少天下单（排期窗口）。 */
    private int maxAdvanceDays = 180;
    /** 是否允许当天开始投放。 */
    private boolean allowToday = true;
    /** 包断词最少字符数（归一化后）。 */
    private int minKeywordLength = 2;
}
