package com.rebuild.phoebe.domain.dto;

import com.rebuild.phoebe.domain.enums.PcStyle;
import com.rebuild.phoebe.domain.middlepage.PageContent;
import lombok.Data;

/** 全部保存 / 分模块保存。字段为 null 表示不修改该部分。 */
@Data
public class MiddlePageDraftRequest {
    private String name;
    private PcStyle pcStyle;
    private PageContent pc;
    private PageContent mobile;
}
