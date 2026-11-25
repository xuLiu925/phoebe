package com.rebuild.phoebe.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.common.Evidence;
import com.rebuild.phoebe.common.EvidenceLevel;
import com.rebuild.phoebe.domain.config.PhoebeRules;
import com.rebuild.phoebe.domain.dto.AccountRequest;
import com.rebuild.phoebe.domain.entity.Account;
import com.rebuild.phoebe.domain.enums.AccountStatus;
import com.rebuild.phoebe.domain.mapper.AccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** 广告账户。开通、余额等属于百度商业通用基础设施，此处为模拟 [I]。 */
@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountMapper mapper;
    private final PhoebeRules rules;
    private final OpLogService opLog;

    public Account get(Long id) {
        Account a = mapper.selectById(id);
        if (a == null) {
            throw BizException.notFound("账户");
        }
        return a;
    }

    public List<Account> list() {
        return mapper.selectList(new LambdaQueryWrapper<Account>().orderByAsc(Account::getId));
    }

    @Transactional
    @Evidence(level = EvidenceLevel.PUBLIC, source = "A,C", note = "医疗、成人用品、彩票行业暂无法开通")
    public Account create(AccountRequest req) {
        if (rules.getBannedIndustries().contains(req.getIndustry())) {
            throw BizException.badRequest("该行业暂不支持开通知识营销：" + req.getIndustry());
        }
        Account a = new Account();
        a.setName(req.getName());
        a.setCompanyName(req.getCompanyName());
        a.setWebsite(req.getWebsite());
        a.setConsultUrl(req.getConsultUrl());
        a.setIndustry(req.getIndustry());
        a.setBalance(req.getInitialBalance() == null ? BigDecimal.ZERO : req.getInitialBalance());
        a.setStatus(AccountStatus.ACTIVE);
        mapper.insert(a);
        opLog.log(a.getId(), "ACCOUNT", a.getId(), "CREATE", a);
        return a;
    }

    @Transactional
    public Account recharge(Long id, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw BizException.badRequest("充值金额必须大于0");
        }
        get(id);
        mapper.update(null, new LambdaUpdateWrapper<Account>()
                .eq(Account::getId, id)
                .setSql("balance = balance + " + amount.toPlainString()));
        opLog.log(id, "ACCOUNT", id, "RECHARGE", "模拟充值 " + amount);
        return get(id);
    }

    /** 扣费：最多扣到 0，返回实际扣费。 */
    @Transactional
    public BigDecimal deduct(Long id, BigDecimal amount) {
        Account a = get(id);
        BigDecimal actual = a.getBalance().min(amount).max(BigDecimal.ZERO);
        if (actual.signum() > 0) {
            mapper.update(null, new LambdaUpdateWrapper<Account>()
                    .eq(Account::getId, id)
                    .setSql("balance = balance - " + actual.toPlainString()));
        }
        return actual;
    }
}
