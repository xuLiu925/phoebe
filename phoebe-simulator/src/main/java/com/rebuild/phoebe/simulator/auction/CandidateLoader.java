package com.rebuild.phoebe.simulator.auction;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rebuild.phoebe.domain.entity.Account;
import com.rebuild.phoebe.domain.entity.MiddlePage;
import com.rebuild.phoebe.domain.entity.Plan;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.entity.Unit;
import com.rebuild.phoebe.domain.enums.Device;
import com.rebuild.phoebe.domain.mapper.AccountMapper;
import com.rebuild.phoebe.domain.mapper.PlanMapper;
import com.rebuild.phoebe.domain.mapper.QuestionAnswerMapper;
import com.rebuild.phoebe.domain.mapper.UnitMapper;
import com.rebuild.phoebe.domain.service.EligibilityService;
import com.rebuild.phoebe.domain.service.StatService;
import com.rebuild.phoebe.domain.service.UnitService;
import com.rebuild.phoebe.simulator.SimulatorProperties;
import com.rebuild.phoebe.simulator.match.MatchService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 根据检索词召回匹配的问答，并区分“可参与竞价”与“匹配但不具备投放资格”。 */
@Component
@RequiredArgsConstructor
public class CandidateLoader {
    private final QuestionAnswerMapper qaMapper;
    private final UnitMapper unitMapper;
    private final PlanMapper planMapper;
    private final AccountMapper accountMapper;
    private final UnitService unitService;
    private final EligibilityService eligibility;
    private final StatService statService;
    private final MatchService matchService;
    private final SimulatorProperties props;

    @Data
    public static class Excluded {
        private Long qaId;
        private String question;
        private String accountName;
        private String matchType;
        private List<String> reasons;
    }

    @Data
    public static class Recall {
        private List<Candidate> candidates = new ArrayList<>();
        private List<Excluded> excluded = new ArrayList<>();
    }

    public Recall recall(String query, Device device, String region, Long excludeQaId) {
        return recall(query, device, region, null, null, excludeQaId);
    }

    /**
     * @param ip   网民 IP（用于计划 IP 排除），可空
     * @param time 检索时刻（用于投放时段），空 = 当前时间
     */
    public Recall recall(String query, Device device, String region, String ip, java.time.LocalDateTime time, Long excludeQaId) {
        java.time.LocalDateTime at = time == null ? java.time.LocalDateTime.now() : time;
        Recall recall = new Recall();
        List<QuestionAnswer> qas = qaMapper.selectList(new LambdaQueryWrapper<QuestionAnswer>());
        if (qas.isEmpty()) {
            return recall;
        }
        Set<Long> unitIds = qas.stream().map(QuestionAnswer::getUnitId).collect(Collectors.toSet());
        Map<Long, Unit> units = unitMapper.selectBatchIds(unitIds).stream().collect(Collectors.toMap(Unit::getId, Function.identity()));
        Map<Long, Plan> plans = planMapper.selectBatchIds(qas.stream().map(QuestionAnswer::getPlanId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Plan::getId, Function.identity()));
        Map<Long, Account> accounts = accountMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        Map<Long, List<String>> words = unitService.subscribeWordsByUnits(unitIds);
        Map<Long, List<String>> planBlockers = new HashMap<>();

        for (QuestionAnswer qa : qas) {
            if (excludeQaId != null && excludeQaId.equals(qa.getId())) {
                continue;
            }
            Unit unit = units.get(qa.getUnitId());
            Plan plan = plans.get(qa.getPlanId());
            if (unit == null || plan == null) {
                continue;
            }
            String mt = matchService.match(query, qa.getQuestion(), words.get(qa.getUnitId()));
            if (mt == null) {
                continue;
            }
            Account account = accounts.get(qa.getAccountId());
            List<String> reasons = new ArrayList<>(eligibility.qaBlockers(qa, unit));
            reasons.addAll(planBlockers.computeIfAbsent(plan.getId(), id -> eligibility.planBlockers(plan, account, at)));
            if (com.rebuild.phoebe.domain.service.TargetingRules.ipExcluded(plan, ip)) {
                reasons.add("网民 IP 已被计划排除");
            }
            if (!EligibilityService.regionMatches(plan, region)) {
                reasons.add("推广地域不包含 " + region);
            }
            BigDecimal bid = qa.getBid() != null ? qa.getBid() : unit.getBid();
            BigDecimal eBid = device == Device.MOBILE
                    ? bid.multiply(plan.getMobileBidRatio()).setScale(2, RoundingMode.HALF_UP) : bid;
            if (eBid.compareTo(props.getMinPrice()) < 0) {
                reasons.add("有效出价低于底价 " + props.getMinPrice());
            }
            if (!reasons.isEmpty()) {
                Excluded ex = new Excluded();
                ex.setQaId(qa.getId());
                ex.setQuestion(qa.getQuestion());
                ex.setAccountName(account == null ? null : account.getCompanyName());
                ex.setMatchType(mt);
                ex.setReasons(reasons);
                recall.getExcluded().add(ex);
                continue;
            }
            MiddlePage mp = eligibility.enabledValidPage(plan.getId());
            Candidate c = new Candidate();
            c.setQa(qa);
            c.setUnit(unit);
            c.setPlan(plan);
            c.setAccount(account);
            c.setQaId(qa.getId());
            c.setQuestion(qa.getQuestion());
            c.setAccountId(qa.getAccountId());
            c.setAccountName(account.getCompanyName());
            c.setMiddlePageId(mp.getId());
            c.setMatchType(mt);
            c.setBid(bid);
            c.setEffectiveBid(eBid);
            c.setQuality(quality(qa.getId()));
            recall.getCandidates().add(c);
        }
        return recall;
    }

    /** 质量度：平滑历史 CTR / 基准 CTR，限制在 [0.5, 2.0]（点击调权 [P:B]，公式 [I]）。 */
    public double quality(Long qaId) {
        long[] h = statService.qaHistory(qaId);
        double ctr = (h[1] + props.getBaseCtr() * props.getSmoothing()) / (h[0] + props.getSmoothing());
        double q = ctr / props.getBaseCtr();
        q = Math.max(0.5, Math.min(2.0, q));
        return Math.round(q * 1000) / 1000.0;
    }
}
