package com.rebuild.phoebe.simulator.library;

import com.rebuild.phoebe.domain.entity.QuestionLibrary;

import java.util.List;

/**
 * 问答知识库来源（替换点）。
 * <p>
 * 公开资料：百度从自有知识类媒体提取优质问答形成问答知识库，供广告主选择 [P:E]；
 * 关键词推荐高 PV 问题、订阅词持续推荐 [P:B]。真实来源可能是其他内部系统 [U]。
 */
public interface QuestionLibraryProvider {

    /** 关键词：一次性推荐高 PV 问题。 */
    List<QuestionLibrary> byKeyword(String keyword, int limit);

    /** 订阅词：持续推荐。 */
    List<QuestionLibrary> bySubscribeWords(List<String> words, int limit);

    /** 优质问答（带答案，可直接采纳）。 */
    List<QuestionLibrary> quality(String keyword, int limit);

    /** 为某个问题推荐参考答案。 */
    List<String> answers(String question, int limit);

    /** 自然结果候选（模拟检索页）。 */
    List<QuestionLibrary> natural(String query, int limit);
}
