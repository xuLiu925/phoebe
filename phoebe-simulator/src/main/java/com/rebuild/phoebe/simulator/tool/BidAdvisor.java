package com.rebuild.phoebe.simulator.tool;

import com.rebuild.phoebe.domain.entity.Plan;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.entity.Unit;
import com.rebuild.phoebe.domain.enums.Device;
import com.rebuild.phoebe.domain.mapper.PlanMapper;
import com.rebuild.phoebe.domain.mapper.UnitMapper;
import com.rebuild.phoebe.domain.service.Guard;
import com.rebuild.phoebe.simulator.SimulatorProperties;
import com.rebuild.phoebe.simulator.auction.Candidate;
import com.rebuild.phoebe.simulator.auction.CandidateLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 出价助手：提供出价建议，提升展现率 [P:A 存在]。算法 [I]：
 * 以问题标题作为检索词召回竞争者，计算排到第一位所需的出价。
 */
@Service
@RequiredArgsConstructor
public class BidAdvisor {
    private final Guard guard;
    private final UnitMapper unitMapper;
    private final PlanMapper planMapper;
    private final CandidateLoader loader;
    private final SimulatorProperties props;

    public Map<String, Object> suggest(Long qaId) {
        QuestionAnswer qa = guard.qa(qaId);
        Unit unit = unitMapper.selectById(qa.getUnitId());
        Plan plan = planMapper.selectById(qa.getPlanId());
        BigDecimal bid = qa.getBid() != null ? qa.getBid() : unit.getBid();
        double q = loader.quality(qaId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("qaId", qaId);
        r.put("question", qa.getQuestion());
        r.put("currentBid", bid);
        r.put("quality", q);
        for (Device d : Device.values()) {
            CandidateLoader.Recall recall = loader.recall(qa.getQuestion(), d, null, qaId);
            double top = recall.getCandidates().stream()
                    .filter(c -> !c.getAccountId().equals(qa.getAccountId()))
                    .mapToDouble(c -> c.getEffectiveBid().doubleValue() * c.getQuality()).max().orElse(0);
            BigDecimal ratio = d == Device.MOBILE ? plan.getMobileBidRatio() : BigDecimal.ONE;
            BigDecimal need = top == 0 ? props.getMinPrice()
                    : BigDecimal.valueOf(top / q).add(new BigDecimal("0.01"));
            BigDecimal base = need.divide(ratio, 2, RoundingMode.UP);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("competitors", recall.getCandidates().stream().filter(c -> !c.getAccountId().equals(qa.getAccountId())).count());
            m.put("topCompetitorScore", Math.round(top * 100) / 100.0);
            m.put("suggestedBid", base.max(props.getMinPrice()));
            m.put("currentRankFirst", bid.multiply(ratio).doubleValue() * q > top);
            r.put(d.name().toLowerCase(), m);
        }
        r.put("note", "模拟算法：建议价 = 最强竞争者综合得分 / 自身质量度 + 0.01，移动端再除以移动出价比例");
        return r;
    }

    /** 供测试/展示：计算单个候选。 */
    static double score(Candidate c) {
        return c.getEffectiveBid().doubleValue() * c.getQuality();
    }
}
