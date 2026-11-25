package com.rebuild.phoebe.simulator.search;

import com.rebuild.phoebe.domain.entity.QuestionLibrary;
import com.rebuild.phoebe.domain.enums.Channel;
import com.rebuild.phoebe.domain.enums.Device;
import com.rebuild.phoebe.simulator.billing.BillingService;
import com.rebuild.phoebe.simulator.library.QuestionLibraryProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** 批量造流量：从问答知识库抽取检索词，按设定点击率模拟点击。 */
@Service
@RequiredArgsConstructor
public class TrafficGenerator {
    private static final String[] REGIONS = {"北京", "上海", "广东", "浙江", "江苏", "四川", "山东", "湖北", "河南", "福建"};
    private final SearchSimulator searchSimulator;
    private final BillingService billingService;
    private final QuestionLibraryProvider library;

    public Map<String, Object> generate(int searches, double clickRate, Long seed) {
        Random rnd = seed == null ? new Random() : new Random(seed);
        List<QuestionLibrary> queries = library.byKeyword(null, 200);
        int impressions = 0;
        int clicks = 0;
        BigDecimal cost = BigDecimal.ZERO;
        for (int i = 0; i < Math.min(searches, 2000); i++) {
            if (queries.isEmpty()) {
                break;
            }
            QuestionLibrary q = queries.get(rnd.nextInt(queries.size()));
            SearchSimulator.SearchRequest req = new SearchSimulator.SearchRequest();
            req.setQuery(rnd.nextBoolean() ? q.getQuestion() : q.getKeywords().split(",")[0]);
            req.setChannel("BAOBAO".equals(q.getChannel()) ? (rnd.nextBoolean() ? Channel.BAOBAO : Channel.BAOBAO_APP)
                    : (rnd.nextInt(4) == 0 ? Channel.SEARCH : Channel.ZHIDAO));
            req.setRegion(REGIONS[rnd.nextInt(REGIONS.length)]);
            req.setIp((rnd.nextInt(200) + 20) + "." + rnd.nextInt(256) + "." + rnd.nextInt(256) + "." + (rnd.nextInt(254) + 1));
            req.setDevice(rnd.nextInt(10) < 6 ? Device.MOBILE : Device.PC);
            req.setPageType(rnd.nextInt(3) == 0 ? SearchSimulator.QUESTION_PAGE : SearchSimulator.SEARCH_LIST);
            SearchSimulator.SearchResult r = searchSimulator.search(req);
            for (SearchSimulator.Item it : r.getItems()) {
                if (!"AD".equals(it.getType())) {
                    continue;
                }
                impressions++;
                if (rnd.nextDouble() < clickRate) {
                    Map<String, Object> c = billingService.clickEntry(it.getImpressionId());
                    clicks++;
                    cost = cost.add((BigDecimal) c.get("charged"));
                    if (rnd.nextDouble() < 0.3) {
                        billingService.clickInner(it.getImpressionId(), rnd.nextBoolean() ? "consult" : "product");
                    }
                }
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("searches", searches);
        m.put("adImpressions", impressions);
        m.put("clicks", clicks);
        m.put("cost", cost);
        return m;
    }
}
