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
import java.util.ArrayList;
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
    }

    @Data
    public static class SearchResult {
        private String requestId;
        private SearchRequest request;
        private List<Item> items = new ArrayList<>();
        private List<Candidate> auction = new ArrayList<>();
        private List<CandidateLoader.Excluded> excluded = new ArrayList<>();
        private String rule;
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

        CandidateLoader.Recall recall = loader.recall(req.getQuery().trim(), req.getDevice(), req.getRegion(), req.getIp(), req.getTime(), null);
        List<Candidate> ranked = auctionEngine.run(recall.getCandidates(), props.getAdSlots());
        result.setAuction(ranked);
        result.setExcluded(recall.getExcluded());

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

    private static String abbreviate(String s, int n) {
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }
}
