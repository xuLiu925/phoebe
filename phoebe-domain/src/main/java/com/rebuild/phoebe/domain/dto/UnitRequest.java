package com.rebuild.phoebe.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 新建/编辑单元 [P:A]：名称、出价、订阅词。 */
@Data
public class UnitRequest {
    @NotBlank
    @Size(max = 30)
    private String name;
    @NotNull
    private BigDecimal bid;
    /** 为 null 时不修改订阅词。 */
    private List<String> subscribeWords;
}
