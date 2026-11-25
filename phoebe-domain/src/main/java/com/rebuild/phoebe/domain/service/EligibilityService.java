package com.rebuild.phoebe.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rebuild.phoebe.common.Evidence;
import com.rebuild.phoebe.common.EvidenceLevel;
import com.rebuild.phoebe.domain.entity.Account;
import com.rebuild.phoebe.domain.entity.MiddlePage;
import com.rebuild.phoebe.domain.entity.Plan;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.entity.Unit;
import com.rebuild.phoebe.domain.enums.AccountStatus;
import com.rebuild.phoebe.domain.enums.AuditStatus;
import com.rebuild.phoebe.domain.enums.RunStatus;
import com.rebuild.phoebe.domain.mapper.AccountMapper;
import com.rebuild.phoebe.domain.mapper.MiddlePageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 投放资格判定。
 * <p>
 * [P:B] 前台展现必须有一条审核通过的问答和一套审核通过的中间页；
 * [P:A] 余额不足或预算过早消耗会导致竞价展现失败；
 * 其余（暂停开关、地域）[I]。
 */
@Service
@RequiredArgsConstructor
public class EligibilityService {
    private final AccountMapper accountMapper;
    private final MiddlePageMapper middlePageMapper;
    private final StatService statService;

    /** 计划层面的不可投放原因；空列表 = 计划可投放。 */
    @Evidence(level = EvidenceLevel.PUBLIC, source = "A,B")
    public List<String> planBlockers(Plan plan, Account account) {
        return planBlockers(plan, account, java.time.LocalDateTime.now());
    }

    /** @param time 判断投放时段用的时刻（模拟器可指定模拟时间） */
    public List<String> planBlockers(Plan plan, Account account, java.time.LocalDateTime time) {
        List<String> reasons = new ArrayList<>();
        if (account == null) {
            account = accountMapper.selectById(plan.getAccountId());
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            reasons.add("账户已停用");
        }
        if (account.getBalance() == null || account.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            reasons.add("账户余额不足");
        }
        if (plan.getStatus() != RunStatus.ACTIVE) {
            reasons.add("计划已暂停");
        }
        if (plan.getBudget() != null && statService.planCost(plan.getId(), LocalDate.now()).compareTo(plan.getBudget()) >= 0) {
            reasons.add("今日预算已用完");
        }
        if (!TargetingRules.inSchedule(plan, time)) {
            reasons.add("不在投放时段");
        }
        if (enabledValidPage(plan.getId()) == null) {
            reasons.add("无启用且审核通过的中间页");
        }
        return reasons;
    }

    public MiddlePage enabledValidPage(Long planId) {
        return middlePageMapper.selectOne(new LambdaQueryWrapper<MiddlePage>()
                .eq(MiddlePage::getPlanId, planId)
                .eq(MiddlePage::getEnabled, true)
                .isNotNull(MiddlePage::getOnlineVersionId)
                .last("LIMIT 1"));
    }

    /** 问答自身及所属单元的不可投放原因（不含计划层）。 */
    public List<String> qaBlockers(QuestionAnswer qa, Unit unit) {
        List<String> reasons = new ArrayList<>();
        if (qa.getAuditStatus() != AuditStatus.APPROVED) {
            reasons.add(qa.getAuditStatus() == AuditStatus.REJECTED ? "问答审核未通过" : "问答待审核");
        }
        if (qa.getStatus() != RunStatus.ACTIVE) {
            reasons.add("问答已暂停");
        }
        if (unit == null) {
            reasons.add("单元不存在");
        } else if (unit.getStatus() != RunStatus.ACTIVE) {
            reasons.add("单元已暂停");
        }
        return reasons;
    }

    public static boolean regionMatches(Plan plan, String region) {
        if (plan.getRegions() == null || plan.getRegions().isBlank() || region == null || region.isBlank()) {
            return true;
        }
        for (String r : plan.getRegions().split(",")) {
            if (r.trim().equals(region.trim())) {
                return true;
            }
        }
        return false;
    }
}
