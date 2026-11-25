package com.rebuild.phoebe.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.common.Evidence;
import com.rebuild.phoebe.common.EvidenceLevel;
import com.rebuild.phoebe.domain.config.PhoebeRules;
import com.rebuild.phoebe.domain.dto.UnitRequest;
import com.rebuild.phoebe.domain.dto.UnitView;
import com.rebuild.phoebe.domain.entity.Creative;
import com.rebuild.phoebe.domain.entity.Plan;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.entity.SubscribeWord;
import com.rebuild.phoebe.domain.entity.Unit;
import com.rebuild.phoebe.domain.enums.AuditStatus;
import com.rebuild.phoebe.domain.enums.RunStatus;
import com.rebuild.phoebe.domain.mapper.CreativeMapper;
import com.rebuild.phoebe.domain.mapper.PlanMapper;
import com.rebuild.phoebe.domain.mapper.QuestionAnswerMapper;
import com.rebuild.phoebe.domain.mapper.SubscribeWordMapper;
import com.rebuild.phoebe.domain.mapper.UnitMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 推广单元：名称、出价、订阅词 [P:A]。 */
@Service
@RequiredArgsConstructor
public class UnitService {
    private final UnitMapper unitMapper;
    private final PlanMapper planMapper;
    private final QuestionAnswerMapper qaMapper;
    private final CreativeMapper creativeMapper;
    private final SubscribeWordMapper wordMapper;
    private final PhoebeRules rules;
    private final Guard guard;
    private final OpLogService opLog;

    public List<UnitView> listByPlan(Long planId) {
        guard.plan(planId);
        return views(unitMapper.selectList(new LambdaQueryWrapper<Unit>().eq(Unit::getPlanId, planId).orderByDesc(Unit::getId)));
    }

    public List<UnitView> listByAccount(Long accountId) {
        return views(unitMapper.selectList(new LambdaQueryWrapper<Unit>().eq(Unit::getAccountId, accountId).orderByDesc(Unit::getId)));
    }

    public UnitView detail(Long id) {
        return views(List.of(guard.unit(id))).get(0);
    }

    private List<UnitView> views(List<Unit> units) {
        if (units.isEmpty()) {
            return List.of();
        }
        Set<Long> planIds = units.stream().map(Unit::getPlanId).collect(Collectors.toSet());
        Map<Long, String> planNames = planMapper.selectBatchIds(planIds).stream().collect(Collectors.toMap(Plan::getId, Plan::getName));
        return units.stream().map(u -> {
            UnitView v = new UnitView();
            v.setUnit(u);
            v.setPlanName(planNames.get(u.getPlanId()));
            v.setQaCount(qaMapper.selectCount(new LambdaQueryWrapper<QuestionAnswer>().eq(QuestionAnswer::getUnitId, u.getId())));
            v.setApprovedQaCount(qaMapper.selectCount(new LambdaQueryWrapper<QuestionAnswer>()
                    .eq(QuestionAnswer::getUnitId, u.getId()).eq(QuestionAnswer::getAuditStatus, AuditStatus.APPROVED)));
            v.setCreativeCount(creativeMapper.selectCount(new LambdaQueryWrapper<Creative>().eq(Creative::getUnitId, u.getId())));
            v.setSubscribeWords(subscribeWords(u.getId()));
            return v;
        }).collect(Collectors.toList());
    }

    public List<String> subscribeWords(Long unitId) {
        return wordMapper.selectList(new LambdaQueryWrapper<SubscribeWord>().eq(SubscribeWord::getUnitId, unitId).orderByAsc(SubscribeWord::getId))
                .stream().map(SubscribeWord::getWord).collect(Collectors.toList());
    }

    public Map<Long, List<String>> subscribeWordsByUnits(Collection<Long> unitIds) {
        if (unitIds.isEmpty()) {
            return Map.of();
        }
        return wordMapper.selectList(new LambdaQueryWrapper<SubscribeWord>().in(SubscribeWord::getUnitId, unitIds))
                .stream().collect(Collectors.groupingBy(SubscribeWord::getUnitId,
                        Collectors.mapping(SubscribeWord::getWord, Collectors.toList())));
    }

    @Transactional
    @Evidence(level = EvidenceLevel.PUBLIC, source = "B", note = "一个计划下最多有100个单元")
    public Unit create(Long planId, UnitRequest req) {
        Plan plan = guard.plan(planId);
        long count = unitMapper.selectCount(new LambdaQueryWrapper<Unit>().eq(Unit::getPlanId, planId));
        if (count >= rules.getMaxUnitsPerPlan()) {
            throw BizException.limit("每个计划最多 " + rules.getMaxUnitsPerPlan() + " 个单元");
        }
        Unit u = new Unit();
        u.setAccountId(plan.getAccountId());
        u.setPlanId(planId);
        apply(u, req);
        u.setStatus(RunStatus.ACTIVE);
        unitMapper.insert(u);
        if (req.getSubscribeWords() != null) {
            replaceWords(u, req.getSubscribeWords());
        }
        opLog.log(u.getAccountId(), "UNIT", u.getId(), "CREATE", req);
        return u;
    }

    @Transactional
    public Unit update(Long id, UnitRequest req) {
        Unit u = guard.unit(id);
        apply(u, req);
        unitMapper.updateById(u);
        if (req.getSubscribeWords() != null) {
            replaceWords(u, req.getSubscribeWords());
        }
        opLog.log(u.getAccountId(), "UNIT", id, "UPDATE", req);
        return u;
    }

    private void apply(Unit u, UnitRequest req) {
        String name = req.getName().trim();
        long dup = unitMapper.selectCount(new LambdaQueryWrapper<Unit>()
                .eq(Unit::getPlanId, u.getPlanId()).eq(Unit::getName, name).ne(u.getId() != null, Unit::getId, u.getId()));
        if (dup > 0) {
            throw BizException.badRequest("同一计划下单元名称已存在");
        }
        checkBid(req.getBid(), rules);
        u.setName(name);
        u.setBid(req.getBid());
    }

    static void checkBid(BigDecimal bid, PhoebeRules rules) {
        if (bid == null) {
            return;
        }
        if (bid.compareTo(rules.getBidMin()) < 0 || bid.compareTo(rules.getBidMax()) > 0) {
            throw BizException.badRequest("出价需在 " + rules.getBidMin() + "~" + rules.getBidMax() + " 元之间");
        }
    }

    @Transactional
    public List<String> setSubscribeWords(Long id, List<String> words) {
        Unit u = guard.unit(id);
        replaceWords(u, words);
        opLog.log(u.getAccountId(), "UNIT", id, "SUBSCRIBE_WORDS", words);
        return subscribeWords(id);
    }

    private void replaceWords(Unit u, List<String> words) {
        Set<String> clean = new LinkedHashSet<>();
        for (String w : words) {
            if (w != null && !w.isBlank()) {
                clean.add(w.trim());
            }
        }
        if (clean.size() > rules.getMaxSubscribeWordsPerUnit()) {
            throw BizException.limit("每个单元最多 " + rules.getMaxSubscribeWordsPerUnit() + " 个订阅词");
        }
        wordMapper.delete(new LambdaQueryWrapper<SubscribeWord>().eq(SubscribeWord::getUnitId, u.getId()));
        for (String w : clean) {
            SubscribeWord sw = new SubscribeWord();
            sw.setAccountId(u.getAccountId());
            sw.setUnitId(u.getId());
            sw.setWord(w);
            wordMapper.insert(sw);
        }
    }

    @Transactional
    public Unit setStatus(Long id, RunStatus status) {
        Unit u = guard.unit(id);
        u.setStatus(status);
        unitMapper.updateById(u);
        opLog.log(u.getAccountId(), "UNIT", id, status == RunStatus.ACTIVE ? "ENABLE" : "PAUSE", null);
        return u;
    }

    @Transactional
    public void delete(Long id) {
        Unit u = guard.unit(id);
        qaMapper.delete(new LambdaQueryWrapper<QuestionAnswer>().eq(QuestionAnswer::getUnitId, id));
        creativeMapper.delete(new LambdaQueryWrapper<Creative>().eq(Creative::getUnitId, id));
        wordMapper.delete(new LambdaQueryWrapper<SubscribeWord>().eq(SubscribeWord::getUnitId, id));
        unitMapper.deleteById(id);
        opLog.log(u.getAccountId(), "UNIT", id, "DELETE", u.getName());
    }
}
