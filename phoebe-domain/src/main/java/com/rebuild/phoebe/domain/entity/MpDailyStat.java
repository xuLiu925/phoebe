package com.rebuild.phoebe.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rebuild.phoebe.domain.enums.Device;
import java.time.LocalDate;
import lombok.Data;

/** 中间页日报（不区分模块）[P:B]。 */
@Data
@TableName("pb_mp_daily_stat")
public class MpDailyStat {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate statDate;
    private Long accountId;
    private Long planId;
    private Long middlePageId;
    private Device device;
    private com.rebuild.phoebe.domain.enums.Channel channel;
    private String region;
    private Long views;
    private Long clicks;
}
