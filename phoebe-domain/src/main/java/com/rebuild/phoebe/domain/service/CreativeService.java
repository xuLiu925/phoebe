package com.rebuild.phoebe.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.domain.config.PhoebeRules;
import com.rebuild.phoebe.domain.dto.CreativeRequest;
import com.rebuild.phoebe.domain.entity.Account;
import com.rebuild.phoebe.domain.entity.AuditTask;
import com.rebuild.phoebe.domain.entity.Creative;
import com.rebuild.phoebe.domain.entity.CreativeQaRel;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.entity.Unit;
import com.rebuild.phoebe.domain.enums.AuditStatus;
import com.rebuild.phoebe.domain.enums.AuditTargetType;
import com.rebuild.phoebe.domain.enums.RunStatus;
import com.rebuild.phoebe.domain.mapper.AccountMapper;
import com.rebuild.phoebe.domain.mapper.CreativeMapper;
import com.rebuild.phoebe.domain.mapper.CreativeQaRelMapper;
import com.rebuild.phoebe.domain.mapper.QuestionAnswerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 高级创意管理。
 * <p>
 * 公开资料确认存在“高级创意（产品图+描述，华表位）”[P:A]；Phoebe 内部创意模块的字段与职责 [U]。
 * 不假定所有问答都必须有创意：创意与问答为可选多对多关联，可整体停用本模块。
 */
@Service
@RequiredArgsConstructor
public class CreativeService implements AuditTargetHandler {
    private final CreativeMapper mapper;
    private final CreativeQaRelMapper relMapper;
    private final QuestionAnswerMapper qaMapper;
    private final AccountMapper accountMapper;
    private final PhoebeRules rules;
    private final Guard guard;
    private final ContentChecker checker;
    private final AuditService auditService;
    private final OpLogService opLog;

    public List<Creative> list(Long unitId) {
        guard.unit(unitId);
        List<Creative> list = mapper.selectList(new LambdaQueryWrapper<Creative>().eq(Creative::getUnitId, unitId).orderByDesc(Creative::getId));
        list.forEach(c -> c.setQaIds(qaIds(c.getId())));
        return list;
    }

    public List<Creative> listByAccount(Long accountId, Long planId, Long unitId) {
        List<Creative> list = mapper.selectList(new LambdaQueryWrapper<Creative>()
                .eq(Creative::getAccountId, accountId)
                .eq(planId != null, Creative::getPlanId, planId)
                .eq(unitId != null, Creative::getUnitId, unitId)
                .orderByDesc(Creative::getId));
        list.forEach(c -> c.setQaIds(qaIds(c.getId())));
        return list;
    }

    public Creative get(Long id) {
        Creative c = guard.creative(id);
        c.setQaIds(qaIds(id));
        return c;
    }

    private List<Long> qaIds(Long creativeId) {
        return relMapper.selectList(new LambdaQueryWrapper<CreativeQaRel>().eq(CreativeQaRel::getCreativeId, creativeId))
                .stream().map(CreativeQaRel::getQaId).collect(Collectors.toList());
    }

    @Transactional
    public Creative create(Long unitId, CreativeRequest req) {
        Unit unit = guard.unit(unitId);
        validate(req);
        Creative c = new Creative();
        c.setAccountId(unit.getAccountId());
        c.setPlanId(unit.getPlanId());
        c.setUnitId(unitId);
        c.setTitle(req.getTitle().trim());
        c.setDescription(req.getDescription().trim());
        c.setImageUrls(req.getImageUrls() == null ? new ArrayList<>() : req.getImageUrls());
        c.setTargetUrl(req.getTargetUrl());
        c.setAuditStatus(AuditStatus.PENDING);
        c.setStatus(RunStatus.ACTIVE);
        c.setAuditVersion(1);
        mapper.insert(c);
        replaceQas(c, req.getQaIds());
        submitAudit(c);
        opLog.log(c.getAccountId(), "CREATIVE", c.getId(), "CREATE", req);
        return get(c.getId());
    }

    @Transactional
    public Creative update(Long id, CreativeRequest req) {
        Creative c = guard.creative(id);
        validate(req);
        List<String> images = req.getImageUrls() == null ? new ArrayList<>() : req.getImageUrls();
        boolean changed = !Objects.equals(c.getTitle(), req.getTitle().trim())
                || !Objects.equals(c.getDescription(), req.getDescription().trim())
                || !Objects.equals(c.getImageUrls(), images)
                || !Objects.equals(c.getTargetUrl(), req.getTargetUrl());
        if (changed) {
            c.setTitle(req.getTitle().trim());
            c.setDescription(req.getDescription().trim());
            c.setImageUrls(images);
            c.setTargetUrl(req.getTargetUrl());
            c.setAuditVersion(c.getAuditVersion() + 1);
            c.setAuditStatus(AuditStatus.PENDING);
            c.setRejectReason(null);
            mapper.updateById(c);
            submitAudit(c);
        }
        if (req.getQaIds() != null) {
            replaceQas(c, req.getQaIds());
        }
        opLog.log(c.getAccountId(), "CREATIVE", id, "UPDATE", req);
        return get(id);
    }

    @Transactional
    public Creative setQas(Long id, List<Long> qaIds) {
        Creative c = guard.creative(id);
        replaceQas(c, qaIds);
        opLog.log(c.getAccountId(), "CREATIVE", id, "BIND_QA", qaIds);
        return get(id);
    }

    private void replaceQas(Creative c, List<Long> qaIds) {
        relMapper.delete(new LambdaQueryWrapper<CreativeQaRel>().eq(CreativeQaRel::getCreativeId, c.getId()));
        if (qaIds == null) {
            return;
        }
        for (Long qaId : new LinkedHashSet<>(qaIds)) {
            QuestionAnswer qa = qaMapper.selectById(qaId);
            if (qa == null || !Objects.equals(qa.getUnitId(), c.getUnitId())) {
                throw BizException.badRequest("只能关联同一单元下的问答：" + qaId);
            }
            CreativeQaRel rel = new CreativeQaRel();
            rel.setCreativeId(c.getId());
            rel.setQaId(qaId);
            relMapper.insert(rel);
        }
    }

    @Transactional
    public Creative setStatus(Long id, RunStatus status) {
        Creative c = guard.creative(id);
        c.setStatus(status);
        mapper.updateById(c);
        opLog.log(c.getAccountId(), "CREATIVE", id, status.name(), null);
        return c;
    }

    @Transactional
    public void delete(Long id) {
        Creative c = guard.creative(id);
        relMapper.delete(new LambdaQueryWrapper<CreativeQaRel>().eq(CreativeQaRel::getCreativeId, id));
        mapper.deleteById(id);
        opLog.log(c.getAccountId(), "CREATIVE", id, "DELETE", c.getTitle());
    }

    private void validate(CreativeRequest req) {
        checker.checkLength(req.getTitle(), "创意标题", 2, rules.getCreativeTitleMaxChars());
        checker.checkLength(req.getDescription(), "创意描述", 2, rules.getCreativeDescMaxChars());
    }

    private void submitAudit(Creative c) {
        Account account = accountMapper.selectById(c.getAccountId());
        List<String> urls = new ArrayList<>();
        urls.add(c.getTargetUrl());
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("title", c.getTitle());
        snap.put("description", c.getDescription());
        snap.put("imageUrls", c.getImageUrls());
        snap.put("targetUrl", c.getTargetUrl());
        auditService.submit(c.getAccountId(), AuditTargetType.CREATIVE, c.getId(), c.getAuditVersion(), c.getTitle(), snap,
                checker.domainWarnings(account == null ? null : account.getWebsite(), urls));
    }

    @Override
    public AuditTargetType type() {
        return AuditTargetType.CREATIVE;
    }

    @Override
    public void onApproved(AuditTask task) {
        Creative c = mapper.selectById(task.getTargetId());
        if (c != null && Objects.equals(c.getAuditVersion(), task.getTargetVersion())) {
            c.setAuditStatus(AuditStatus.APPROVED);
            c.setRejectReason(null);
            mapper.updateById(c);
        }
    }

    @Override
    public void onRejected(AuditTask task, String reason) {
        Creative c = mapper.selectById(task.getTargetId());
        if (c != null && Objects.equals(c.getAuditVersion(), task.getTargetVersion())) {
            c.setAuditStatus(AuditStatus.REJECTED);
            c.setRejectReason(reason);
            mapper.updateById(c);
        }
    }
}
