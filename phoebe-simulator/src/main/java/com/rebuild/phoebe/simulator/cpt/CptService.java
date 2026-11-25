package com.rebuild.phoebe.simulator.cpt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.common.Evidence;
import com.rebuild.phoebe.common.EvidenceLevel;
import com.rebuild.phoebe.common.UserContext;
import com.rebuild.phoebe.domain.entity.ClickLog;
import com.rebuild.phoebe.domain.entity.CptOrder;
import com.rebuild.phoebe.domain.entity.ImpressionLog;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.entity.QuestionLibrary;
import com.rebuild.phoebe.domain.enums.AuditStatus;
import com.rebuild.phoebe.domain.mapper.ClickLogMapper;
import com.rebuild.phoebe.domain.mapper.CptOrderMapper;
import com.rebuild.phoebe.domain.mapper.ImpressionLogMapper;
import com.rebuild.phoebe.domain.mapper.QuestionAnswerMapper;
import com.rebuild.phoebe.domain.service.AccountService;
import com.rebuild.phoebe.domain.service.Guard;
import com.rebuild.phoebe.domain.service.OpLogService;
import com.rebuild.phoebe.simulator.library.QuestionLibraryProvider;
import com.rebuild.phoebe.simulator.match.TextSimilarity;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CPT 包词。
 * <ul>
 *   <li>[C] 产品同时有 CPC 与 CPT；同一个词 CPT 展示优先级高于 CPC</li>
 *   <li>[C] 按天包词、下单时一次性预付；同一词同一天只卖给一家（独占）</li>
 *   <li>[C] CPT 占第一个广告位，CPC 顺延；CPT 点击不扣费</li>
 *   <li>[I] 开始前取消全额退款，投放中取消退还明天起的未投放天数；已结束不可取消</li>
 *   <li>[I] 多个 CPT 词同时命中一次检索时，取最长（最具体）的词</li>
 *   <li>[U] 定价公式、最长天数</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Evidence(level = EvidenceLevel.CONFIRMED, note = "CPT 优先级与四条售卖规则为已确认；退款与多词命中规则为推测")
public class CptService {
    private final CptOrderMapper mapper;
    private final QuestionAnswerMapper qaMapper;
    private final ImpressionLogMapper impressionMapper;
    private final ClickLogMapper clickMapper;
    private final QuestionLibraryProvider library;
    private final AccountService accountService;
    private final Guard guard;
    private final OpLogService opLog;
    private final CptProperties props;

    @Data
    public static class Quote {
        private String keyword;
        private LocalDate startDate;
        private LocalDate endDate;
        private long days;
        private BigDecimal dailyPrice;
        private BigDecimal total;
        private long monthlyPv;
        private boolean available;
        /** 与已售订单冲突的日期段（不透露其他广告主信息）。 */
        private List<Map<String, Object>> conflicts = new ArrayList<>();
    }

    // ------------------------------------------------------------------ 询价

    public Quote quote(String keyword, LocalDate start, LocalDate end) {
        String norm = normalizeKeyword(keyword);
        validateDates(start, end);
        long pv = monthlyPv(keyword);
        BigDecimal daily = props.getBaseDailyPrice()
                .add(props.getPricePerThousandPv().multiply(BigDecimal.valueOf(pv)).divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP))
                .max(props.getMinDailyPrice()).min(props.getMaxDailyPrice())
                .setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.UNNECESSARY);
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        Quote q = new Quote();
        q.setKeyword(keyword.trim());
        q.setStartDate(start);
        q.setEndDate(end);
        q.setDays(days);
        q.setDailyPrice(daily);
        q.setTotal(daily.multiply(BigDecimal.valueOf(days)));
        q.setMonthlyPv(pv);
        for (CptOrder o : overlapping(norm, start, end, null)) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("from", o.getStartDate().isBefore(start) ? start : o.getStartDate());
            c.put("to", o.getEndDate().isAfter(end) ? end : o.getEndDate());
            c.put("mine", isMine(o.getAccountId()));
            q.getConflicts().add(c);
        }
        q.setAvailable(q.getConflicts().isEmpty());
        return q;
    }

    /** 词相关问题的月检索量（模拟问答知识库），作为定价依据 [U]。 */
    private long monthlyPv(String keyword) {
        String k = keyword.trim();
        return library.byKeyword(k, 200).stream()
                .filter(x -> x.getQuestion().contains(k) || (x.getKeywords() != null && x.getKeywords().contains(k)))
                .mapToLong(QuestionLibrary::getPv).sum();
    }

    // ------------------------------------------------------------------ 下单 / 取消

    @Transactional
    public CptOrder create(Long qaId, String keyword, LocalDate start, LocalDate end) {
        QuestionAnswer qa = guard.qa(qaId);
        if (qa.getAuditStatus() != AuditStatus.APPROVED) {
            throw BizException.state("只能为审核通过（有效）的问答购买 CPT [I]");
        }
        Quote q = quote(keyword, start, end);
        if (!q.isAvailable()) {
            throw BizException.state("所选日期内该词已被包断（同一个词同一天只卖给一家）");
        }
        accountService.charge(qa.getAccountId(), q.getTotal(), "CPT 包词「" + q.getKeyword() + "」" + start + "~" + end);
        CptOrder o = new CptOrder();
        o.setAccountId(qa.getAccountId());
        o.setPlanId(qa.getPlanId());
        o.setUnitId(qa.getUnitId());
        o.setQaId(qaId);
        o.setKeyword(q.getKeyword());
        o.setKeywordNorm(normalizeKeyword(keyword));
        o.setStartDate(start);
        o.setEndDate(end);
        o.setDailyPrice(q.getDailyPrice());
        o.setTotalPrice(q.getTotal());
        o.setRefundAmount(BigDecimal.ZERO);
        o.setStatus(CptOrder.ACTIVE);
        mapper.insert(o);
        opLog.log(o.getAccountId(), "CPT", o.getId(), "CREATE",
                "「" + o.getKeyword() + "」" + start + "~" + end + " 共 " + q.getDays() + " 天，扣款 " + q.getTotal());
        return decorate(List.of(mapper.selectById(o.getId()))).get(0);
    }

    @Transactional
    public CptOrder cancel(Long id) {
        CptOrder o = mapper.selectById(id);
        if (o == null) {
            throw BizException.notFound("CPT 订单");
        }
        guard.check(o.getAccountId());
        if (CptOrder.CANCELED.equals(o.getStatus())) {
            throw BizException.state("订单已取消");
        }
        LocalDate today = LocalDate.now();
        if (o.getEndDate().isBefore(today)) {
            throw BizException.state("订单已结束，不能取消");
        }
        // 开始前取消：全额退；投放中：退明天起的天数（今天已开始投放不退）[I]
        LocalDate refundFrom = o.getStartDate().isAfter(today) ? o.getStartDate() : today.plusDays(1);
        long refundDays = refundFrom.isAfter(o.getEndDate()) ? 0 : ChronoUnit.DAYS.between(refundFrom, o.getEndDate()) + 1;
        BigDecimal refund = o.getDailyPrice().multiply(BigDecimal.valueOf(refundDays));
        o.setStatus(CptOrder.CANCELED);
        o.setRefundAmount(refund);
        o.setCanceledAt(LocalDateTime.now());
        mapper.updateById(o);
        accountService.refund(o.getAccountId(), refund, "取消 CPT 包词「" + o.getKeyword() + "」");
        opLog.log(o.getAccountId(), "CPT", id, "CANCEL", "退款 " + refund + "（" + refundDays + " 天）");
        return decorate(List.of(o)).get(0);
    }

    // ------------------------------------------------------------------ 查询

    public List<CptOrder> list(Long accountId) {
        return decorate(mapper.selectList(new LambdaQueryWrapper<CptOrder>()
                .eq(CptOrder::getAccountId, accountId).orderByDesc(CptOrder::getId)));
    }

    /** CPT 报告：按订单统计时间范围内的展现、点击（CPT 不进入 CPC 日报，单独统计）。 */
    public List<Map<String, Object>> report(Long accountId, LocalDate from, LocalDate to) {
        List<CptOrder> orders = list(accountId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CptOrder o : orders) {
            LocalDate f = max(from, o.getStartDate());
            LocalDate t = min(to, o.getEndDate());
            long imp = 0;
            long clk = 0;
            if (!f.isAfter(t)) {
                imp = impressionMapper.selectCount(new LambdaQueryWrapper<ImpressionLog>()
                        .eq(ImpressionLog::getCptOrderId, o.getId())
                        .ge(ImpressionLog::getCreatedAt, f.atStartOfDay()).lt(ImpressionLog::getCreatedAt, t.plusDays(1).atStartOfDay()));
                clk = countClicks(o.getId(), f, t);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("targetId", o.getId());
            m.put("name", o.getKeyword());
            m.put("question", o.getQuestion());
            m.put("period", o.getStartDate() + " ~ " + o.getEndDate());
            m.put("phase", o.getPhase());
            m.put("impressions", imp);
            m.put("clicks", clk);
            m.put("ctr", imp == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(clk * 100.0 / imp).setScale(2, RoundingMode.HALF_UP));
            m.put("cost", o.getTotalPrice().subtract(o.getRefundAmount()));
            m.put("dailyPrice", o.getDailyPrice());
            rows.add(m);
        }
        return rows;
    }

    private long countClicks(Long orderId, LocalDate f, LocalDate t) {
        List<Object> impIds = impressionMapper.selectObjs(new QueryWrapper<ImpressionLog>().select("id").eq("cpt_order_id", orderId));
        if (impIds.isEmpty()) {
            return 0;
        }
        return clickMapper.selectCount(new LambdaQueryWrapper<ClickLog>()
                .in(ClickLog::getImpressionId, impIds)
                .eq(ClickLog::getClickType, ClickLog.QA_ENTRY)
                .ge(ClickLog::getCreatedAt, f.atStartOfDay()).lt(ClickLog::getCreatedAt, t.plusDays(1).atStartOfDay()));
    }

    /**
     * 找出某次检索命中的 CPT 订单（可能多个，按优先级排序）：订单有效、日期覆盖检索日、
     * 检索词包含包断词（归一化后），最长的词优先 [I]。资格判断由调用方完成。
     */
    public List<CptOrder> matching(String query, LocalDate date) {
        String q = TextSimilarity.normalize(query);
        if (q.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<CptOrder>()
                        .eq(CptOrder::getStatus, CptOrder.ACTIVE)
                        .le(CptOrder::getStartDate, date).ge(CptOrder::getEndDate, date))
                .stream()
                .filter(o -> q.contains(o.getKeywordNorm()))
                .sorted(Comparator.comparingInt((CptOrder o) -> o.getKeywordNorm().length()).reversed().thenComparing(CptOrder::getId))
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ internals

    private List<CptOrder> overlapping(String norm, LocalDate start, LocalDate end, Long excludeId) {
        // 已取消的订单立即停止投放并释放排期
        return mapper.selectList(new LambdaQueryWrapper<CptOrder>()
                .eq(CptOrder::getKeywordNorm, norm)
                .eq(CptOrder::getStatus, CptOrder.ACTIVE)
                .le(CptOrder::getStartDate, end).ge(CptOrder::getEndDate, start)
                .ne(excludeId != null, CptOrder::getId, excludeId));
    }

    private String normalizeKeyword(String keyword) {
        String n = TextSimilarity.normalize(keyword);
        if (n.length() < props.getMinKeywordLength() || n.length() > 64) {
            throw BizException.badRequest("包断词需为 " + props.getMinKeywordLength() + "~64 个字符（去掉标点和空格后）");
        }
        return n;
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw BizException.badRequest("请选择投放日期");
        }
        LocalDate today = LocalDate.now();
        if (start.isBefore(props.isAllowToday() ? today : today.plusDays(1))) {
            throw BizException.badRequest(props.isAllowToday() ? "开始日期不能早于今天" : "开始日期最早为明天");
        }
        if (end.isBefore(start)) {
            throw BizException.badRequest("结束日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > props.getMaxDays()) {
            throw BizException.limit("单个 CPT 订单最长 " + props.getMaxDays() + " 天");
        }
        if (start.isAfter(today.plusDays(props.getMaxAdvanceDays()))) {
            throw BizException.badRequest("最多只能提前 " + props.getMaxAdvanceDays() + " 天下单");
        }
    }

    private boolean isMine(Long accountId) {
        UserContext.CurrentUser u = UserContext.get();
        return u != null && Objects.equals(u.accountId(), accountId);
    }

    private List<CptOrder> decorate(List<CptOrder> orders) {
        if (orders.isEmpty()) {
            return orders;
        }
        Set<Long> qaIds = orders.stream().map(CptOrder::getQaId).collect(Collectors.toSet());
        Map<Long, String> questions = qaMapper.selectBatchIds(qaIds).stream()
                .collect(Collectors.toMap(QuestionAnswer::getId, QuestionAnswer::getQuestion));
        LocalDate today = LocalDate.now();
        for (CptOrder o : orders) {
            o.setQuestion(questions.getOrDefault(o.getQaId(), "(问答已删除 #" + o.getQaId() + ")"));
            if (CptOrder.CANCELED.equals(o.getStatus())) {
                o.setPhase("已取消");
            } else if (o.getStartDate().isAfter(today)) {
                o.setPhase("待开始");
            } else if (o.getEndDate().isBefore(today)) {
                o.setPhase("已结束");
            } else {
                o.setPhase("投放中");
            }
            o.setImpressions(impressionMapper.selectCount(new LambdaQueryWrapper<ImpressionLog>().eq(ImpressionLog::getCptOrderId, o.getId())));
            o.setClicks(countClicks(o.getId(), o.getStartDate(), o.getEndDate()));
        }
        return orders;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a == null ? b : (a.isAfter(b) ? a : b);
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a == null ? b : (a.isBefore(b) ? a : b);
    }
}
