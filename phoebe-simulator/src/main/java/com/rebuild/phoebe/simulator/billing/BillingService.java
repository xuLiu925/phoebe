package com.rebuild.phoebe.simulator.billing;

import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.common.Evidence;
import com.rebuild.phoebe.common.EvidenceLevel;
import com.rebuild.phoebe.domain.entity.ClickLog;
import com.rebuild.phoebe.domain.entity.ImpressionLog;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.mapper.ClickLogMapper;
import com.rebuild.phoebe.domain.mapper.ImpressionLogMapper;
import com.rebuild.phoebe.domain.mapper.QuestionAnswerMapper;
import com.rebuild.phoebe.domain.service.AccountService;
import com.rebuild.phoebe.domain.service.MiddlePageService;
import com.rebuild.phoebe.domain.service.StatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模拟 CPC 计费。
 * <p>
 * [P:B] 只收取从广告点击到商业问答详情页（中间页）的点击费用；中间页点击不收费。
 * [I] 同一次展现只计费一次；余额不足时最多扣至 0。
 */
@Service
@RequiredArgsConstructor
public class BillingService {
    private final ImpressionLogMapper impressionMapper;
    private final ClickLogMapper clickMapper;
    private final QuestionAnswerMapper qaMapper;
    private final AccountService accountService;
    private final StatService statService;
    private final MiddlePageService middlePageService;
    private final com.rebuild.phoebe.domain.service.BudgetLimitService budgetLimitService;

    @Transactional
    @Evidence(level = EvidenceLevel.PUBLIC, source = "B,C", note = "问答入口点击收费")
    public Map<String, Object> clickEntry(Long impressionId) {
        ImpressionLog imp = impressionMapper.selectById(impressionId);
        if (imp == null) {
            throw BizException.notFound("展现记录");
        }
        BigDecimal charged = BigDecimal.ZERO;
        boolean duplicate = Boolean.TRUE.equals(imp.getClicked());
        boolean cpt = "CPT".equals(imp.getBillingType());
        if (!duplicate && cpt) {
            // [C] CPT 已按天预付，点击只记录不扣费；不计入 CPC 日报（CPT 单独统计）
            imp.setClicked(true);
            impressionMapper.updateById(imp);
            ClickLog log = new ClickLog();
            log.setImpressionId(imp.getId());
            log.setAccountId(imp.getAccountId());
            log.setPlanId(imp.getPlanId());
            log.setUnitId(imp.getUnitId());
            log.setQaId(imp.getQaId());
            log.setMiddlePageId(imp.getMiddlePageId());
            log.setDevice(imp.getDevice());
            log.setChannel(imp.getChannel());
            log.setClickType(ClickLog.QA_ENTRY);
            log.setTarget("CPT");
            log.setChargePrice(BigDecimal.ZERO);
            clickMapper.insert(log);
        } else if (!duplicate) {
            charged = accountService.deduct(imp.getAccountId(), imp.getChargePrice());
            imp.setClicked(true);
            impressionMapper.updateById(imp);
            ClickLog log = new ClickLog();
            log.setImpressionId(imp.getId());
            log.setAccountId(imp.getAccountId());
            log.setPlanId(imp.getPlanId());
            log.setUnitId(imp.getUnitId());
            log.setQaId(imp.getQaId());
            log.setMiddlePageId(imp.getMiddlePageId());
            log.setDevice(imp.getDevice());
            log.setChannel(imp.getChannel());
            log.setClickType(ClickLog.QA_ENTRY);
            log.setChargePrice(charged);
            clickMapper.insert(log);
            QuestionAnswer qa = qaMapper.selectById(imp.getQaId());
            if (qa != null) {
                statService.addQa(qa, imp.getDevice(), imp.getChannel(), imp.getRegion(), LocalDate.now(), 0, 1, charged);
            }
            budgetLimitService.checkAndRecord(imp.getPlanId());
        }
        // 进入中间页：计一次中间页展现
        statService.addMiddlePage(imp.getAccountId(), imp.getPlanId(), imp.getMiddlePageId(), imp.getDevice(), imp.getChannel(), imp.getRegion(), LocalDate.now(), 1, 0);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("impressionId", impressionId);
        r.put("charged", charged);
        r.put("duplicateClick", duplicate);
        r.put("billingType", cpt ? "CPT" : "CPC");
        r.put("bidPrice", imp.getEffectiveBid());
        r.put("middlePage", middlePageService.publicPreview(imp.getMiddlePageId(), imp.getDevice()));
        r.put("balance", accountService.get(imp.getAccountId()).getBalance());
        return r;
    }

    @Transactional
    @Evidence(level = EvidenceLevel.PUBLIC, source = "B,C", note = "中间页内点击不收费")
    public Map<String, Object> clickInner(Long impressionId, String target) {
        ImpressionLog imp = impressionMapper.selectById(impressionId);
        if (imp == null) {
            throw BizException.notFound("展现记录");
        }
        ClickLog log = new ClickLog();
        log.setImpressionId(imp.getId());
        log.setAccountId(imp.getAccountId());
        log.setPlanId(imp.getPlanId());
        log.setUnitId(imp.getUnitId());
        log.setQaId(imp.getQaId());
        log.setMiddlePageId(imp.getMiddlePageId());
        log.setDevice(imp.getDevice());
        log.setChannel(imp.getChannel());
        log.setClickType(ClickLog.MIDDLE_PAGE_INNER);
        log.setTarget(target);
        log.setChargePrice(BigDecimal.ZERO);
        clickMapper.insert(log);
        statService.addMiddlePage(imp.getAccountId(), imp.getPlanId(), imp.getMiddlePageId(), imp.getDevice(), imp.getChannel(), imp.getRegion(), LocalDate.now(), 0, 1);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("charged", BigDecimal.ZERO);
        r.put("target", target);
        return r;
    }
}
