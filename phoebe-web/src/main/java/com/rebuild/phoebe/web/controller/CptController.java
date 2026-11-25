package com.rebuild.phoebe.web.controller;

import com.rebuild.phoebe.common.Result;
import com.rebuild.phoebe.common.UserContext;
import com.rebuild.phoebe.domain.entity.CptOrder;
import com.rebuild.phoebe.simulator.cpt.CptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** CPT 包词：按天包词、预付、同词同日独占，展示优先级高于 CPC [C]。 */
@Tag(name = "CPT 包词")
@RestController
@RequestMapping("/api/cpt")
@RequiredArgsConstructor
public class CptController {
    private final CptService cptService;

    @Operation(summary = "询价：日单价、总价与排期冲突（不透露其他广告主）")
    @GetMapping("/quote")
    public Result<CptService.Quote> quote(@RequestParam String keyword,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(cptService.quote(keyword, startDate, endDate));
    }

    @Operation(summary = "当前账户的 CPT 订单（含状态与展现/点击）")
    @GetMapping("/orders")
    public Result<List<CptOrder>> orders() {
        return Result.ok(cptService.list(UserContext.requireAccountId()));
    }

    public record CreateRequest(Long qaId, String keyword,
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    }

    @Operation(summary = "下单：一次性从余额扣除 天数×日单价")
    @PostMapping("/orders")
    public Result<CptOrder> create(@RequestBody CreateRequest req) {
        return Result.ok(cptService.create(req.qaId(), req.keyword(), req.startDate(), req.endDate()));
    }

    @Operation(summary = "取消：开始前全额退款；投放中退还明天起的天数")
    @PostMapping("/orders/{id}/cancel")
    public Result<CptOrder> cancel(@PathVariable Long id) {
        return Result.ok(cptService.cancel(id));
    }

    @Operation(summary = "CPT 报告：按订单统计时间范围内的展现、点击（CPT 不进入 CPC 日报）")
    @GetMapping("/report")
    public Result<List<Map<String, Object>>> report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = from == null ? LocalDate.now().minusDays(7) : from;
        LocalDate t = to == null ? LocalDate.now() : to;
        return Result.ok(cptService.report(UserContext.requireAccountId(), f, t));
    }
}
