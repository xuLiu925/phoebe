package com.rebuild.phoebe.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rebuild.phoebe.domain.entity.DailyStat;
import com.rebuild.phoebe.domain.entity.MpDailyStat;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.enums.Channel;
import com.rebuild.phoebe.domain.enums.Device;
import com.rebuild.phoebe.domain.mapper.DailyStatMapper;
import com.rebuild.phoebe.domain.mapper.MpDailyStatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 日报累加与常用聚合。存储结构 [U]，指标 [P:B]。 */
@Service
@RequiredArgsConstructor
public class StatService {
    private final DailyStatMapper statMapper;
    private final MpDailyStatMapper mpStatMapper;

    public static final String UNKNOWN_REGION = "未知";

    public void addQa(QuestionAnswer qa, Device device, LocalDate date, long impressions, long clicks, BigDecimal cost) {
        addQa(qa, device, Channel.ZHIDAO, null, date, impressions, clicks, cost);
    }

    /** 按 日期 × 问答 × 设备 × 渠道 × 地域 累加（渠道、地域用于报表“流量类型 / 分地域” [P:A 截图]）。 */
    public synchronized void addQa(QuestionAnswer qa, Device device, Channel channel, String region, LocalDate date,
                                   long impressions, long clicks, BigDecimal cost) {
        String rg = region == null || region.isBlank() ? UNKNOWN_REGION : region;
        Channel ch = channel == null ? Channel.ZHIDAO : channel;
        LambdaUpdateWrapper<DailyStat> w = new LambdaUpdateWrapper<DailyStat>()
                .eq(DailyStat::getStatDate, date).eq(DailyStat::getQaId, qa.getId()).eq(DailyStat::getDevice, device)
                .eq(DailyStat::getChannel, ch).eq(DailyStat::getRegion, rg)
                .setSql("impressions = impressions + " + impressions)
                .setSql("clicks = clicks + " + clicks)
                .setSql("cost = cost + " + cost.toPlainString());
        if (statMapper.update(null, w) == 0) {
            DailyStat s = new DailyStat();
            s.setStatDate(date);
            s.setAccountId(qa.getAccountId());
            s.setPlanId(qa.getPlanId());
            s.setUnitId(qa.getUnitId());
            s.setQaId(qa.getId());
            s.setDevice(device);
            s.setChannel(ch);
            s.setRegion(rg);
            s.setImpressions(impressions);
            s.setClicks(clicks);
            s.setCost(cost);
            statMapper.insert(s);
        }
    }

    public void addMiddlePage(Long accountId, Long planId, Long pageId, Device device, LocalDate date, long views, long clicks) {
        addMiddlePage(accountId, planId, pageId, device, Channel.ZHIDAO, null, date, views, clicks);
    }

    public synchronized void addMiddlePage(Long accountId, Long planId, Long pageId, Device device, Channel channel, String region,
                                           LocalDate date, long views, long clicks) {
        String rg = region == null || region.isBlank() ? UNKNOWN_REGION : region;
        Channel ch = channel == null ? Channel.ZHIDAO : channel;
        LambdaUpdateWrapper<MpDailyStat> w = new LambdaUpdateWrapper<MpDailyStat>()
                .eq(MpDailyStat::getStatDate, date).eq(MpDailyStat::getMiddlePageId, pageId).eq(MpDailyStat::getDevice, device)
                .eq(MpDailyStat::getChannel, ch).eq(MpDailyStat::getRegion, rg)
                .setSql("views = views + " + views)
                .setSql("clicks = clicks + " + clicks);
        if (mpStatMapper.update(null, w) == 0) {
            MpDailyStat s = new MpDailyStat();
            s.setStatDate(date);
            s.setAccountId(accountId);
            s.setPlanId(planId);
            s.setMiddlePageId(pageId);
            s.setDevice(device);
            s.setChannel(ch);
            s.setRegion(rg);
            s.setViews(views);
            s.setClicks(clicks);
            mpStatMapper.insert(s);
        }
    }

    public BigDecimal planCost(Long planId, LocalDate date) {
        List<Map<String, Object>> rows = statMapper.selectMaps(new QueryWrapper<DailyStat>()
                .select("COALESCE(SUM(cost), 0) AS total")
                .eq("plan_id", planId).eq("stat_date", date));
        return rows.isEmpty() || rows.get(0) == null ? BigDecimal.ZERO : toDecimal(rows.get(0).get("total"));
    }

    /** 问答历史展现/点击，用于质量度（点击调权）。 */
    public long[] qaHistory(Long qaId) {
        List<Map<String, Object>> rows = statMapper.selectMaps(new QueryWrapper<DailyStat>()
                .select("COALESCE(SUM(impressions), 0) AS imp", "COALESCE(SUM(clicks), 0) AS clk")
                .eq("qa_id", qaId));
        if (rows.isEmpty() || rows.get(0) == null) {
            return new long[]{0, 0};
        }
        return new long[]{toLong(rows.get(0).get("imp")), toLong(rows.get(0).get("clk"))};
    }

    public boolean isEmpty() {
        return statMapper.selectCount(new LambdaQueryWrapper<>()) == 0;
    }

    public static BigDecimal toDecimal(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        return o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }

    public static long toLong(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
