package com.rebuild.phoebe.simulator.match;

import java.util.HashSet;
import java.util.Set;

/** 简易文本工具：归一化 + 二元组 Jaccard。无分词库依赖 [新建实现]。 */
public final class TextSimilarity {
    private static final String STOP = "什么怎么哪个哪些如何是否吗呢啊吧的了是有";

    private TextSimilarity() {
    }

    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[\\p{P}\\p{S}\\s]", "").toLowerCase();
    }

    /** 去掉疑问词等停用字后的核心串。 */
    public static String core(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : normalize(s).toCharArray()) {
            if (STOP.indexOf(c) < 0) {
                b.append(c);
            }
        }
        return b.toString();
    }

    public static Set<String> bigrams(String s) {
        String n = normalize(s);
        Set<String> set = new HashSet<>();
        if (n.length() == 1) {
            set.add(n);
        }
        for (int i = 0; i + 1 < n.length(); i++) {
            set.add(n.substring(i, i + 2));
        }
        return set;
    }

    public static double jaccard(String a, String b) {
        Set<String> x = bigrams(a);
        Set<String> y = bigrams(b);
        if (x.isEmpty() || y.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(x);
        inter.retainAll(y);
        Set<String> union = new HashSet<>(x);
        union.addAll(y);
        return (double) inter.size() / union.size();
    }
}
