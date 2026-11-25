package com.rebuild.phoebe.web;

import com.rebuild.phoebe.domain.entity.Plan;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.enums.Channel;
import com.rebuild.phoebe.domain.enums.Device;
import com.rebuild.phoebe.domain.mapper.PlanMapper;
import com.rebuild.phoebe.domain.mapper.QuestionAnswerMapper;
import com.rebuild.phoebe.domain.service.AccountService;
import com.rebuild.phoebe.domain.service.MiddlePageService;
import com.rebuild.phoebe.domain.service.StatService;
import com.rebuild.phoebe.simulator.SimulatorProperties;
import com.rebuild.phoebe.simulator.auction.Candidate;
import com.rebuild.phoebe.simulator.auction.GspAuctionEngine;
import com.rebuild.phoebe.simulator.billing.BillingService;
import com.rebuild.phoebe.simulator.search.SearchSimulator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AuctionBillingTest extends TestSupport {
    @Autowired SearchSimulator search;
    @Autowired BillingService billing;
    @Autowired AccountService accountService;
    @Autowired MiddlePageService middlePageService;
    @Autowired StatService statService;
    @Autowired PlanMapper planMapper;
    @Autowired QuestionAnswerMapper qaMapper;

    private static Candidate cand(long qaId, long accountId, String eBid, double q) {
        Candidate c = new Candidate();
        c.setQaId(qaId);
        c.setAccountId(accountId);
        c.setEffectiveBid(new BigDecimal(eBid));
        c.setQuality(q);
        return c;
    }

    @Test
    void gspSecondPrice() {
        GspAuctionEngine engine = new GspAuctionEngine(new SimulatorProperties());
        List<Candidate> r = engine.run(List.of(cand(1, 1, "1.50", 1.0), cand(2, 2, "2.00", 1.0), cand(3, 2, "1.90", 1.0)), 1);
        assertThat(r.get(0).getQaId()).isEqualTo(2);
        assertThat(r.get(0).isWon()).isTrue();
        // 同账户只取最优一条 → 第二名是账户1 的 1.50，计费 1.51
        assertThat(r.get(0).getChargePrice()).isEqualByComparingTo("1.51");
        assertThat(r.get(1).getQaId()).isEqualTo(1);
        assertThat(r.get(1).getChargePrice()).isEqualByComparingTo("0.30");
        assertThat(r.get(2).getNote()).contains("同账户");
    }

    @Test
    void qualityAffectsRankAndPriceNeverExceedsBid() {
        GspAuctionEngine engine = new GspAuctionEngine(new SimulatorProperties());
        List<Candidate> r = engine.run(List.of(cand(1, 1, "1.00", 2.0), cand(2, 2, "1.80", 1.0)), 1);
        assertThat(r.get(0).getQaId()).isEqualTo(1);
        assertThat(r.get(0).getChargePrice()).isEqualByComparingTo("0.91");
        List<Candidate> single = engine.run(List.of(cand(1, 1, "0.30", 1.0)), 1);
        assertThat(single.get(0).getChargePrice()).isEqualByComparingTo("0.30");
    }

    private SearchSimulator.SearchResult run(String q, Device device) {
        SearchSimulator.SearchRequest req = new SearchSimulator.SearchRequest();
        req.setQuery(q);
        req.setDevice(device);
        req.setChannel(Channel.ZHIDAO);
        return search.search(req);
    }

    @Test
    void mobileRatioAppliedAndClickCharged() {
        SearchSimulator.SearchResult r = run("眼霜什么牌子好", Device.MOBILE);
        Candidate qa1 = r.getAuction().stream().filter(c -> c.getQaId() == 1L).findFirst().orElseThrow();
        // 单元出价 1.50 × 计划移动出价比例 1.20
        assertThat(qa1.getEffectiveBid()).isEqualByComparingTo("1.80");
        SearchSimulator.Item ad = r.getItems().stream().filter(i -> "AD".equals(i.getType())).findFirst().orElseThrow();

        BigDecimal before = accountService.get(1L).getBalance();
        Long winnerAccount = qaMapper.selectById(ad.getQaId()).getAccountId();
        BigDecimal winnerBefore = accountService.get(winnerAccount).getBalance();
        Map<String, Object> click = billing.clickEntry(ad.getImpressionId());
        BigDecimal charged = (BigDecimal) click.get("charged");
        assertThat(charged).isPositive();
        assertThat(accountService.get(winnerAccount).getBalance()).isEqualByComparingTo(winnerBefore.subtract(charged));
        // 重复点击不再计费；中间页内点击不计费
        assertThat((BigDecimal) billing.clickEntry(ad.getImpressionId()).get("charged")).isEqualByComparingTo("0");
        assertThat((BigDecimal) billing.clickInner(ad.getImpressionId(), "consult").get("charged")).isEqualByComparingTo("0");
        assertThat(before).isNotNull();
    }

    @Test
    void noEnabledMiddlePageMeansExcluded() {
        asAdvertiser(1);
        middlePageService.disable(1L);
        SearchSimulator.SearchResult r = run("眼霜什么牌子好", Device.PC);
        assertThat(r.getAuction()).noneMatch(c -> c.getAccountId() == 1L);
        assertThat(r.getExcluded()).anyMatch(e -> e.getQaId() == 1L && e.getReasons().contains("无启用且审核通过的中间页"));
    }

    @Test
    void budgetExhaustedMeansExcluded() {
        Plan p = planMapper.selectById(1L);
        p.setBudget(new BigDecimal("50"));
        planMapper.updateById(p);
        QuestionAnswer qa = qaMapper.selectById(1L);
        statService.addQa(qa, Device.PC, LocalDate.now(), 0, 10, new BigDecimal("60"));
        SearchSimulator.SearchResult r = run("眼霜什么牌子好", Device.PC);
        assertThat(r.getExcluded()).anyMatch(e -> e.getQaId() == 1L && e.getReasons().contains("今日预算已用完"));
    }

    @Test
    void pendingQaNotDelivered() {
        SearchSimulator.SearchResult r = run("眼霜的正确使用方法是什么", Device.PC);
        assertThat(r.getExcluded()).anyMatch(e -> e.getQaId() == 3L && e.getReasons().contains("问答待审核"));
    }
}
