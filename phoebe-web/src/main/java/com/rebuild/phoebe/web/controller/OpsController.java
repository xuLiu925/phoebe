package com.rebuild.phoebe.web.controller;

import com.rebuild.phoebe.common.PageResult;
import com.rebuild.phoebe.common.Result;
import com.rebuild.phoebe.common.UserContext;
import com.rebuild.phoebe.domain.config.AuditProperties;
import com.rebuild.phoebe.domain.config.PhoebeRules;
import com.rebuild.phoebe.domain.entity.AuditTask;
import com.rebuild.phoebe.domain.entity.OperationLog;
import com.rebuild.phoebe.domain.enums.AuditStatus;
import com.rebuild.phoebe.domain.enums.AuditTargetType;
import com.rebuild.phoebe.domain.enums.Device;
import com.rebuild.phoebe.domain.service.AuditService;
import com.rebuild.phoebe.domain.service.OpLogService;
import com.rebuild.phoebe.domain.service.ReportService;
import com.rebuild.phoebe.simulator.SimulatorProperties;
import com.rebuild.phoebe.simulator.billing.BillingService;
import com.rebuild.phoebe.simulator.search.SearchSimulator;
import com.rebuild.phoebe.simulator.search.TrafficGenerator;
import com.rebuild.phoebe.simulator.tool.BidAdvisor;
import com.rebuild.phoebe.simulator.tool.WizardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 审核台、模拟投放、数据报告、工具中心。 */
@Tag(name = "审核 / 模拟投放 / 报表 / 工具")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OpsController {
    private static final String BOM = "\uFEFF";
    private final AuditService auditService;
    private final SearchSimulator searchSimulator;
    private final BillingService billingService;
    private final TrafficGenerator trafficGenerator;
    private final ReportService reportService;
    private final OpLogService opLogService;
    private final com.rebuild.phoebe.domain.service.BudgetLimitService budgetLimitService;
    private final BidAdvisor bidAdvisor;
    private final WizardService wizardService;
    private final PhoebeRules rules;
    private final SimulatorProperties simulatorProperties;
    private final AuditProperties auditProperties;

    // ---------------------------------------------------------------- 审核

    @Operation(summary = "[审核员] 审核任务列表")
    @GetMapping("/audit/tasks")
    public Result<PageResult<AuditTask>> tasks(@RequestParam(required = false) AuditStatus status,
                                               @RequestParam(required = false) AuditTargetType type,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return Result.ok(auditService.page(status, type, page, size));
    }

    @GetMapping("/audit/tasks/{id}")
    public Result<AuditTask> task(@PathVariable Long id) {
        return Result.ok(auditService.get(id));
    }

    public record AuditDecision(String reason) {
    }

    @PostMapping("/audit/tasks/{id}/approve")
    public Result<AuditTask> approve(@PathVariable Long id, @RequestBody(required = false) AuditDecision req) {
        return Result.ok(auditService.approve(id, req == null ? null : req.reason()));
    }

    @PostMapping("/audit/tasks/{id}/reject")
    public Result<AuditTask> reject(@PathVariable Long id, @RequestBody AuditDecision req) {
        return Result.ok(auditService.reject(id, req.reason()));
    }

    // ---------------------------------------------------------------- 模拟投放

    @Operation(summary = "模拟检索：返回混排结果、竞价明细、匹配但被排除的问答及原因")
    @PostMapping("/simulator/search")
    public Result<SearchSimulator.SearchResult> search(@RequestBody SearchSimulator.SearchRequest req) {
        return Result.ok(searchSimulator.search(req));
    }

    @Operation(summary = "模拟点击问答入口（CPC 计费），返回中间页内容")
    @PostMapping("/simulator/click/{impressionId}")
    public Result<Map<String, Object>> click(@PathVariable Long impressionId) {
        return Result.ok(billingService.clickEntry(impressionId));
    }

    @Operation(summary = "模拟中间页内点击（不计费）")
    @PostMapping("/simulator/inner-click/{impressionId}")
    public Result<Map<String, Object>> innerClick(@PathVariable Long impressionId, @RequestParam(defaultValue = "product") String target) {
        return Result.ok(billingService.clickInner(impressionId, target));
    }

    public record TrafficRequest(Integer searches, Double clickRate, Long seed) {
    }

    @Operation(summary = "批量造流量")
    @PostMapping("/simulator/traffic")
    public Result<Map<String, Object>> traffic(@RequestBody TrafficRequest req) {
        return Result.ok(trafficGenerator.generate(req.searches() == null ? 50 : req.searches(),
                req.clickRate() == null ? 0.1 : req.clickRate(), req.seed()));
    }

    // ---------------------------------------------------------------- 报表

    @Operation(summary = "数据报告：level=ACCOUNT/PLAN/UNIT/QA/MIDDLE_PAGE，默认最近一周（截止昨日）")
    @GetMapping("/reports")
    public Result<Map<String, Object>> report(@RequestParam(defaultValue = "PLAN") ReportService.Level level,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @RequestParam(required = false) Device device,
                                              @RequestParam(required = false) Long planId,
                                              @RequestParam(required = false) Long unitId,
                                              @RequestParam(defaultValue = "ALL") ReportService.Flow flow,
                                              @RequestParam(defaultValue = "false") boolean splitRegion) {
        return Result.ok(reportService.report(UserContext.requireAccountId(), level, from, to, device, planId, unitId, flow, splitRegion));
    }

    @Operation(summary = "下载报告（CSV，Excel 可直接打开）")
    @GetMapping("/reports/export")
    @SuppressWarnings("unchecked")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "PLAN") ReportService.Level level,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) Device device,
                                         @RequestParam(defaultValue = "ALL") ReportService.Flow flow,
                                         @RequestParam(defaultValue = "false") boolean splitRegion) {
        Map<String, Object> r = reportService.report(UserContext.requireAccountId(), level, from, to, device, null, null, flow, splitRegion);
        String header = splitRegion ? "对象,地域,展现,点击,点击率(%),消费,平均点击价格" : "对象,展现,点击,点击率(%),消费,平均点击价格";
        StringBuilder sb = new StringBuilder(BOM).append(header).append('\n');
        for (Map<String, Object> row : (List<Map<String, Object>>) r.get("rows")) {
            sb.append('"').append(String.valueOf(row.get("name")).replace("\"", "\"\"")).append('"').append(',');
            if (splitRegion) {
                sb.append(row.get("region")).append(',');
            }
            sb.append(row.get("impressions")).append(',').append(row.get("clicks")).append(',').append(row.get("ctr")).append(',')
                    .append(row.getOrDefault("cost", "")).append(',').append(row.getOrDefault("cpc", "")).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report-" + level.name().toLowerCase() + ".csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- 工具

    @Operation(summary = "历史操作记录（默认最近一周）")
    @GetMapping("/oplogs")
    public Result<PageResult<OperationLog>> oplogs(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                   @RequestParam(required = false) String targetType,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        UserContext.CurrentUser u = UserContext.get();
        Long accountId = u.accountId();
        if (accountId == null && u.role() == UserContext.Role.ADVERTISER) {
            accountId = UserContext.requireAccountId();
        }
        return Result.ok(opLogService.page(accountId, from, to, targetType, page, size));
    }

    @Operation(summary = "预算达限记录（默认最近一周）")
    @GetMapping("/budget-limits")
    public Result<PageResult<com.rebuild.phoebe.domain.entity.BudgetLimitLog>> budgetLimits(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(budgetLimitService.page(UserContext.requireAccountId(), from, to, page, size));
    }

    @Operation(summary = "出价助手")
    @GetMapping("/tools/bid-suggestion")
    public Result<Map<String, Object>> bidSuggestion(@RequestParam Long qaId) {
        return Result.ok(bidAdvisor.suggest(qaId));
    }

    public record WizardRequest(String keyword, BigDecimal budget, BigDecimal bid, Integer maxQas) {
    }

    @Operation(summary = "智能引导：一键生成计划-单元-问答-中间页草稿")
    @PostMapping("/tools/wizard")
    public Result<Map<String, Object>> wizard(@RequestBody WizardRequest req) {
        return Result.ok(wizardService.build(req.keyword(), req.budget(), req.bid(), req.maxQas() == null ? 5 : req.maxQas()));
    }

    @Operation(summary = "当前生效的业务规则与模拟参数")
    @GetMapping("/rules")
    public Result<Map<String, Object>> rules() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rules", rules);
        m.put("simulator", simulatorProperties);
        m.put("audit", auditProperties);
        return Result.ok(m);
    }
}
