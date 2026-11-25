package com.rebuild.phoebe.common;

import java.util.regex.Pattern;

/**
 * 文本规则工具。
 */
public final class TextRules {

    /** 中英文标点。 */
    private static final String PUNCT = "[\\p{P}\\p{S}&&[^#@_\\-/:.%（）()《》<>\"“”'‘’]]";
    private static final Pattern CONSECUTIVE_PUNCT = Pattern.compile(PUNCT + "{2,}");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    private TextRules() {
    }

    /**
     * 按“汉字=2 字符、其他=1 字符”计数。
     */
    @Evidence(level = EvidenceLevel.PUBLIC, source = "A", note = "问题不少于10字符(=5汉字)，不多于98字符(=49汉字)")
    public static int countChars(String s) {
        if (s == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            n += isWide(cp) ? 2 : 1;
            i += Character.charCount(cp);
        }
        return n;
    }

    private static boolean isWide(int cp) {
        Character.UnicodeScript sc = Character.UnicodeScript.of(cp);
        if (sc == Character.UnicodeScript.HAN) {
            return true;
        }
        Character.UnicodeBlock b = Character.UnicodeBlock.of(cp);
        return b == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || b == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }

    /**
     * 是否含有 2 个及以上连续标点（如 “？？”“！！”）。
     * 原文举例为重复的问号/叹号；省略号“……”是否允许 [U]，此处按连续标点处理。
     */
    @Evidence(level = EvidenceLevel.PUBLIC, source = "A", note = "不能含有2个及以上的连续标点符号")
    public static boolean hasConsecutivePunct(String s) {
        return s != null && CONSECUTIVE_PUNCT.matcher(s).find();
    }

    /** 去掉富文本 HTML 标签，用于答案字数统计。 */
    public static String stripHtml(String s) {
        if (s == null) {
            return "";
        }
        return HTML_TAG.matcher(s).replaceAll("").replace("&nbsp;", " ").trim();
    }
}
