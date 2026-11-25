package com.rebuild.phoebe.domain.service;

import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.common.UserContext;
import com.rebuild.phoebe.domain.entity.Creative;
import com.rebuild.phoebe.domain.entity.EnterpriseKnowledge;
import com.rebuild.phoebe.domain.entity.ImageAsset;
import com.rebuild.phoebe.domain.entity.MiddlePage;
import com.rebuild.phoebe.domain.entity.Plan;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.entity.Unit;
import com.rebuild.phoebe.domain.mapper.CreativeMapper;
import com.rebuild.phoebe.domain.mapper.EnterpriseKnowledgeMapper;
import com.rebuild.phoebe.domain.mapper.ImageAssetMapper;
import com.rebuild.phoebe.domain.mapper.MiddlePageMapper;
import com.rebuild.phoebe.domain.mapper.PlanMapper;
import com.rebuild.phoebe.domain.mapper.QuestionAnswerMapper;
import com.rebuild.phoebe.domain.mapper.UnitMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 数据归属校验：广告主只能操作本账户数据；未代登录的管理员与系统调用（无上下文）不受限。
 */
@Component
@RequiredArgsConstructor
public class Guard {
    private final PlanMapper planMapper;
    private final UnitMapper unitMapper;
    private final QuestionAnswerMapper qaMapper;
    private final CreativeMapper creativeMapper;
    private final MiddlePageMapper middlePageMapper;
    private final EnterpriseKnowledgeMapper knowledgeMapper;
    private final ImageAssetMapper imageMapper;

    public void check(Long ownerAccountId) {
        UserContext.CurrentUser u = UserContext.get();
        if (u == null) {
            return;
        }
        if (u.accountId() == null && u.role() != UserContext.Role.ADVERTISER) {
            return;
        }
        if (!Objects.equals(u.accountId(), ownerAccountId)) {
            throw BizException.forbidden();
        }
    }

    public Plan plan(Long id) {
        Plan p = planMapper.selectById(id);
        if (p == null) {
            throw BizException.notFound("计划");
        }
        check(p.getAccountId());
        return p;
    }

    public Unit unit(Long id) {
        Unit u = unitMapper.selectById(id);
        if (u == null) {
            throw BizException.notFound("单元");
        }
        check(u.getAccountId());
        return u;
    }

    public QuestionAnswer qa(Long id) {
        QuestionAnswer q = qaMapper.selectById(id);
        if (q == null) {
            throw BizException.notFound("问答");
        }
        check(q.getAccountId());
        return q;
    }

    public Creative creative(Long id) {
        Creative c = creativeMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("创意");
        }
        check(c.getAccountId());
        return c;
    }

    public MiddlePage middlePage(Long id) {
        MiddlePage m = middlePageMapper.selectById(id);
        if (m == null) {
            throw BizException.notFound("中间页");
        }
        check(m.getAccountId());
        return m;
    }

    public EnterpriseKnowledge knowledge(Long id) {
        EnterpriseKnowledge k = knowledgeMapper.selectById(id);
        if (k == null) {
            throw BizException.notFound("企业知识");
        }
        check(k.getAccountId());
        return k;
    }

    public ImageAsset image(Long id) {
        ImageAsset i = imageMapper.selectById(id);
        if (i == null) {
            throw BizException.notFound("图片");
        }
        check(i.getAccountId());
        return i;
    }
}
