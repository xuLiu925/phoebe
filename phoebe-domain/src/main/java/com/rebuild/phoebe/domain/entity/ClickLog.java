package com.rebuild.phoebe.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rebuild.phoebe.domain.enums.Channel;
import com.rebuild.phoebe.domain.enums.Device;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 点击日志：QA_ENTRY 计费；MIDDLE_PAGE_INNER 不计费 [P:B,C]。 */
@Data
@TableName("pb_click_log")
public class ClickLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    public static final String QA_ENTRY = "QA_ENTRY";
    public static final String MIDDLE_PAGE_INNER = "MIDDLE_PAGE_INNER";
    private Long impressionId;
    private Long accountId;
    private Long planId;
    private Long unitId;
    private Long qaId;
    private Long middlePageId;
    private Device device;
    private Channel channel;
    private String clickType;
    private String target;
    private BigDecimal chargePrice;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
