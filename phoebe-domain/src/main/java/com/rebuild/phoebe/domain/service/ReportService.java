package com.rebuild.phoebe.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.domain.entity.DailyStat;
import com.rebuild.phoebe.domain.entity.MiddlePage;
import com.rebuild.phoebe.domain.entity.MpDailyStat;
import com.rebuild.phoebe.domain.entity.Plan;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.entity.Unit;
import com.rebuild.phoebe.domain.enums.Device;
import com.rebuild.phoebe.domain.mapper.DailyStatMapper;
import com.rebuild.phoebe.domain.mapper.MiddlePageMapper;
import com.rebuild.phoebe.domain.mapper.MpDailyStatMapper;
import com.rebuild.phoebe.domain.mapper.PlanMapper;
import com.rebuild.phoebe.domain.mapper.QuestionAnswerMapper;
import com.rebuild.phoebe.domain.mapper.UnitMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 数据报告 [P:A,B]：
 * 问答维度：展现、点击、点击率、消费、平均点击价格；中间页（不区分模块）：展现、点击、点击率。
 * 默认最近一周（截止昨日）；可按时间、报告类型、推广设备筛选。
 */
@Service
@RequiredArgsConstructor
public class ReportService {
    public enum Level { ACCOUNT, PLAN, UNIT, QA, MIDDLE_PAGE }

    private final DailyStatMapper statMapper;
    private final MpDailyStatMapper mpStatMapper;
    private final PlanMapper planMapper;
    private final UnitMapper unitMapper;
    private final QuestionAnswerMapper qaMapper;
    private final MiddlePageMapper middlePageMapper;

    /** 流量类型 [P:A 截图]：全部 / 知道 / 宝宝知道（不含app）/ 宝宝知道app。网页搜索流量计入“知道” [I]。 */
    public enum Flow { ALL, ZHIDAO, BAOBAO, BAOBAO_APP }

    private static List<String> channels(Flow flow) {
        if (flow == null) {
            return null;
        }
        return switch (flow) {
            case ZHIDAO -> List.of("SEARCH", "ZHIDAO");
            case BAOBAO -> List.of("BAOBAO");
            case BAOBAO_APP -> List.of("BAOBAO_APP");
            default -> null;
        };
    }

    public Map<String, Object> report(Long accountId, Level level, LocalDate from, LocalDate to, Device device, Long planId, Long unitId) {
        return report(accountId, level, from, to, device, planId, unitId, Flow.ALL, false);
    }

    /**
     * @param flow     流量类型筛选
     * @param byRegion true = 分地域（每个对象按网民地域拆成多行）[P:A 截图“地域类型：全地域/分地域”]
     */
    public Map<String, Object> report(Long accountId, Level level, LocalDate from, LocalDate to, Device device, Long planId, Long unitId,
                                      Flow flow, boolean byRegion) {
        List<String> ch = channels(flow);
        LocalDate f = from == null ? LocalDate.now().minusDays(7) : from;
        LocalDate t = to == null ? LocalDate.now().minusDays(1) : to;
        if (f.isAfter(t)) {
            throw BizException.badRequest("开始日期不能晚于结束日期");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("level", level);
        result.put("from", f);
        result.put("to", t);
        if (level == Level.MIDDLE_PAGE) {
            result.put("rows", middlePageRows(accountId, f, t, device, planId, ch, byRegion));
            result.put("trend", mpTrend(accountId, f, t, device, planId, ch));
            return result;
        }
        String groupCol = switch (level) {
            case PLAN -> "plan_id";
            case UNIT -> "unit_id";
            case QA -> "qa_id";
            default -> "account_id";
        };
        QueryWrapper<DailyStat> w = base(accountId, f, t, device, planId, unitId, ch);
        if (byRegion) {
            w.select(groupCol + " AS target_id", "region AS region", "SUM(impressions) AS impressions", "SUM(clicks) AS clicks", "SUM(cost) AS cost")
                    .groupBy(groupCol, "region");
        } else {
            w.select(groupCol + " AS target_id", "SUM(impressions) AS impressions", "SUM(clicks) AS clicks", "SUM(cost) AS cost")
                    .groupBy(groupCol);
        }
        List<Map<String, Object>> raw = statMapper.selectMaps(w);
        Set<Long> ids = raw.stream().map(r -> StatService.toLong(r.get("target_id"))).collect(Collectors.toSet());
        Map<Long, String> names = names(level, ids);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : raw) {
            long id = StatService.toLong(r.get("target_id"));
            Map<String, Object> m = metrics(id, names.getOrDefault(id, "(已删除 #" + id + ")"),
                    StatService.toLong(r.get("impressions")), StatService.toLong(r.get("clicks")), StatService.toDecimal(r.get("cost")));
            if (byRegion) {
                m.put("region", String.valueOf(r.get("region")));
            }
            rows.add(m);
        }
        rows.sort((a, b) -> ((BigDecimal) b.get("cost")).compareTo((BigDecimal) a.get("cost")));
        result.put("rows", rows);

        List<Map<String, Object>> trendRaw = statMapper.selectMaps(base(accountId, f, t, device, planId, unitId, ch)
                .select("stat_date AS stat_date", "SUM(impressions) AS impressions", "SUM(clicks) AS clicks", "SUM(cost) AS cost")
                .groupBy("stat_date").orderByAsc("stat_date"));
        Map<String, Map<String, Object>> byDate = new LinkedHashMap<>();
        for (Map<String, Object> r : trendRaw) {
            byDate.put(String.valueOf(r.get("stat_date")), r);
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        long ti = 0, tc = 0;
        BigDecimal tcost = BigDecimal.ZERO;
        for (LocalDate d = f; !d.isAfter(t); d = d.plusDays(1)) {
            Map<String, Object> r = byDate.get(d.toString());
            long imp = r == null ? 0 : StatService.toLong(r.get("impressions"));
            long clk = r == null ? 0 : StatService.toLong(r.get("clicks"));
            BigDecimal cost = r == null ? BigDecimal.ZERO : StatService.toDecimal(r.get("cost"));
            ti += imp;
            tc += clk;
            tcost = tcost.add(cost);
            Map<String, Object> m = metrics(null, d.toString(), imp, clk, cost);
            trend.add(m);
        }
        result.put("trend", trend);
        result.put("summary", metrics(null, "合计", ti, tc, tcost));
        return result;
    }

    private QueryWrapper<DailyStat> base(Long accountId, LocalDate f, LocalDate t, Device device, Long planId, Long unitId, List<String> ch) {
        return new QueryWrapper<DailyStat>()
                .in(ch != null, "channel", ch)
                .eq("account_id", accountId)
                .ge("stat_date", f).le("stat_date", t)
                .eq(device != null, "device", device == null ? null : device.name())
                .eq(planId != null, "plan_id", planId)
                .eq(unitId != null, "unit_id", unitId);
    }

    private List<Map<String, Object>> middlePageRows(Long accountId, LocalDate f, LocalDate t, Device device, Long planId,
                                                     List<String> ch, boolean byRegion) {
        QueryWrapper<MpDailyStat> w = mpBase(accountId, f, t, device, planId, ch);
        if (byRegion) {
            w.select("middle_page_id AS target_id", "region AS region", "SUM(views) AS impressions", "SUM(clicks) AS clicks")
                    .groupBy("middle_page_id", "region");
        } else {
            w.select("middle_page_id AS target_id", "SUM(views) AS impressions", "SUM(clicks) AS clicks").groupBy("middle_page_id");
        }
        List<Map<String, Object>> raw = mpStatMapper.selectMaps(w);
        Set<Long> ids = raw.stream().map(r -> StatService.toLong(r.get("target_id"))).collect(Collectors.toSet());
        Map<Long, String> names = names(Level.MIDDLE_PAGE, ids);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : raw) {
            long id = StatService.toLong(r.get("target_id"));
            Map<String, Object> m = metrics(id, names.getOrDefault(id, "(已删除 #" + id + ")"),
                    StatService.toLong(r.get("impressions")), StatService.toLong(r.get("clicks")), null);
            if (byRegion) {
                m.put("region", String.valueOf(r.get("region")));
            }
            rows.add(m);
        }
        return rows;
    }

    private List<Map<String, Object>> mpTrend(Long accountId, LocalDate f, LocalDate t, Device device, Long planId, List<String> ch) {
        List<Map<String, Object>> raw = mpStatMapper.selectMaps(mpBase(accountId, f, t, device, planId, ch)
                .select("stat_date AS stat_date", "SUM(views) AS impressions", "SUM(clicks) AS clicks")
                .groupBy("stat_date").orderByAsc("stat_date"));
        Map<String, Map<String, Object>> byDate = raw.stream().collect(Collectors.toMap(r -> String.valueOf(r.get("stat_date")), Function.identity()));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (LocalDate d = f; !d.isAfter(t); d = d.plusDays(1)) {
            Map<String, Object> r = byDate.get(d.toString());
            trend.add(metrics(null, d.toString(), r == null ? 0 : StatService.toLong(r.get("impressions")),
                    r == null ? 0 : StatService.toLong(r.get("clicks")), null));
        }
        return trend;
    }

    private QueryWrapper<MpDailyStat> mpBase(Long accountId, LocalDate f, LocalDate t, Device device, Long planId, List<String> ch) {
        return new QueryWrapper<MpDailyStat>()
                .in(ch != null, "channel", ch)
                .eq("account_id", accountId)
                .ge("stat_date", f).le("stat_date", t)
                .eq(device != null, "device", device == null ? null : device.name())
                .eq(planId != null, "plan_id", planId);
    }

    private Map<String, Object> metrics(Long id, String name, long imp, long clk, BigDecimal cost) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (id != null) {
            m.put("targetId", id);
        }
        m.put("name", name);
        m.put("impressions", imp);
        m.put("clicks", clk);
        m.put("ctr", imp == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(clk * 100.0 / imp).setScale(2, RoundingMode.HALF_UP));
        if (cost != null) {
            m.put("cost", cost.setScale(2, RoundingMode.HALF_UP));
            m.put("cpc", clk == 0 ? BigDecimal.ZERO : cost.divide(BigDecimal.valueOf(clk), 2, RoundingMode.HALF_UP));
        }
        return m;
    }

    private Map<Long, String> names(Level level, Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return switch (level) {
            case PLAN -> planMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(Plan::getId, Plan::getName));
            case UNIT -> unitMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(Unit::getId, Unit::getName));
            case QA -> qaMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(QuestionAnswer::getId, QuestionAnswer::getQuestion));
            case MIDDLE_PAGE -> middlePageMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(MiddlePage::getId, MiddlePage::getName));
            default -> ids.stream().collect(Collectors.toMap(i -> i, i -> "账户合计"));
        };
    }
}
