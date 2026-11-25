package com.rebuild.phoebe.domain.service;

import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.common.Evidence;
import com.rebuild.phoebe.common.EvidenceLevel;
import com.rebuild.phoebe.common.TextRules;
import com.rebuild.phoebe.domain.config.PhoebeRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 物料校验与机器预审。
 * <ul>
 *   <li>长度/连续标点：输入时硬校验 [P:A]</li>
 *   <li>违禁词：提交审核时机器预审，命中直接拒绝 [P:A 提及“黄反毒等”，词表 U]</li>
 *   <li>主域一致性：仅提示 [P:B 存在该策略]</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ContentChecker {
    private final PhoebeRules rules;

    public void checkQuestion(String question) {
        String q = question == null ? "" : question.trim();
        int n = TextRules.countChars(q);
        if (n < rules.getQuestionMinChars() || n > rules.getQuestionMaxChars()) {
            throw BizException.badRequest(String.format("问题需为 %d~%d 个字符（汉字计2个字符），当前 %d",
                    rules.getQuestionMinChars(), rules.getQuestionMaxChars(), n));
        }
        checkPunct(q, "问题");
    }

    public void checkAnswer(String answer) {
        String plain = TextRules.stripHtml(answer);
        int n = TextRules.countChars(plain);
        if (n < rules.getAnswerMinChars() || n > rules.getAnswerMaxChars()) {
            throw BizException.badRequest(String.format("答案需为 %d~%d 个字符（汉字计2个字符），当前 %d",
                    rules.getAnswerMinChars(), rules.getAnswerMaxChars(), n));
        }
        checkPunct(plain, "答案");
    }

    public void checkLength(String value, String field, int minChars, int maxChars) {
        int n = TextRules.countChars(value == null ? "" : value.trim());
        if (n < minChars || n > maxChars) {
            throw BizException.badRequest(String.format("%s需为 %d~%d 个字符（汉字计2个字符），当前 %d", field, minChars, maxChars, n));
        }
    }

    private void checkPunct(String text, String field) {
        if (rules.isForbidConsecutivePunct() && TextRules.hasConsecutivePunct(text)) {
            throw BizException.badRequest(field + "不能包含2个及以上连续标点符号");
        }
    }

    /** 返回命中的违禁词。 */
    public List<String> bannedWords(String... texts) {
        List<String> hit = new ArrayList<>();
        for (String w : rules.getBannedWords()) {
            for (String t : texts) {
                if (t != null && t.contains(w) && !hit.contains(w)) {
                    hit.add(w);
                }
            }
        }
        return hit;
    }

    /** 主域一致性提示：链接主域与账户官网主域不一致时给出提示。 */
    @Evidence(level = EvidenceLevel.INFERRED, source = "B", note = "原策略细节未知，此处只比较二级域名")
    public List<String> domainWarnings(String website, Collection<String> urls) {
        List<String> warnings = new ArrayList<>();
        if (!rules.isDomainConsistencyCheck() || website == null) {
            return warnings;
        }
        String main = mainDomain(website);
        if (main == null) {
            return warnings;
        }
        for (String u : urls) {
            if (u == null || u.isBlank() || u.startsWith("/")) {
                continue;
            }
            String d = mainDomain(u);
            if (d != null && !Objects.equals(d, main)) {
                warnings.add("链接主域与官网不一致: " + u);
            }
        }
        return warnings;
    }

    static String mainDomain(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            if (host == null) {
                return null;
            }
            String[] parts = host.split("\\.");
            return parts.length <= 2 ? host : parts[parts.length - 2] + "." + parts[parts.length - 1];
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
