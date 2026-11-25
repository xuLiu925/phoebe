package com.rebuild.phoebe.simulator;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** 模拟投放参数（全部为新建实现 [U]/[I]）。 */
@Data
@ConfigurationProperties(prefix = "phoebe.simulator")
public class SimulatorProperties {
    /** 无竞争者时的底价 / 最低计费价。 */
    private BigDecimal minPrice = new BigDecimal("0.30");
    /** 质量度基准点击率（点击调权 [P:B]，算法 [I]）。 */
    private double baseCtr = 0.05;
    /** 平滑样本量。 */
    private int smoothing = 20;
    /** 检索页广告混排位置：初始位次为自然结果第 4 位 [P:B]。 */
    private int searchListPosition = 4;
    /** 问题页“相似问题”第 1 位 [P:B]。 */
    private int questionPagePosition = 1;
    /** 每次检索的广告位数量 [U]。CPT 命中时占第一个，CPC 顺延填充其余 [C]。 */
    private int adSlots = 2;
    /** 相似匹配阈值（二元组 Jaccard）。 */
    private double similarThreshold = 0.4;
}
