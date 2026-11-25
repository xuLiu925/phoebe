package com.rebuild.phoebe.domain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模拟审核配置。真实产品为人工业审，承诺 3 个工作日 [P:B]；此处不模拟时效。
 */
@Data
@ConfigurationProperties(prefix = "phoebe.audit")
public class AuditProperties {
    /** true：提交即自动通过（机器预审命中违禁词的除外），便于演示。 */
    private boolean autoApprove = false;
}
