package com.rebuild.phoebe.domain.dto;

import com.rebuild.phoebe.domain.entity.MiddlePage;
import com.rebuild.phoebe.domain.entity.MiddlePageVersion;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MiddlePageView {
    private MiddlePage page;
    private MiddlePageVersion latest;
    private MiddlePageVersion online;
    /** VALID / VALID_EDITING / VALID_PENDING / VALID_REJECTED / DRAFT / PENDING / REJECTED */
    private String status;
    private String statusText;
    /** 模块完整度：pc.companyInfo -> true ... */
    private Map<String, Boolean> completeness;
    private List<String> missing;
}
