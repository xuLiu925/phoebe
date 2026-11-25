package com.rebuild.phoebe.web.controller;

import com.rebuild.phoebe.common.Result;
import com.rebuild.phoebe.common.UserContext;
import com.rebuild.phoebe.domain.dto.CreativeRequest;
import com.rebuild.phoebe.domain.dto.MiddlePageDraftRequest;
import com.rebuild.phoebe.domain.dto.MiddlePageView;
import com.rebuild.phoebe.domain.entity.Creative;
import com.rebuild.phoebe.domain.entity.MiddlePageVersion;
import com.rebuild.phoebe.domain.enums.Device;
import com.rebuild.phoebe.domain.enums.RunStatus;
import com.rebuild.phoebe.domain.service.CreativeService;
import com.rebuild.phoebe.domain.service.MiddlePageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 物料：中间页、高级创意。 */
@Tag(name = "物料：中间页 / 创意")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MaterialController {
    private final MiddlePageService middlePageService;
    private final CreativeService creativeService;

    // ---------------------------------------------------------------- 中间页

    @GetMapping("/plans/{planId}/middle-pages")
    public Result<List<MiddlePageView>> middlePages(@PathVariable Long planId) {
        return Result.ok(middlePageService.listByPlan(planId));
    }

    @Operation(summary = "账户级中间页列表（可按计划过滤）")
    @GetMapping("/middle-pages")
    public Result<List<MiddlePageView>> accountPages(@RequestParam(required = false) Long planId) {
        return Result.ok(middlePageService.listByAccount(UserContext.requireAccountId(), planId));
    }

    @Operation(summary = "账户级高级创意列表")
    @GetMapping("/creatives")
    public Result<List<Creative>> accountCreatives(@RequestParam(required = false) Long planId, @RequestParam(required = false) Long unitId) {
        return Result.ok(creativeService.listByAccount(UserContext.requireAccountId(), planId, unitId));
    }

    @Operation(summary = "当前账户所有有效中间页（导入候选）")
    @GetMapping("/middle-pages/valid")
    public Result<List<MiddlePageView>> validPages() {
        return Result.ok(middlePageService.listValidByAccount(UserContext.requireAccountId()));
    }

    public record CreatePageRequest(String name) {
    }

    @Operation(summary = "新建中间页（每计划最多 2 套，默认值取账户企业信息）")
    @PostMapping("/plans/{planId}/middle-pages")
    public Result<MiddlePageView> createPage(@PathVariable Long planId, @RequestBody(required = false) CreatePageRequest req) {
        return Result.ok(middlePageService.create(planId, req == null ? null : req.name()));
    }

    @GetMapping("/middle-pages/{id}")
    public Result<MiddlePageView> page(@PathVariable Long id) {
        return Result.ok(middlePageService.detail(id));
    }

    @Operation(summary = "全部保存 / 分模块保存（不送审）")
    @PutMapping("/middle-pages/{id}/draft")
    public Result<MiddlePageView> saveDraft(@PathVariable Long id, @RequestBody MiddlePageDraftRequest req) {
        return Result.ok(middlePageService.saveDraft(id, req));
    }

    @Operation(summary = "全部保存并提交审核（需所有模块完整）")
    @PostMapping("/middle-pages/{id}/submit")
    public Result<MiddlePageView> submit(@PathVariable Long id, @RequestBody(required = false) MiddlePageDraftRequest req) {
        return Result.ok(middlePageService.saveAndSubmit(id, req));
    }

    @Operation(summary = "由 PC 中间页导入生成无线中间页")
    @PostMapping("/middle-pages/{id}/copy-pc-to-mobile")
    public Result<MiddlePageView> copyPcToMobile(@PathVariable Long id) {
        return Result.ok(middlePageService.copyPcToMobile(id));
    }

    @Operation(summary = "导入其他有效中间页（覆盖当前内容）")
    @PostMapping("/middle-pages/{id}/import")
    public Result<MiddlePageView> importFrom(@PathVariable Long id, @RequestParam Long fromId) {
        return Result.ok(middlePageService.importFrom(id, fromId));
    }

    @Operation(summary = "启用（同计划其余中间页自动停用）")
    @PostMapping("/middle-pages/{id}/enable")
    public Result<MiddlePageView> enable(@PathVariable Long id) {
        return Result.ok(middlePageService.enable(id));
    }

    @PostMapping("/middle-pages/{id}/disable")
    public Result<MiddlePageView> disable(@PathVariable Long id) {
        return Result.ok(middlePageService.disable(id));
    }

    @GetMapping("/middle-pages/{id}/versions")
    public Result<List<MiddlePageVersion>> versions(@PathVariable Long id) {
        return Result.ok(middlePageService.versions(id));
    }

    @Operation(summary = "预览：which=online（线上版）/ latest（最新编辑版）")
    @GetMapping("/middle-pages/{id}/preview")
    public Result<Map<String, Object>> preview(@PathVariable Long id,
                                               @RequestParam(defaultValue = "PC") Device device,
                                               @RequestParam(defaultValue = "latest") String which) {
        return Result.ok(middlePageService.preview(id, device, which));
    }

    @DeleteMapping("/middle-pages/{id}")
    public Result<Void> deletePage(@PathVariable Long id) {
        middlePageService.delete(id);
        return Result.ok();
    }

    // ---------------------------------------------------------------- 创意

    @GetMapping("/units/{unitId}/creatives")
    public Result<List<Creative>> creatives(@PathVariable Long unitId) {
        return Result.ok(creativeService.list(unitId));
    }

    @GetMapping("/creatives/{id}")
    public Result<Creative> creative(@PathVariable Long id) {
        return Result.ok(creativeService.get(id));
    }

    @Operation(summary = "新建高级创意（产品图+描述，可关联同单元问答）")
    @PostMapping("/units/{unitId}/creatives")
    public Result<Creative> createCreative(@PathVariable Long unitId, @Valid @RequestBody CreativeRequest req) {
        return Result.ok(creativeService.create(unitId, req));
    }

    @PutMapping("/creatives/{id}")
    public Result<Creative> updateCreative(@PathVariable Long id, @Valid @RequestBody CreativeRequest req) {
        return Result.ok(creativeService.update(id, req));
    }

    @PutMapping("/creatives/{id}/qas")
    public Result<Creative> bindQas(@PathVariable Long id, @RequestBody List<Long> qaIds) {
        return Result.ok(creativeService.setQas(id, qaIds));
    }

    public record StatusRequest(RunStatus status) {
    }

    @PutMapping("/creatives/{id}/status")
    public Result<Creative> creativeStatus(@PathVariable Long id, @RequestBody StatusRequest req) {
        return Result.ok(creativeService.setStatus(id, req.status()));
    }

    @DeleteMapping("/creatives/{id}")
    public Result<Void> deleteCreative(@PathVariable Long id) {
        creativeService.delete(id);
        return Result.ok();
    }
}
