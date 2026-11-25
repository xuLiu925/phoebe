package com.rebuild.phoebe.domain.dto;

import com.rebuild.phoebe.domain.entity.Plan;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PlanView {
    private Plan plan;
    private long unitCount;
    private long qaCount;
    private long middlePageCount;
    private String enabledMiddlePageName;
    private BigDecimal todayCost;
    /** 不可投放原因；空 = 有效投放中。 */
    private List<String> blockers;
}
