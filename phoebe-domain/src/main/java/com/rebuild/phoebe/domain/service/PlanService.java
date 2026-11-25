package com.rebuild.phoebe.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.common.Evidence;
import com.rebuild.phoebe.common.EvidenceLevel;
import com.rebuild.phoebe.common.UserContext;
import com.rebuild.phoebe.domain.config.PhoebeRules;
import com.rebuild.phoebe.domain.dto.PlanRequest;
import com.rebuild.phoebe.domain.dto.PlanView;
import com.rebuild.phoebe.domain.entity.Account;
import com.rebuild.phoebe.domain.entity.Creative;
import com.rebuild.phoebe.domain.entity.MiddlePage;
import com.rebuild.phoebe.domain.entity.Plan;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.entity.Unit;
import com.rebuild.phoebe.domain.enums.RunStatus;
import com.rebuild.phoebe.domain.mapper.AccountMapper;
import com.rebuild.phoebe.domain.mapper.CreativeMapper;
import com.rebuild.phoebe.domain.mapper.MiddlePageMapper;
import com.rebuild.phoebe.domain.mapper.PlanMapper;
import com.rebuild.phoebe.domain.mapper.QuestionAnswerMapper;
import com.rebuild.phoebe.domain.mapper.UnitMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** 推广计划：创建、编辑、删除、启停、列表统计 [P:A]。 */
@Service
@RequiredArgsConstructor
public class PlanService {
    private final PlanMapper planMapper;
    private final UnitMapper unitMapper;
    private final QuestionAnswerMapper qaMapper;
    private final CreativeMapper creativeMapper;
    private final MiddlePageMapper middlePageMapper;
    private final AccountMapper accountMapper;
    private final PhoebeRules rules;
    private final Guard guard;
    private final EligibilityService eligibility;
    private final StatService statService;
    private final OpLogService opLog;

    public List<PlanView> list(Long accountId) {
        Account account = accountMapper.selectById(accountId);
        return planMapper.selectList(new LambdaQueryWrapper<Plan>()
                        .eq(Plan::getAccountId, accountId).orderByDesc(Plan::getId))
                .stream().map(p -> view(p, account)).collect(Collectors.toList());
    }

    public PlanView detail(Long id) {
        Plan p = guard.plan(id);
        return view(p, accountMapper.selectById(p.getAccountId()));
    }

    private PlanView view(Plan p, Account account) {
        PlanView v = new PlanView();
        v.setPlan(p);
        v.setUnitCount(unitMapper.selectCount(new LambdaQueryWrapper<Unit>().eq(Unit::getPlanId, p.getId())));
        v.setQaCount(qaMapper.selectCount(new LambdaQueryWrapper<QuestionAnswer>().eq(QuestionAnswer::getPlanId, p.getId())));
        v.setMiddlePageCount(middlePageMapper.selectCount(new LambdaQueryWrapper<MiddlePage>().eq(MiddlePage::getPlanId, p.getId())));
        MiddlePage enabled = eligibility.enabledValidPage(p.getId());
        v.setEnabledMiddlePageName(enabled == null ? null : enabled.getName());
        v.setTodayCost(statService.planCost(p.getId(), LocalDate.now()));
        v.setBlockers(eligibility.planBlockers(p, account));
        return v;
    }

    @Transactional
    @Evidence(level = EvidenceLevel.PUBLIC, source = "B", note = "一个账户下最多有100个计划")
    public Plan create(PlanRequest req) {
        Long accountId = UserContext.requireAccountId();
        long count = planMapper.selectCount(new LambdaQueryWrapper<Plan>().eq(Plan::getAccountId, accountId));
        if (count >= rules.getMaxPlansPerAccount()) {
            throw BizException.limit("每个账户最多 " + rules.getMaxPlansPerAccount() + " 个计划");
        }
        Plan p = new Plan();
        p.setAccountId(accountId);
        apply(p, req);
        p.setStatus(RunStatus.ACTIVE);
        planMapper.insert(p);
        opLog.log(accountId, "PLAN", p.getId(), "CREATE", req);
        return p;
    }

    @Transactional
    public Plan update(Long id, PlanRequest req) {
        Plan p = guard.plan(id);
        apply(p, req);
        planMapper.updateById(p);
        opLog.log(p.getAccountId(), "PLAN", id, "UPDATE", req);
        return p;
    }

    private void apply(Plan p, PlanRequest req) {
        String name = req.getName().trim();
        Long dup = planMapper.selectCount(new LambdaQueryWrapper<Plan>()
                .eq(Plan::getAccountId, p.getAccountId()).eq(Plan::getName, name)
                .ne(p.getId() != null, Plan::getId, p.getId()));
        if (dup > 0) {
            throw BizException.badRequest("计划名称已存在");
        }
        if (req.getBudget() != null && req.getBudget().compareTo(rules.getBudgetMin()) < 0) {
            throw BizException.badRequest("计划日预算不能低于 " + rules.getBudgetMin() + " 元（或选择不限预算）");
        }
        if (req.getMobileBidRatio().compareTo(rules.getMobileRatioMin()) < 0
                || req.getMobileBidRatio().compareTo(rules.getMobileRatioMax()) > 0) {
            throw BizException.badRequest("移动出价比例需在 " + rules.getMobileRatioMin() + "~" + rules.getMobileRatioMax() + " 之间");
        }
        p.setName(name);
        p.setBudget(req.getBudget());
        p.setMobileBidRatio(req.getMobileBidRatio());
        p.setRegions(req.getRegions() == null || req.getRegions().isEmpty() ? null
                : req.getRegions().stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().collect(Collectors.joining(",")));
        p.setMobilePhone(req.getMobilePhone() == null || req.getMobilePhone().isBlank() ? null : req.getMobilePhone().trim());
        p.setSchedule(TargetingRules.normalizeSchedule(req.getSchedule()));
        p.setExcludedIps(TargetingRules.normalizeIps(req.getExcludedIps(), rules.getMaxExcludedIpsPerPlan()));
    }

    @Transactional
    public Plan setStatus(Long id, RunStatus status) {
        Plan p = guard.plan(id);
        if (!Objects.equals(p.getStatus(), status)) {
            p.setStatus(status);
            planMapper.updateById(p);
            opLog.log(p.getAccountId(), "PLAN", id, status == RunStatus.ACTIVE ? "ENABLE" : "PAUSE", null);
        }
        return p;
    }

    /** 逻辑删除计划及其下单元、问答、创意、中间页 [I]。 */
    @Transactional
    public void delete(Long id) {
        Plan p = guard.plan(id);
        qaMapper.delete(new LambdaQueryWrapper<QuestionAnswer>().eq(QuestionAnswer::getPlanId, id));
        creativeMapper.delete(new LambdaQueryWrapper<Creative>().eq(Creative::getPlanId, id));
        unitMapper.delete(new LambdaQueryWrapper<Unit>().eq(Unit::getPlanId, id));
        middlePageMapper.delete(new LambdaQueryWrapper<MiddlePage>().eq(MiddlePage::getPlanId, id));
        planMapper.deleteById(id);
        opLog.log(p.getAccountId(), "PLAN", id, "DELETE", p.getName());
    }
}
