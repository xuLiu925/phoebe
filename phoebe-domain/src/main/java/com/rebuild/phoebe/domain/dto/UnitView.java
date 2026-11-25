package com.rebuild.phoebe.domain.dto;

import com.rebuild.phoebe.domain.entity.Unit;
import lombok.Data;

import java.util.List;

@Data
public class UnitView {
    private Unit unit;
    private String planName;
    private long qaCount;
    private long approvedQaCount;
    private long creativeCount;
    private List<String> subscribeWords;
}
