package com.rebuild.phoebe.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rebuild.phoebe.domain.enums.AuditStatus;
import com.rebuild.phoebe.domain.enums.QaSource;
import com.rebuild.phoebe.domain.enums.RunStatus;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 问答：问题标题是竞价对象，完整问答对参与竞价展现 [P:A]。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pb_question_answer")
public class QuestionAnswer extends BaseEntity {
    private Long accountId;
    private Long planId;
    private Long unitId;
    private String question;
    private String answer;
    /** 问答出价；null = 使用单元出价 [I]。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal bid;
    private QaSource source;
    private AuditStatus auditStatus;
    private RunStatus status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String rejectReason;
    private Integer auditVersion;
    @TableLogic
    private Integer deleted;
}
