package com.rebuild.phoebe.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/** 高级创意：标题、描述、图片、落地链接、关联问答。字段 [I]。 */
@Data
public class CreativeRequest {
    @NotBlank
    private String title;
    @NotBlank
    private String description;
    private List<String> imageUrls;
    private String targetUrl;
    private List<Long> qaIds;
}
