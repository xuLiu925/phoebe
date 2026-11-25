package com.rebuild.phoebe.simulator.library;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rebuild.phoebe.domain.entity.QuestionLibrary;
import com.rebuild.phoebe.domain.mapper.QuestionLibraryMapper;
import com.rebuild.phoebe.simulator.match.TextSimilarity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 基于 pb_question_library 表的模拟实现（数据量小，内存过滤）。 */
@Component
@RequiredArgsConstructor
public class DbQuestionLibraryProvider implements QuestionLibraryProvider {
    private final QuestionLibraryMapper mapper;

    private List<QuestionLibrary> all() {
        return mapper.selectList(new LambdaQueryWrapper<QuestionLibrary>().orderByDesc(QuestionLibrary::getPv));
    }

    private static boolean hit(QuestionLibrary q, String kw) {
        if (kw == null || kw.isBlank()) {
            return true;
        }
        String k = kw.trim();
        return q.getQuestion().contains(k) || (q.getKeywords() != null && q.getKeywords().contains(k))
                || TextSimilarity.jaccard(q.getQuestion(), k) >= 0.3;
    }

    @Override
    public List<QuestionLibrary> byKeyword(String keyword, int limit) {
        return all().stream().filter(q -> hit(q, keyword)).limit(limit).collect(Collectors.toList());
    }

    @Override
    public List<QuestionLibrary> bySubscribeWords(List<String> words, int limit) {
        Map<Long, QuestionLibrary> m = new LinkedHashMap<>();
        for (QuestionLibrary q : all()) {
            for (String w : words) {
                if (w != null && !w.isBlank() && hit(q, w)) {
                    m.put(q.getId(), q);
                }
            }
        }
        return m.values().stream().sorted(Comparator.comparing(QuestionLibrary::getPv).reversed()).limit(limit).collect(Collectors.toList());
    }

    @Override
    public List<QuestionLibrary> quality(String keyword, int limit) {
        return all().stream().filter(q -> q.getQuality() != null && q.getQuality() == 1 && q.getAnswer() != null)
                .filter(q -> hit(q, keyword)).limit(limit).collect(Collectors.toList());
    }

    @Override
    public List<String> answers(String question, int limit) {
        return all().stream().filter(q -> q.getAnswer() != null)
                .sorted(Comparator.comparingDouble((QuestionLibrary q) -> TextSimilarity.jaccard(q.getQuestion(), question)).reversed())
                .filter(q -> TextSimilarity.jaccard(q.getQuestion(), question) > 0.1)
                .limit(limit).map(QuestionLibrary::getAnswer).collect(Collectors.toList());
    }

    @Override
    public List<QuestionLibrary> natural(String query, int limit) {
        return all().stream()
                .filter(q -> TextSimilarity.jaccard(q.getQuestion(), query) >= 0.15 || hit(q, query))
                .sorted(Comparator.comparingDouble((QuestionLibrary q) -> TextSimilarity.jaccard(q.getQuestion(), query)).reversed())
                .limit(limit).collect(Collectors.toList());
    }
}
