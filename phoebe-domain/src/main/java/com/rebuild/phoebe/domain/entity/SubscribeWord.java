package com.rebuild.phoebe.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 订阅词 [P:A,B]。 */
@Data
@TableName("pb_subscribe_word")
public class SubscribeWord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long accountId;
    private Long unitId;
    private String word;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
