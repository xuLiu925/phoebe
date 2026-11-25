package com.rebuild.phoebe.domain.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 图片素材 [P:A]。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pb_image_asset")
public class ImageAsset extends BaseEntity {
    private Long accountId;
    private String name;
    private String url;
    private Integer width;
    private Integer height;
    private String source;
    private String cropRatio;
    @TableLogic
    private Integer deleted;
}
