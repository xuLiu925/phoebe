package com.rebuild.phoebe.simulator.auction;

import java.util.List;

/** 竞价引擎（替换点）。真实竞价由百度广告系统完成，Phoebe 是否包含该逻辑 [U]。 */
public interface AuctionEngine {
    /**
     * 对候选排序并决定胜出者与计费价。实现需填充每个候选的 rank / won / chargePrice。
     *
     * @param slots 广告位数量
     * @return 按排名排序后的全部候选
     */
    List<Candidate> run(List<Candidate> candidates, int slots);
}
