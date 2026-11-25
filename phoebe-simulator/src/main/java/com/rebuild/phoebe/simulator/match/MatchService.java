package com.rebuild.phoebe.simulator.match;

import com.rebuild.phoebe.common.Evidence;
import com.rebuild.phoebe.common.EvidenceLevel;
import com.rebuild.phoebe.simulator.SimulatorProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 触发词匹配。
 * <p>
 * [P:B] 对问题出价，触发词由系统匹配；有多种配词策略，可近似理解为精确匹配和短语包含；
 * 例：问题“眼霜什么牌子好?”可被“眼霜”“眼霜 牌子”“眼霜什么牌子好”“眼霜哪个好”等触发。
 * 具体算法 [U]，此处：精确 → 短语包含 → 订阅词 → 相似。
 */
@Component
@RequiredArgsConstructor
@Evidence(level = EvidenceLevel.INFERRED, source = "B")
public class MatchService {
    public static final String EXACT = "EXACT";
    public static final String PHRASE = "PHRASE";
    public static final String SUBSCRIBE = "SUBSCRIBE";
    public static final String SIMILAR = "SIMILAR";

    private final SimulatorProperties props;

    /** 返回匹配类型，不匹配返回 null。 */
    public String match(String query, String question, List<String> subscribeWords) {
        String q = TextSimilarity.normalize(query);
        String t = TextSimilarity.normalize(question);
        if (q.isEmpty() || t.isEmpty()) {
            return null;
        }
        if (q.equals(t)) {
            return EXACT;
        }
        // 短语包含：查询词的每个空格分段都出现在问题中（如“眼霜 牌子”）
        String[] parts = query.trim().split("\\s+");
        boolean allIn = true;
        for (String p : parts) {
            String np = TextSimilarity.normalize(p);
            if (np.length() < 2 || !t.contains(np)) {
                allIn = false;
                break;
            }
        }
        if (allIn) {
            return PHRASE;
        }
        String qc = TextSimilarity.core(query);
        String tc = TextSimilarity.core(question);
        if (qc.length() >= 2 && (tc.contains(qc) || qc.contains(tc))) {
            return PHRASE;
        }
        if (subscribeWords != null) {
            for (String w : subscribeWords) {
                String nw = TextSimilarity.normalize(w);
                if (nw.length() >= 2 && q.contains(nw) && t.contains(nw)
                        && TextSimilarity.jaccard(query, question) >= props.getSimilarThreshold() / 2) {
                    return SUBSCRIBE;
                }
            }
        }
        if (TextSimilarity.jaccard(qc, tc) >= props.getSimilarThreshold()) {
            return SIMILAR;
        }
        return null;
    }
}
