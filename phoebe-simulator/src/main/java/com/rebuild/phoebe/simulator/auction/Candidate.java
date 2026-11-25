package com.rebuild.phoebe.simulator.auction;

import com.rebuild.phoebe.domain.entity.Account;
import com.rebuild.phoebe.domain.entity.Plan;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.entity.Unit;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;

/** 一条参与竞价的问答。 */
@Data
public class Candidate {
    @JsonIgnore
    private QuestionAnswer qa;
    @JsonIgnore
    private Unit unit;
    @JsonIgnore
    private Plan plan;
    @JsonIgnore
    private Account account;

    private Long qaId;
    private String question;
    private Long accountId;
    private String accountName;
    private Long middlePageId;
    private String matchType;
    /** 原始出价（问答出价或单元出价）。 */
    private BigDecimal bid;
    /** 有效出价 = 出价 × (移动端 ? 移动出价比例 : 1) [P:B 推出]。 */
    private BigDecimal effectiveBid;
    private double quality;
    private double score;

    private Integer rank;
    private BigDecimal chargePrice;
    private boolean won;
    private String note;
}
