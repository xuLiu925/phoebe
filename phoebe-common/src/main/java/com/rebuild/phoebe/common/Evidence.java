package com.rebuild.phoebe.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注一条业务规则/设计的证据等级与来源，便于日后与原项目逐项校准。
 * <p>
 * 用法：{@code @Evidence(level = EvidenceLevel.PUBLIC, source = "B", note = "单元最多100条问答")}
 * <p>
 * 可通过 {@code grep -rn "@Evidence" } 汇总所有待校准点。
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface Evidence {
    EvidenceLevel level();

    /** 参考资料编号（A~H，见 docs/01-业务分析.md），或 "dev" 表示已知项目线索。 */
    String source() default "";

    String note() default "";
}
