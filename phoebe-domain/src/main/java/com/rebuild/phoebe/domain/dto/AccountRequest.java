package com.rebuild.phoebe.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String companyName;
    private String website;
    private String consultUrl;
    @NotBlank
    private String industry;
    private BigDecimal initialBalance;
    /** 同时创建的广告主登录账号（web 层处理）。 */
    private String username;
    private String password;
}
