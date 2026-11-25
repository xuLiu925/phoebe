package com.rebuild.phoebe.simulator.tool;

import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.domain.dto.MiddlePageView;
import com.rebuild.phoebe.domain.dto.PlanRequest;
import com.rebuild.phoebe.domain.dto.QaRequest;
import com.rebuild.phoebe.domain.dto.UnitRequest;
import com.rebuild.phoebe.domain.entity.Plan;
import com.rebuild.phoebe.domain.entity.QuestionLibrary;
import com.rebuild.phoebe.domain.entity.Unit;
import com.rebuild.phoebe.domain.enums.QaSource;
import com.rebuild.phoebe.domain.service.MiddlePageService;
import com.rebuild.phoebe.domain.service.PlanService;
import com.rebuild.phoebe.domain.service.QaService;
import com.rebuild.phoebe.domain.service.UnitService;
import com.rebuild.phoebe.simulator.library.QuestionLibraryProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能引导：系统引导智能创建计划-单元-问答等物料，快速搭建账户 [P:A 存在]。流程细节 [I]。
 * 中间页物料需客户补充，因此只生成带默认值的中间页草稿。
 */
@Service
@RequiredArgsConstructor
public class WizardService {
    private final PlanService planService;
    private final UnitService unitService;
    private final QaService qaService;
    private final MiddlePageService middlePageService;
    private final QuestionLibraryProvider library;

    @Transactional
    public Map<String, Object> build(String keyword, BigDecimal budget, BigDecimal bid, int maxQas) {
        if (keyword == null || keyword.isBlank()) {
            throw BizException.badRequest("请输入推广关键词");
        }
        String kw = keyword.trim();
        String suffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"));
        PlanRequest pr = new PlanRequest();
        pr.setName("智能-" + kw + "-" + suffix);
        pr.setBudget(budget);
        pr.setMobileBidRatio(BigDecimal.ONE);
        Plan plan = planService.create(pr);

        UnitRequest ur = new UnitRequest();
        ur.setName(kw + "-智能单元");
        ur.setBid(bid == null ? new BigDecimal("1.00") : bid);
        ur.setSubscribeWords(List.of(kw));
        Unit unit = unitService.create(plan.getId(), ur);

        List<Long> qaIds = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (QuestionLibrary q : library.quality(kw, Math.max(1, maxQas))) {
            try {
                QaRequest req = new QaRequest();
                req.setQuestion(q.getQuestion());
                req.setAnswer(q.getAnswer());
                req.setSource(QaSource.WIZARD);
                qaIds.add(qaService.create(unit.getId(), req).getId());
            } catch (BizException e) {
                skipped.add(q.getQuestion() + "：" + e.getMessage());
            }
        }
        MiddlePageView mp = middlePageService.create(plan.getId(), kw + "-中间页");

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("planId", plan.getId());
        r.put("unitId", unit.getId());
        r.put("qaIds", qaIds);
        r.put("skipped", skipped);
        r.put("middlePageId", mp.getPage().getId());
        r.put("next", "请补全并提交中间页；问答与中间页审核通过后即可参与投放");
        return r;
    }
}
