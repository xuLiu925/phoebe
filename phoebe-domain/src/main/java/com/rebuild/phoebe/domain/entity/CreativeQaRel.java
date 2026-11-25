package com.rebuild.phoebe.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 创意-问答关联（形式 [U]）。 */
@Data
@TableName("pb_creative_qa_rel")
public class CreativeQaRel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long creativeId;
    private Long qaId;
}
