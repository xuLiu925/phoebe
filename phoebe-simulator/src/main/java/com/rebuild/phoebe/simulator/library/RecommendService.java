package com.rebuild.phoebe.simulator.library;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rebuild.phoebe.common.UserContext;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.entity.QuestionLibrary;
import com.rebuild.phoebe.domain.mapper.QuestionAnswerMapper;
import com.rebuild.phoebe.domain.service.Guard;
import com.rebuild.phoebe.domain.service.UnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 新建问答的三个入口 [P:A]：优质问答 / 推荐问题（关键词、订阅词）/ 手动创建；以及答案推荐。 */
@Service
@RequiredArgsConstructor
public class RecommendService {
    private final QuestionLibraryProvider provider;
    private final QuestionAnswerMapper qaMapper;
    private final UnitService unitService;
    private final Guard guard;

    public List<Map<String, Object>> byKeyword(String keyword) {
        return mark(provider.byKeyword(keyword, 30));
    }

    public List<Map<String, Object>> bySubscribe(Long unitId) {
        guard.unit(unitId);
        List<String> words = unitService.subscribeWords(unitId);
        return mark(provider.bySubscribeWords(words, 30));
    }

    public List<Map<String, Object>> quality(String keyword) {
        return mark(provider.quality(keyword, 30));
    }

    public List<String> answers(String question) {
        return provider.answers(question, 3);
    }

    /** 标记当前账户是否已使用该问题。 */
    private List<Map<String, Object>> mark(List<QuestionLibrary> list) {
        Long accountId = UserContext.get() == null ? null : UserContext.get().accountId();
        Set<String> used = accountId == null ? Set.of()
                : qaMapper.selectList(new LambdaQueryWrapper<QuestionAnswer>().eq(QuestionAnswer::getAccountId, accountId)
                        .select(QuestionAnswer::getQuestion)).stream().map(QuestionAnswer::getQuestion).collect(Collectors.toSet());
        return list.stream().map(q -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", q.getId());
            m.put("question", q.getQuestion());
            m.put("answer", q.getAnswer());
            m.put("pv", q.getPv());
            m.put("quality", q.getQuality() != null && q.getQuality() == 1);
            m.put("channel", q.getChannel());
            m.put("used", used.contains(q.getQuestion()));
            return m;
        }).collect(Collectors.toList());
    }
}
