package com.rebuild.phoebe.web;

import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.domain.entity.CptOrder;
import com.rebuild.phoebe.domain.entity.MiddlePage;
import com.rebuild.phoebe.domain.enums.Channel;
import com.rebuild.phoebe.domain.enums.Device;
import com.rebuild.phoebe.domain.mapper.CptOrderMapper;
import com.rebuild.phoebe.domain.mapper.MiddlePageMapper;
import com.rebuild.phoebe.domain.service.AccountService;
import com.rebuild.phoebe.simulator.billing.BillingService;
import com.rebuild.phoebe.simulator.cpt.CptService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CPT 包词 [C]：同词 CPT 优先于 CPC、按天预付、同词同日独占、CPT 占第一位 CPC 顺延、CPT 点击不扣费。
 * 演示数据：问答 1/2 属于账户1(青禾)，问答 6 属于账户2(悦颜)，都能匹配“眼霜什么牌子好”。
 */
@SpringBootTest
@Transactional
class CptTest extends TestSupport {
    @Autowired CptService cpt;
    @Autowired CptOrderMapper orderMapper;
    @Autowired SearchSimulator search;
    @Autowired BillingService billing;
    @Autowired AccountService accountService;
    @Autowired MiddlePageMapper middlePageMapper;

    private final LocalDate today = LocalDate.now();

    private SearchSimulator.SearchResult run() {
        SearchSimulator.SearchRequest r = new SearchSimulator.SearchRequest();
        r.setQuery("眼霜什么牌子好");
        r.setDevice(Device.MOBILE);
        r.setChannel(Channel.ZHIDAO);
        return search.search(r);
    }

    @Test
    void quotePricesByDaysAndDetectsConflicts() {
        asAdvertiser(2);
        CptService.Quote q = cpt.quote("眼霜", today, today.plusDays(2));
        assertThat(q.getDays()).isEqualTo(3);
        assertThat(q.getTotal()).isEqualByComparingTo(q.getDailyPrice().multiply(BigDecimal.valueOf(3)));
        assertThat(q.getDailyPrice()).isGreaterThan(new BigDecimal("100")); // 眼霜相关问题检索量高，价格高于基础价
        assertThat(q.isAvailable()).isTrue();
        assertThatThrownBy(() -> cpt.quote("眼霜", today.minusDays(1), today)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> cpt.quote("眼", today, today)).hasMessageContaining("包断词");
        assertThatThrownBy(() -> cpt.quote("眼霜", today, today.plusDays(100))).hasMessageContaining("最长");
    }

    @Test
    void createChargesPrepaidAndIsExclusivePerKeywordPerDay() {
        asAdvertiser(2);
        BigDecimal before = accountService.get(2L).getBalance();
        CptOrder o = cpt.create(6L, "眼霜", today, today.plusDays(1));
        assertThat(o.getPhase()).isEqualTo("投放中");
        assertThat(accountService.get(2L).getBalance()).isEqualByComparingTo(before.subtract(o.getTotalPrice()));

        // 其他账户同词重叠日期：不能再卖；报价显示冲突（不透露是谁）
        asAdvertiser(1);
        CptService.Quote q = cpt.quote("眼霜", today.plusDays(1), today.plusDays(3));
        assertThat(q.isAvailable()).isFalse();
        assertThat(q.getConflicts()).hasSize(1);
        assertThat(q.getConflicts().get(0).get("mine")).isEqualTo(false);
        assertThatThrownBy(() -> cpt.create(1L, "眼霜", today.plusDays(1), today.plusDays(3))).hasMessageContaining("已被包断");
        // 归一化后相同的词也冲突；不重叠的日期可以买
        assertThatThrownBy(() -> cpt.create(1L, " 眼霜！", today, today)).hasMessageContaining("已被包断");
        assertThat(cpt.create(1L, "眼霜", today.plusDays(2), today.plusDays(3)).getPhase()).isEqualTo("待开始");
    }

    @Test
    void insufficientBalanceRejected() {
        asAdvertiser(2);
        accountService.deduct(2L, accountService.get(2L).getBalance().subtract(new BigDecimal("10")));
        assertThatThrownBy(() -> cpt.create(6L, "眼霜", today, today)).hasMessageContaining("余额不足");
        assertThat(orderMapper.selectCount(null)).isZero();
    }

    @Test
    void cancelRefundsByRule() {
        asAdvertiser(2);
        CptOrder future = cpt.create(6L, "眼霜牌子", today.plusDays(5), today.plusDays(6));
        BigDecimal before = accountService.get(2L).getBalance();
        CptOrder c1 = cpt.cancel(future.getId());
        assertThat(c1.getRefundAmount()).isEqualByComparingTo(future.getTotalPrice()); // 开始前：全额
        assertThat(accountService.get(2L).getBalance()).isEqualByComparingTo(before.add(future.getTotalPrice()));

        CptOrder running = cpt.create(6L, "眼霜", today, today.plusDays(2)); // 3 天，今天已开始
        CptOrder c2 = cpt.cancel(running.getId());
        assertThat(c2.getRefundAmount()).isEqualByComparingTo(running.getDailyPrice().multiply(BigDecimal.valueOf(2))); // 退明后两天
        assertThat(c2.getPhase()).isEqualTo("已取消");
        assertThatThrownBy(() -> cpt.cancel(running.getId())).hasMessageContaining("已取消");
        // 取消后排期释放，别人可以买
        asAdvertiser(1);
        assertThat(cpt.quote("眼霜", today, today.plusDays(2)).isAvailable()).isTrue();
    }

    @Test
    void cptTakesFirstSlotAndCpcFillsTheRest() {
        // 无 CPT：CPC 竞价，青禾(移动 1.8) 第一，悦颜(1.3) 第二
        SearchSimulator.SearchResult before = run();
        assertThat(before.getCpt()).isNull();
        List<SearchSimulator.Item> adsBefore = before.getItems().stream().filter(i -> "AD".equals(i.getType())).toList();
        assertThat(adsBefore).extracting(SearchSimulator.Item::getQaId).containsExactly(1L, 6L);

        // 悦颜包下“眼霜”：CPT 占第一位，CPC 顺延（悦颜自己的 CPC 不再重复出现）
        asAdvertiser(2);
        CptOrder o = cpt.create(6L, "眼霜", today, today);
        clearContext();
        SearchSimulator.SearchResult r = run();
        List<SearchSimulator.Item> ads = r.getItems().stream().filter(i -> "AD".equals(i.getType())).toList();
        assertThat(ads).extracting(SearchSimulator.Item::getBillingType).containsExactly("CPT", "CPC");
        assertThat(ads.get(0).getQaId()).isEqualTo(6L);
        assertThat(ads.get(1).getQaId()).isEqualTo(1L);
        assertThat(r.getCpt()).containsEntry("orderId", o.getId());
        assertThat(r.getAuction()).anyMatch(c -> c.getQaId() == 6L && !c.isWon() && c.getNote().contains("CPT"));

        // CPT 点击不扣费；CPC 点击照常扣费
        BigDecimal b2 = accountService.get(2L).getBalance();
        Map<String, Object> click = billing.clickEntry(ads.get(0).getImpressionId());
        assertThat((BigDecimal) click.get("charged")).isEqualByComparingTo("0");
        assertThat(click.get("billingType")).isEqualTo("CPT");
        assertThat(accountService.get(2L).getBalance()).isEqualByComparingTo(b2);
        assertThat((BigDecimal) billing.clickEntry(ads.get(1).getImpressionId()).get("charged")).isPositive();

        // CPT 报告单独统计展现、点击
        asAdvertiser(2);
        Map<String, Object> row = cpt.report(2L, today, today).get(0);
        assertThat(row.get("impressions")).isEqualTo(1L);
        assertThat(row.get("clicks")).isEqualTo(1L);
    }

    @Test
    void longestKeywordWinsAndIneligibleCptFallsBackToCpc() {
        asAdvertiser(2);
        cpt.create(6L, "眼霜", today, today);
        asAdvertiser(1);
        cpt.create(1L, "眼霜什么牌子", today, today); // 更具体的词
        clearContext();
        SearchSimulator.SearchResult r = run();
        assertThat(r.getCpt().get("keyword")).isEqualTo("眼霜什么牌子");
        assertThat(r.getItems().stream().filter(i -> "CPT".equals(i.getBillingType())).findFirst().orElseThrow().getQaId()).isEqualTo(1L);

        // 青禾停用中间页 → 其 CPT 不具备资格，落到下一个 CPT（悦颜“眼霜”）
        MiddlePage mp = middlePageMapper.selectById(1L);
        mp.setEnabled(false);
        middlePageMapper.updateById(mp);
        SearchSimulator.SearchResult r2 = run();
        assertThat(r2.getCpt().get("keyword")).isEqualTo("眼霜");
        assertThat(r2.getExcluded()).anyMatch(e -> "CPT".equals(e.getMatchType()) && e.getReasons().get(0).contains("不具备展现资格"));
    }
}
