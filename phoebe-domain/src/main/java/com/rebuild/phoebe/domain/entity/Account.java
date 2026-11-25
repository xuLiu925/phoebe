package com.rebuild.phoebe.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.rebuild.phoebe.domain.enums.AccountStatus;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 广告账户。层级 [P:A]，字段 [I]。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pb_account")
public class Account extends BaseEntity {
    private String name;
    private String companyName;
    private String website;
    private String consultUrl;
    private String industry;
    private BigDecimal balance;
    private AccountStatus status;
}
