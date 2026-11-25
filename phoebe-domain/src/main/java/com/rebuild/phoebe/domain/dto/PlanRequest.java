package com.rebuild.phoebe.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 新建/编辑计划 [P:A]：名称、预算、移动出价比例、推广地域、移动推广电话。 */
@Data
public class PlanRequest {
    @NotBlank
    @Size(max = 30)
    private String name;
    /** null = 不限预算。 */
    private BigDecimal budget;
    @NotNull
    private BigDecimal mobileBidRatio;
    /** 空 = 全部地域。 */
    private List<String> regions;
    private String mobilePhone;
    /** 投放时段 168 位 0/1 串；null = 不限时间。 */
    private String schedule;
    /** IP 排除列表；空 = 不限 IP。 */
    private List<String> excludedIps;
}
