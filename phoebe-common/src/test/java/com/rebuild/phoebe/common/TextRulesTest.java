package com.rebuild.phoebe.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextRulesTest {

    @Test
    void chineseCountsAsTwo() {
        assertThat(TextRules.countChars("眼霜什么牌子好")).isEqualTo(14);
        assertThat(TextRules.countChars("abc")).isEqualTo(3);
        assertThat(TextRules.countChars("A眼霜？")).isEqualTo(7);
    }

    @Test
    void consecutivePunctuation() {
        assertThat(TextRules.hasConsecutivePunct("眼霜什么牌子好？？")).isTrue();
        assertThat(TextRules.hasConsecutivePunct("太好了!!")).isTrue();
        assertThat(TextRules.hasConsecutivePunct("眼霜什么牌子好？")).isFalse();
        assertThat(TextRules.hasConsecutivePunct("官网：www.example.com")).isFalse();
    }

    @Test
    void stripHtml() {
        assertThat(TextRules.stripHtml("<p>你好<b>世界</b></p>")).isEqualTo("你好世界");
    }
}
