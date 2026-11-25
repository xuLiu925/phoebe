package com.rebuild.phoebe.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 模拟问答知识库条目 [P:E 存在]，数据虚构。 */
@Data
@TableName("pb_question_library")
public class QuestionLibrary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String question;
    private String answer;
    private String keywords;
    private Integer pv;
    private Integer quality;
    private String channel;
}
