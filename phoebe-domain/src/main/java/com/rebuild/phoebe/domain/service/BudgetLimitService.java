package com.rebuild.phoebe.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rebuild.phoebe.common.Evidence;
import com.rebuild.phoebe.common.EvidenceLevel;
import com.rebuild.phoebe.common.PageResult;
import com.rebuild.phoebe.domain.entity.BudgetLimitLog;
import com.rebuild.phoebe.domain.entity.Plan;
import com.rebuild.phoebe.domain.mapper.BudgetLimitLogMapper;
import com.rebuild.phoebe.domain.mapper.PlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 预算达限记录 [P:A 截图：历史操作记录页的“预算达限记录”tab]。
 * 计划当日消费首次达到日预算时记一条（每计划每天一条）[I]。
 */
@Service
@RequiredArgsConstructor
@Evidence(level = EvidenceLevel.INFERRED, source = "A", note = "tab 存在为公开资料；记录时机与字段为推测")
public class BudgetLimitService {
    private final BudgetLimitLogMapper mapper;
    private final PlanMapper planMapper;
    private final StatService statService;

    /** 在计费后调用：若计划当日消费已达预算且今天尚未记录，则记录一条。返回是否新记录。 */
    public boolean checkAndRecord(Long planId) {
        Plan plan = planMapper.selectById(planId);
        if (plan == null || plan.getBudget() == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        BigDecimal cost = statService.planCost(planId, today);
        if (cost.compareTo(plan.getBudget()) < 0) {
            return false;
        }
        boolean exists = mapper.selectCount(new LambdaQueryWrapper<BudgetLimitLog>()
                .eq(BudgetLimitLog::getPlanId, planId).eq(BudgetLimitLog::getLimitDate, today)) > 0;
        if (exists) {
            return false;
        }
        BudgetLimitLog log = new BudgetLimitLog();
        log.setAccountId(plan.getAccountId());
        log.setPlanId(planId);
        log.setPlanName(plan.getName());
        log.setBudget(plan.getBudget());
        log.setCost(cost);
        log.setLimitDate(today);
        mapper.insert(log);
        return true;
    }

    public PageResult<BudgetLimitLog> page(Long accountId, LocalDate from, LocalDate to, int pageNo, int size) {
        LocalDate f = from == null ? LocalDate.now().minusDays(6) : from;
        LocalDate t = to == null ? LocalDate.now() : to;
        Page<BudgetLimitLog> p = mapper.selectPage(new Page<>(pageNo, size), new LambdaQueryWrapper<BudgetLimitLog>()
                .eq(accountId != null, BudgetLimitLog::getAccountId, accountId)
                .ge(BudgetLimitLog::getLimitDate, f).le(BudgetLimitLog::getLimitDate, t)
                .orderByDesc(BudgetLimitLog::getId));
        return new PageResult<>(p.getTotal(), p.getRecords());
    }
}
