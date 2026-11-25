package com.rebuild.phoebe.simulator.search;

import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.common.Evidence;
import com.rebuild.phoebe.common.EvidenceLevel;
import com.rebuild.phoebe.domain.entity.ImpressionLog;
import com.rebuild.phoebe.domain.entity.QuestionLibrary;
import com.rebuild.phoebe.domain.enums.Channel;
import com.rebuild.phoebe.domain.enums.Device;
import com.rebuild.phoebe.domain.mapper.ImpressionLogMapper;
import com.rebuild.phoebe.domain.service.StatService;
import com.rebuild.phoebe.simulator.SimulatorProperties;
import com.rebuild.phoebe.simulator.auction.AuctionEngine;
import com.rebuild.phoebe.simulator.auction.Candidate;
import com.rebuild.phoebe.simulator.auction.CandidateLoader;
import com.rebuild.phoebe.simulator.library.QuestionLibraryProvider;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.rebuild.phoebe.domain.entity.Account;
import com.rebuild.phoebe.domain.entity.CptOrder;
import com.rebuild.phoebe.domain.entity.MiddlePage;
import com.rebuild.phoebe.domain.entity.Plan;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.entity.Unit;
import com.rebuild.phoebe.domain.enums.RunStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.UUID;

/**
 * 模拟检索：召回 → 资格过滤 → GSP 竞价 → 与自然结果混排 → 记录展现。
 * 不接入任何真实百度系统；自然结果来自虚构的问答知识库。
 */
@Service
@RequiredArgsConstructor
public class SearchSimulator {
    public static final String SEARCH_LIST = "SEARCH_LIST";
    public static final String QUESTION_PAGE = "QUESTION_PAGE";

    private final CandidateLoader loader;
    private final AuctionEngine auctionEngine;
    private final QuestionLibraryProvider library;
    private final ImpressionLogMapper impressionMapper;
    private final StatService statService;
    private final SimulatorProperties props;
    private final com.rebuild.phoebe.simulator.cpt.CptService cptService;
    private final com.rebuild.phoebe.domain.service.EligibilityService eligibility;
    private final com.rebuild.phoebe.domain.mapper.QuestionAnswerMapper qaMapper;
    private final com.rebuild.phoebe.domain.mapper.UnitMapper unitMapper;
    private final com.rebuild.phoebe.domain.mapper.PlanMapper planMapper;
    private final com.rebuild.phoebe.domain.mapper.AccountMapper accountMapper;

    @Data
    public static class SearchRequest {
        private String query;
        private Channel channel = Channel.ZHIDAO;
        private Device device = Device.PC;
        private String pageType = SEARCH_LIST;
        private String region;
        /** 网民 IP（模拟），用于计划 IP 排除。 */
        private String ip;
        /** 模拟检索时刻，用于投放时段；空 = 当前时间。 */
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private java.time.LocalDateTime time;
    }

    @Data
    public static class Item {
        private String type;
        private int position;
        private String title;
        private String snippet;
        /** 广告项的完整答案（中间页展示用）。 */
        private String answer;
        private Long impressionId;
        private Long qaId;
        private Long middlePageId;
        private String advertiser;
        /** CPC 竞价 / CPT 包词。 */
        private String billingType;
    }

    @Data
    public static class SearchResult {
        private String requestId;
        private SearchRequest request;
        private List<Item> items = new ArrayList<>();
        private List<Candidate> auction = new ArrayList<>();
        private List<CandidateLoader.Excluded> excluded = new ArrayList<>();
        private String rule;
        /** 命中并展现的 CPT 包词订单（无则为 null）。 */
        private Map<String, Object> cpt;
    }

    @Transactional
    @Evidence(level = EvidenceLevel.PUBLIC, source = "B", note = "检索页与自然结果混排、初始第4位；问题页相似问题第1位")
    public SearchResult search(SearchRequest req) {
        if (req.getQuery() == null || req.getQuery().isBlank()) {
            throw BizException.badRequest("请输入检索词");
        }
        SearchResult result = new SearchResult();
        result.setRequestId(UUID.randomUUID().toString());
        result.setRequest(req);

        LocalDate searchDate = req.getTime() == null ? LocalDate.now() : req.getTime().toLocalDate();
        CandidateLoader.Recall recall = loader.recall(req.getQuery().trim(), req.getDevice(), req.getRegion(), req.getIp(), req.getTime(), null);
        result.setExcluded(recall.getExcluded());

        // [C] 同一个词 CPT 展示优先级高于 CPC：CPT 占第一个广告位，CPC 顺延
        CptHit cpt = resolveCpt(req.getQuery().trim(), searchDate, result.getExcluded());
        List<Candidate> cpcCandidates = new ArrayList<>(recall.getCandidates());
        int cpcSlots = props.getAdSlots();
        if (cpt != null) {
            cpcSlots = Math.max(0, cpcSlots - 1);
            // CPT 广告主自己的 CPC 问答不再重复展现 [I]
            cpcCandidates.removeIf(c -> Objects.equals(c.getAccountId(), cpt.order.getAccountId()));
        }
        List<Candidate> ranked = auctionEngine.run(cpcCandidates, cpcSlots);
        if (cpt != null) {
            for (Candidate c : recall.getCandidates()) {
                if (Objects.equals(c.getAccountId(), cpt.order.getAccountId())) {
                    c.setNote("该广告主已通过 CPT 包词展现");
                    c.setScore(c.getEffectiveBid().doubleValue() * c.getQuality());
                    ranked.add(c);
                } else if (!c.isWon() && cpcSlots == 0 && c.getRank() != null && c.getRank() == 1) {
                    c.setNote("CPT 优先占位，CPC 无剩余广告位");
                }
            }
        }
        result.setAuction(ranked);

        boolean questionPage = QUESTION_PAGE.equals(req.getPageType());
        int adPos = questionPage ? props.getQuestionPagePosition() : props.getSearchListPosition();
        result.setRule(questionPage ? "问题页“相似问题”第1位" : "检索页与自然结果混排，初始第" + adPos + "位");

        List<QuestionLibrary> natural = library.natural(req.getQuery(), 9);
        List<Item> naturals = new ArrayList<>();
        for (QuestionLibrary q : natural) {
            Item it = new Item();
            it.setType("NATURAL");
            it.setTitle(q.getQuestion());
            it.setSnippet(q.getAnswer() == null ? "网友回答（模拟自然结果）" : abbreviate(q.getAnswer(), 60));
            naturals.add(it);
        }
        if (naturals.isEmpty()) {
            Item it = new Item();
            it.setType("NATURAL");
            it.setTitle(req.getQuery() + "（模拟自然结果）");
            it.setSnippet("暂无更多相关问题");
            naturals.add(it);
        }

        List<Item> ads = new ArrayList<>();
        if (cpt != null) {
            ads.add(serveCpt(cpt, req, result, adPos));
        }
        for (Candidate c : ranked) {
            if (!c.isWon()) {
                continue;
            }
            ImpressionLog imp = new ImpressionLog();
            imp.setRequestId(result.getRequestId());
            imp.setQuery(req.getQuery());
            imp.setChannel(req.getChannel());
            imp.setPageType(req.getPageType());
            imp.setDevice(req.getDevice());
            imp.setRegion(req.getRegion());
            imp.setAccountId(c.getAccountId());
            imp.setPlanId(c.getPlan().getId());
            imp.setUnitId(c.getUnit().getId());
            imp.setQaId(c.getQaId());
            imp.setMiddlePageId(c.getMiddlePageId());
            imp.setRankPos(adPos);
            imp.setEffectiveBid(c.getEffectiveBid());
            imp.setChargePrice(c.getChargePrice());
            imp.setQualityScore(BigDecimal.valueOf(c.getQuality()));
            imp.setMatchType(c.getMatchType());
            imp.setBillingType("CPC");
            imp.setClicked(false);
            impressionMapper.insert(imp);
            statService.addQa(c.getQa(), req.getDevice(), req.getChannel(), req.getRegion(), LocalDate.now(), 1, 0, BigDecimal.ZERO);

            Item it = new Item();
            it.setType("AD");
            it.setTitle(c.getQuestion());
            it.setSnippet(abbreviate(com.rebuild.phoebe.common.TextRules.stripHtml(c.getQa().getAnswer()), 60));
            it.setAnswer(c.getQa().getAnswer());
            it.setImpressionId(imp.getId());
            it.setQaId(c.getQaId());
            it.setMiddlePageId(c.getMiddlePageId());
            it.setAdvertiser(c.getAccountName());
            it.setBillingType("CPC");
            ads.add(it);
        }

        List<Item> items = new ArrayList<>(naturals);
        int insertAt = Math.min(Math.max(adPos - 1, 0), items.size());
        items.addAll(insertAt, ads);
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setPosition(i + 1);
        }
        result.setItems(items);
        return result;
    }

    // ------------------------------------------------------------------ CPT

    private record CptHit(CptOrder order, QuestionAnswer qa, Account account, Long middlePageId) {
    }

    /**
     * 按优先级检查命中的 CPT 订单，返回第一个具备展现资格的。
     * CPT 已预付：不受账户余额、计划日预算影响；投放时段、地域、IP 排除也不作用于 CPT [I]；
     * 但问答须审核通过且启用、单元与计划启用、计划有启用的有效中间页 [I]。
     */
    private CptHit resolveCpt(String query, LocalDate date, List<CandidateLoader.Excluded> excluded) {
        for (CptOrder o : cptService.matching(query, date)) {
            QuestionAnswer qa = qaMapper.selectById(o.getQaId());
            Unit unit = qa == null ? null : unitMapper.selectById(qa.getUnitId());
            Plan plan = qa == null ? null : planMapper.selectById(qa.getPlanId());
            Account account = accountMapper.selectById(o.getAccountId());
            List<String> reasons = new ArrayList<>();
            MiddlePage mp = null;
            if (qa == null || unit == null || plan == null) {
                reasons.add("CPT 绑定的问答已删除");
            } else {
                reasons.addAll(eligibility.qaBlockers(qa, unit));
                if (plan.getStatus() != RunStatus.ACTIVE) {
                    reasons.add("计划已暂停");
                }
                mp = eligibility.enabledValidPage(plan.getId());
                if (mp == null) {
                    reasons.add("无启用且审核通过的中间页");
                }
            }
            if (reasons.isEmpty()) {
                return new CptHit(o, qa, account, mp.getId());
            }
            CandidateLoader.Excluded ex = new CandidateLoader.Excluded();
            ex.setQaId(o.getQaId());
            ex.setQuestion(qa == null ? "(已删除)" : qa.getQuestion());
            ex.setAccountName(account == null ? null : account.getCompanyName());
            ex.setMatchType("CPT");
            reasons.add(0, "CPT 订单#" + o.getId() + "「" + o.getKeyword() + "」不具备展现资格，广告位交给 CPC");
            ex.setReasons(reasons);
            excluded.add(ex);
        }
        return null;
    }

    private Item serveCpt(CptHit hit, SearchRequest req, SearchResult result, int adPos) {
        ImpressionLog imp = new ImpressionLog();
        imp.setRequestId(result.getRequestId());
        imp.setQuery(req.getQuery());
        imp.setChannel(req.getChannel());
        imp.setPageType(req.getPageType());
        imp.setDevice(req.getDevice());
        imp.setRegion(req.getRegion());
        imp.setAccountId(hit.order.getAccountId());
        imp.setPlanId(hit.qa.getPlanId());
        imp.setUnitId(hit.qa.getUnitId());
        imp.setQaId(hit.qa.getId());
        imp.setMiddlePageId(hit.middlePageId);
        imp.setRankPos(adPos);
        imp.setEffectiveBid(BigDecimal.ZERO);
        imp.setChargePrice(BigDecimal.ZERO);
        imp.setQualityScore(BigDecimal.ONE);
        imp.setMatchType("CPT");
        imp.setBillingType("CPT");
        imp.setCptOrderId(hit.order.getId());
        imp.setClicked(false);
        impressionMapper.insert(imp);

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("orderId", hit.order.getId());
        info.put("keyword", hit.order.getKeyword());
        info.put("advertiser", hit.account == null ? null : hit.account.getCompanyName());
        info.put("question", hit.qa.getQuestion());
        info.put("period", hit.order.getStartDate() + " ~ " + hit.order.getEndDate());
        info.put("dailyPrice", hit.order.getDailyPrice());
        result.setCpt(info);

        Item it = new Item();
        it.setType("AD");
        it.setTitle(hit.qa.getQuestion());
        it.setSnippet(abbreviate(com.rebuild.phoebe.common.TextRules.stripHtml(hit.qa.getAnswer()), 60));
        it.setAnswer(hit.qa.getAnswer());
        it.setImpressionId(imp.getId());
        it.setQaId(hit.qa.getId());
        it.setMiddlePageId(hit.middlePageId);
        it.setAdvertiser(hit.account == null ? null : hit.account.getCompanyName());
        it.setBillingType("CPT");
        return it;
    }

    private static String abbreviate(String s, int n) {
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }
}
