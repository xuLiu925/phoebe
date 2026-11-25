package com.rebuild.phoebe.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.domain.dto.PlanRequest;
import com.rebuild.phoebe.domain.entity.BudgetLimitLog;
import com.rebuild.phoebe.domain.entity.Plan;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.enums.Channel;
import com.rebuild.phoebe.domain.enums.Device;
import com.rebuild.phoebe.domain.mapper.BudgetLimitLogMapper;
import com.rebuild.phoebe.domain.mapper.PlanMapper;
import com.rebuild.phoebe.domain.mapper.QuestionAnswerMapper;
import com.rebuild.phoebe.domain.service.PlanService;
import com.rebuild.phoebe.domain.service.ReportService;
import com.rebuild.phoebe.domain.service.StatService;
import com.rebuild.phoebe.domain.service.TargetingRules;
import com.rebuild.phoebe.simulator.billing.BillingService;
import com.rebuild.phoebe.simulator.search.SearchSimulator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 投放时段、IP 排除、报表流量类型/分地域、预算达限记录。 */
@SpringBootTest
@Transactional
class TargetingAndReportTest extends TestSupport {
    @Autowired PlanService planService;
    @Autowired PlanMapper planMapper;
    @Autowired QuestionAnswerMapper qaMapper;
    @Autowired SearchSimulator search;
    @Autowired BillingService billing;
    @Autowired StatService statService;
    @Autowired ReportService reportService;
    @Autowired BudgetLimitLogMapper budgetLimitMapper;

    /** 只在工作日 9:00-18:00 投放 */
    private static String workdayDaytime() {
        StringBuilder sb = new StringBuilder();
        for (int d = 0; d < 7; d++) {
            for (int h = 0; h < 24; h++) {
                sb.append(d < 5 && h >= 9 && h < 18 ? '1' : '0');
            }
        }
        return sb.toString();
    }

    private PlanRequest req(Plan p) {
        PlanRequest r = new PlanRequest();
        r.setName(p.getName());
        r.setBudget(p.getBudget());
        r.setMobileBidRatio(p.getMobileBidRatio());
        r.setRegions(List.of());
        return r;
    }

    private SearchSimulator.SearchResult run(LocalDateTime time, String ip) {
        SearchSimulator.SearchRequest r = new SearchSimulator.SearchRequest();
        r.setQuery("眼霜什么牌子好");
        r.setDevice(Device.MOBILE);
        r.setChannel(Channel.ZHIDAO);
        r.setTime(time);
        r.setIp(ip);
        r.setRegion("北京");
        return search.search(r);
    }

    @Test
    void scheduleValidationAndMatching() {
        assertThat(TargetingRules.normalizeSchedule("1".repeat(168))).isNull(); // 全选 = 不限
        assertThatThrownBy(() -> TargetingRules.normalizeSchedule("0".repeat(168))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> TargetingRules.normalizeSchedule("101")).hasMessageContaining("168");
        Plan p = new Plan();
        p.setSchedule(workdayDaytime());
        assertThat(TargetingRules.inSchedule(p, LocalDateTime.of(2026, 11, 23, 10, 0))).isTrue();  // 周一 10 点
        assertThat(TargetingRules.inSchedule(p, LocalDateTime.of(2026, 11, 23, 20, 0))).isFalse(); // 周一 20 点
        assertThat(TargetingRules.inSchedule(p, LocalDateTime.of(2026, 11, 29, 10, 0))).isFalse(); // 周日
    }

    @Test
    void ipValidationAndMatching() {
        assertThat(TargetingRules.normalizeIps(List.of("1.2.3.4", "10.0.*.*", " 1.2.3.4 "), 100)).isEqualTo("1.2.3.4,10.0.*.*");
        assertThatThrownBy(() -> TargetingRules.normalizeIps(List.of("1.2.3"), 100)).hasMessageContaining("IP 格式错误");
        assertThatThrownBy(() -> TargetingRules.normalizeIps(List.of("*.1.2.3"), 100)).hasMessageContaining("IP 格式错误");
        assertThatThrownBy(() -> TargetingRules.normalizeIps(List.of("1.*.2.3"), 100)).hasMessageContaining("IP 格式错误");
        assertThatThrownBy(() -> TargetingRules.normalizeIps(List.of("1.2.3.256"), 100)).hasMessageContaining("IP 格式错误");
        assertThatThrownBy(() -> TargetingRules.normalizeIps(List.of("1.1.1.1", "2.2.2.2"), 1)).hasMessageContaining("最多排除");
        Plan p = new Plan();
        p.setExcludedIps("1.2.3.4,10.0.*.*");
        assertThat(TargetingRules.ipExcluded(p, "1.2.3.4")).isTrue();
        assertThat(TargetingRules.ipExcluded(p, "10.0.88.9")).isTrue();
        assertThat(TargetingRules.ipExcluded(p, "10.1.0.1")).isFalse();
        assertThat(TargetingRules.ipExcluded(p, null)).isFalse();
    }

    @Test
    void scheduleAndIpAffectAuction() {
        asAdvertiser(1);
        Plan p = planMapper.selectById(1L);
        PlanRequest r = req(p);
        r.setSchedule(workdayDaytime());
        r.setExcludedIps(List.of("8.8.8.*"));
        Plan saved = planService.update(1L, r);
        assertThat(saved.getSchedule()).hasSize(168);
        assertThat(saved.getExcludedIps()).isEqualTo("8.8.8.*");

        LocalDateTime mondayNoon = LocalDateTime.of(2026, 11, 23, 12, 0);
        LocalDateTime mondayNight = LocalDateTime.of(2026, 11, 23, 22, 0);
        // 时段内、IP 不在排除列表：青禾问答1 参与竞价（移动端 1.5×1.2=1.8 胜出）
        assertThat(run(mondayNoon, "1.1.1.1").getAuction()).anyMatch(c -> c.getQaId() == 1L);
        // 时段外：被排除
        assertThat(run(mondayNight, "1.1.1.1").getExcluded()).anyMatch(e -> e.getQaId() == 1L && e.getReasons().contains("不在投放时段"));
        // IP 段命中：被排除
        assertThat(run(mondayNoon, "8.8.8.8").getExcluded()).anyMatch(e -> e.getQaId() == 1L && e.getReasons().contains("网民 IP 已被计划排除"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportFlowTypeAndRegionSplit() {
        QuestionAnswer qa = qaMapper.selectById(1L);
        LocalDate d = LocalDate.now();
        statService.addQa(qa, Device.PC, Channel.ZHIDAO, "北京", d, 100, 10, new BigDecimal("10"));
        statService.addQa(qa, Device.PC, Channel.SEARCH, "上海", d, 50, 5, new BigDecimal("5"));
        statService.addQa(qa, Device.MOBILE, Channel.BAOBAO_APP, "北京", d, 30, 3, new BigDecimal("3"));

        Map<String, Object> zd = reportService.report(1L, ReportService.Level.QA, d, d, null, null, null, ReportService.Flow.ZHIDAO, false);
        Map<String, Object> row = ((List<Map<String, Object>>) zd.get("rows")).stream().filter(x -> x.get("targetId").equals(1L)).findFirst().orElseThrow();
        assertThat(row.get("impressions")).isEqualTo(150L); // 知道 = 知道 + 网页搜索

        Map<String, Object> app = reportService.report(1L, ReportService.Level.QA, d, d, null, null, null, ReportService.Flow.BAOBAO_APP, false);
        assertThat(((Map<String, Object>) app.get("summary")).get("impressions")).isEqualTo(30L);

        Map<String, Object> split = reportService.report(1L, ReportService.Level.QA, d, d, null, null, null, ReportService.Flow.ALL, true);
        List<Map<String, Object>> rows = ((List<Map<String, Object>>) split.get("rows")).stream().filter(x -> x.get("targetId").equals(1L)).toList();
        assertThat(rows).extracting(x -> x.get("region")).containsExactlyInAnyOrder("北京", "上海");
        assertThat(rows.stream().filter(x -> "北京".equals(x.get("region"))).findFirst().orElseThrow().get("impressions")).isEqualTo(130L);
    }

    @Test
    void budgetLimitRecordedOncePerDay() {
        Plan p = planMapper.selectById(1L);
        p.setBudget(new BigDecimal("50"));
        planMapper.updateById(p);
        QuestionAnswer qa = qaMapper.selectById(1L);
        statService.addQa(qa, Device.PC, LocalDate.now(), 0, 0, new BigDecimal("49.90"));

        SearchSimulator.SearchResult r = run(null, null);
        SearchSimulator.Item ad = r.getItems().stream().filter(i -> "AD".equals(i.getType()) && i.getQaId() == 1L).findFirst().orElseThrow();
        billing.clickEntry(ad.getImpressionId());

        List<BudgetLimitLog> logs = budgetLimitMapper.selectList(new LambdaQueryWrapper<BudgetLimitLog>().eq(BudgetLimitLog::getPlanId, 1L));
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getBudget()).isEqualByComparingTo("50");
        assertThat(logs.get(0).getCost()).isGreaterThanOrEqualTo(new BigDecimal("50"));
        // 预算已用完 → 后续检索不再展现，也不会重复记录
        assertThat(run(null, null).getExcluded()).anyMatch(e -> e.getQaId() == 1L && e.getReasons().contains("今日预算已用完"));
    }
}
