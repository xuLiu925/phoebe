package com.rebuild.phoebe.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rebuild.phoebe.common.PageResult;
import com.rebuild.phoebe.common.UserContext;
import com.rebuild.phoebe.domain.entity.OperationLog;
import com.rebuild.phoebe.domain.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/** 历史操作记录 [P:A]：默认最近一周，可筛选时间、下载。 */
@Service
@RequiredArgsConstructor
public class OpLogService {
    private final OperationLogMapper mapper;
    private final ObjectMapper objectMapper;

    public void log(Long accountId, String targetType, Long targetId, String action, Object detail) {
        OperationLog log = new OperationLog();
        UserContext.CurrentUser u = UserContext.get();
        log.setAccountId(accountId);
        log.setUserId(u == null ? null : u.userId());
        log.setUsername(u == null ? "system" : u.username());
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setAction(action);
        log.setDetail(toJson(detail));
        mapper.insert(log);
    }

    public PageResult<OperationLog> page(Long accountId, LocalDate from, LocalDate to, String targetType, int pageNo, int size) {
        LocalDate f = from == null ? LocalDate.now().minusDays(6) : from;
        LocalDate t = to == null ? LocalDate.now() : to;
        LambdaQueryWrapper<OperationLog> w = new LambdaQueryWrapper<OperationLog>()
                .eq(accountId != null, OperationLog::getAccountId, accountId)
                .eq(targetType != null && !targetType.isBlank(), OperationLog::getTargetType, targetType)
                .ge(OperationLog::getCreatedAt, f.atStartOfDay())
                .lt(OperationLog::getCreatedAt, t.plusDays(1).atStartOfDay())
                .orderByDesc(OperationLog::getId);
        Page<OperationLog> p = mapper.selectPage(new Page<>(pageNo, size), w);
        return new PageResult<>(p.getTotal(), p.getRecords());
    }

    private String toJson(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return String.valueOf(o);
        }
    }
}
