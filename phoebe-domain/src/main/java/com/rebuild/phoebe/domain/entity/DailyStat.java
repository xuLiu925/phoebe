package com.rebuild.phoebe.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rebuild.phoebe.domain.enums.Device;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/** 问答维度日报，可聚合至单元/计划/账户。指标 [P:B]。 */
@Data
@TableName("pb_daily_stat")
public class DailyStat {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate statDate;
    private Long accountId;
    private Long planId;
    private Long unitId;
    private Long qaId;
    private Device device;
    private com.rebuild.phoebe.domain.enums.Channel channel;
    private String region;
    private Long impressions;
    private Long clicks;
    private BigDecimal cost;
}
