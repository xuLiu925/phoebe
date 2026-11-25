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

/** 模拟展现日志。 */
@Data
@TableName("pb_impression_log")
public class ImpressionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private String query;
    private Channel channel;
    private String pageType;
    private Device device;
    private String region;
    private Long accountId;
    private Long planId;
    private Long unitId;
    private Long qaId;
    private Long middlePageId;
    private Integer rankPos;
    private BigDecimal effectiveBid;
    private BigDecimal chargePrice;
    private BigDecimal qualityScore;
    private String matchType;
    /** CPC / CPT [C]。 */
    private String billingType;
    private Long cptOrderId;
    private Boolean clicked;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
