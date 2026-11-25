package com.rebuild.phoebe.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.common.PageResult;
import com.rebuild.phoebe.common.UserContext;
import com.rebuild.phoebe.domain.config.AuditProperties;
import com.rebuild.phoebe.domain.entity.AuditTask;
import com.rebuild.phoebe.domain.enums.AuditStatus;
import com.rebuild.phoebe.domain.enums.AuditTargetType;
import com.rebuild.phoebe.domain.mapper.AuditTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模拟审核（快照式）。提交时保存内容快照，审核结果回写到对应物料。
 * 真实产品：业审承诺 3 个工作日 [P:B]，可优审；此处为人工审核台 + 可选自动通过。
 */
@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditTaskMapper mapper;
    private final AuditProperties props;
    private final ContentChecker checker;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<AuditTargetHandler> handlers;
    private final OpLogService opLog;

    @Transactional
    public AuditTask submit(Long accountId, AuditTargetType type, Long targetId, int version,
                            String title, Object snapshot, List<String> preCheckWarnings) {
        // 同一对象的旧待审任务作废
        mapper.update(null, new LambdaUpdateWrapper<AuditTask>()
                .set(AuditTask::getStatus, AuditStatus.CANCELED)
                .eq(AuditTask::getTargetType, type)
                .eq(AuditTask::getTargetId, targetId)
                .eq(AuditTask::getStatus, AuditStatus.PENDING));

        String snapshotJson = toJson(snapshot);
        List<String> banned = checker.bannedWords(snapshotJson);

        AuditTask t = new AuditTask();
        t.setAccountId(accountId);
        t.setTargetType(type);
        t.setTargetId(targetId);
        t.setTargetVersion(version);
        t.setTitle(title != null && title.length() > 250 ? title.substring(0, 250) : title);
        t.setSnapshot(snapshotJson);
        t.setStatus(AuditStatus.PENDING);
        StringBuilder pre = new StringBuilder();
        if (!banned.isEmpty()) {
            pre.append("命中违禁词: ").append(String.join("、", banned)).append("；");
        }
        if (preCheckWarnings != null && !preCheckWarnings.isEmpty()) {
            pre.append(String.join("；", preCheckWarnings));
        }
        t.setPreCheckResult(pre.length() == 0 ? "机器预审通过" : truncate(pre.toString(), 500));
        mapper.insert(t);

        if (!banned.isEmpty()) {
            decide(t, false, "机器预审拒绝：含违禁词 " + String.join("、", banned), null);
        } else if (props.isAutoApprove()) {
            decide(t, true, "自动审核通过（模拟）", null);
        }
        return t;
    }

    @Transactional
    public AuditTask approve(Long taskId, String remark) {
        AuditTask t = pending(taskId);
        decide(t, true, remark, UserContext.userIdOrNull());
        return t;
    }

    @Transactional
    public AuditTask reject(Long taskId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw BizException.badRequest("拒绝时必须填写拒绝理由");
        }
        AuditTask t = pending(taskId);
        decide(t, false, reason, UserContext.userIdOrNull());
        return t;
    }

    private AuditTask pending(Long taskId) {
        AuditTask t = mapper.selectById(taskId);
        if (t == null) {
            throw BizException.notFound("审核任务");
        }
        if (t.getStatus() != AuditStatus.PENDING) {
            throw BizException.state("该审核任务已处理");
        }
        return t;
    }

    private void decide(AuditTask t, boolean approved, String reason, Long auditorId) {
        t.setStatus(approved ? AuditStatus.APPROVED : AuditStatus.REJECTED);
        t.setReason(reason);
        t.setAuditorId(auditorId);
        t.setAuditedAt(LocalDateTime.now());
        mapper.updateById(t);
        AuditTargetHandler h = handlers.orderedStream()
                .filter(x -> x.type() == t.getTargetType())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no audit handler for " + t.getTargetType()));
        if (approved) {
            h.onApproved(t);
        } else {
            h.onRejected(t, reason);
        }
        opLog.log(t.getAccountId(), "AUDIT", t.getId(), approved ? "APPROVE" : "REJECT",
                t.getTargetType() + "#" + t.getTargetId() + " " + (reason == null ? "" : reason));
    }

    public PageResult<AuditTask> page(AuditStatus status, AuditTargetType type, int pageNo, int size) {
        LambdaQueryWrapper<AuditTask> w = new LambdaQueryWrapper<AuditTask>()
                .eq(status != null, AuditTask::getStatus, status)
                .eq(type != null, AuditTask::getTargetType, type)
                .orderByDesc(AuditTask::getId);
        Page<AuditTask> p = mapper.selectPage(new Page<>(pageNo, size), w);
        return new PageResult<>(p.getTotal(), p.getRecords());
    }

    public AuditTask get(Long id) {
        AuditTask t = mapper.selectById(id);
        if (t == null) {
            throw BizException.notFound("审核任务");
        }
        return t;
    }

    private String toJson(Object o) {
        try {
            return o instanceof String s ? s : objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }
}
