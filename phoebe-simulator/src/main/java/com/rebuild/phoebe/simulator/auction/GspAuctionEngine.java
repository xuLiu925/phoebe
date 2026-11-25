package com.rebuild.phoebe.simulator.auction;

import com.rebuild.phoebe.common.Evidence;
import com.rebuild.phoebe.common.EvidenceLevel;
import com.rebuild.phoebe.simulator.SimulatorProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 广义第二价格（GSP）竞拍。
 * <p>
 * [P:A] 二价竞拍模式，CPC 计费；[P:C] 计费综合考虑排序中低于胜出广告主的其他广告主实际出价；
 * [P:B] 点击率越高展现位次越高（点击调权）。
 * 公式 [I]：score = 有效出价 × 质量度；计费 = min(有效出价, 下一名 score / 自身质量度 + 0.01)，不低于底价。
 * 同一账户只取最优一条 [I]。
 */
@Component
@RequiredArgsConstructor
@Evidence(level = EvidenceLevel.INFERRED, source = "A,B,C")
public class GspAuctionEngine implements AuctionEngine {
    private static final BigDecimal STEP = new BigDecimal("0.01");
    private final SimulatorProperties props;

    @Override
    public List<Candidate> run(List<Candidate> candidates, int slots) {
        List<Candidate> sorted = new ArrayList<>(candidates);
        for (Candidate c : sorted) {
            c.setScore(c.getEffectiveBid().doubleValue() * c.getQuality());
        }
        sorted.sort(Comparator.comparingDouble(Candidate::getScore).reversed()
                .thenComparing(Candidate::getEffectiveBid, Comparator.reverseOrder())
                .thenComparing(Candidate::getQaId));

        // 同账户去重：仅保留排序最高的一条参与
        Set<Long> seen = new HashSet<>();
        List<Candidate> ranked = new ArrayList<>();
        for (Candidate c : sorted) {
            if (seen.add(c.getAccountId())) {
                ranked.add(c);
            } else {
                c.setNote("同账户已有更优问答参与");
            }
        }
        BigDecimal minPrice = props.getMinPrice();
        for (int i = 0; i < ranked.size(); i++) {
            Candidate c = ranked.get(i);
            c.setRank(i + 1);
            BigDecimal price;
            if (i + 1 < ranked.size()) {
                Candidate next = ranked.get(i + 1);
                price = BigDecimal.valueOf(next.getScore() / c.getQuality()).add(STEP);
            } else {
                price = minPrice;
            }
            price = price.max(minPrice).min(c.getEffectiveBid()).setScale(2, RoundingMode.HALF_UP);
            c.setChargePrice(price);
            c.setWon(i < slots);
        }
        List<Candidate> all = new ArrayList<>(ranked);
        for (Candidate c : sorted) {
            if (!ranked.contains(c)) {
                all.add(c);
            }
        }
        return all;
    }
}
