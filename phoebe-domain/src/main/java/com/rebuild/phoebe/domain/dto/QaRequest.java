package com.rebuild.phoebe.domain.dto;

import com.rebuild.phoebe.domain.enums.QaSource;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class QaRequest {
    @NotBlank
    private String question;
    @NotBlank
    private String answer;
    /** null = 使用单元出价。 */
    private BigDecimal bid;
    private QaSource source;
}
