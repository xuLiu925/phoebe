package com.rebuild.phoebe.domain.config;

import com.rebuild.phoebe.common.Evidence;
import com.rebuild.phoebe.common.EvidenceLevel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 全部可配置业务规则（application.yml 前缀 phoebe.rules）。
 * 历史产品规则不视为永久不变，统一配置化，便于与原项目校准。
 */
@Data
@ConfigurationProperties(prefix = "phoebe.rules")
public class PhoebeRules {

    @Evidence(level = EvidenceLevel.PUBLIC, source = "B", note = "一个账户下最多有100个计划")
    private int maxPlansPerAccount = 100;

    @Evidence(level = EvidenceLevel.PUBLIC, source = "B", note = "一个计划下最多有100个单元")
    private int maxUnitsPerPlan = 100;

    @Evidence(level = EvidenceLevel.PUBLIC, source = "B", note = "每个单元下最多有100条问答")
    private int maxQasPerUnit = 100;

    @Evidence(level = EvidenceLevel.PUBLIC, source = "A,B", note = "一个计划下最多2套中间页")
    private int maxMiddlePagesPerPlan = 2;

    @Evidence(level = EvidenceLevel.PUBLIC, source = "A,B", note = "同一时间只能启用1套")
    private int maxEnabledMiddlePagesPerPlan = 1;

    @Evidence(level = EvidenceLevel.PUBLIC, source = "A", note = "问题 10~98 字符（汉字计2）")
    private int questionMinChars = 10;
    private int questionMaxChars = 98;

    @Evidence(level = EvidenceLevel.PUBLIC, source = "A", note = "答案 160~19998 字符（汉字计2）")
    private int answerMinChars = 160;
    private int answerMaxChars = 19998;

    @Evidence(level = EvidenceLevel.PUBLIC, source = "A", note = "不能含2个及以上连续标点")
    private boolean forbidConsecutivePunct = true;

    @Evidence(level = EvidenceLevel.PUBLIC, source = "A,B", note = "Excel 一次最多100条，超出忽略")
    private int excelMaxRows = 100;

    @Evidence(level = EvidenceLevel.UNKNOWN, note = "违禁词表为示例，非百度词表")
    private List<String> bannedWords = new ArrayList<>(List.of("最好的", "第一品牌", "国家级", "赌博", "代开发票"));

    @Evidence(level = EvidenceLevel.PUBLIC, source = "A,C", note = "医疗服务、成人用品、彩票暂不开放")
    private List<String> bannedIndustries = new ArrayList<>(List.of("医疗服务", "成人用品", "彩票"));

    @Evidence(level = EvidenceLevel.UNKNOWN, note = "出价上下限为示例值")
    private BigDecimal bidMin = new BigDecimal("0.30");
    private BigDecimal bidMax = new BigDecimal("999.99");

    @Evidence(level = EvidenceLevel.UNKNOWN, note = "最低日预算为示例值；预算可不限 [P:A]")
    private BigDecimal budgetMin = new BigDecimal("50");

    @Evidence(level = EvidenceLevel.PUBLIC, source = "A", note = "截图提示：请输入0.10~10.00之间，保留2位小数的数值")
    private BigDecimal mobileRatioMin = new BigDecimal("0.10");
    private BigDecimal mobileRatioMax = new BigDecimal("10.00");

    @Evidence(level = EvidenceLevel.UNKNOWN)
    private int maxSubscribeWordsPerUnit = 50;

    @Evidence(level = EvidenceLevel.UNKNOWN, note = "每计划 IP 排除上限为示例值")
    private int maxExcludedIpsPerPlan = 100;

    @Evidence(level = EvidenceLevel.UNKNOWN, note = "高级创意标题/描述长度（字符，汉字计2）")
    private int creativeTitleMaxChars = 40;
    private int creativeDescMaxChars = 160;

    @Evidence(level = EvidenceLevel.PUBLIC, source = "B", note = "中间页审核存在“主域一致性”策略；此处仅作预审提示")
    private boolean domainConsistencyCheck = true;
}
