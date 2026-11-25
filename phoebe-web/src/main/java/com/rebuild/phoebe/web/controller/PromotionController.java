package com.rebuild.phoebe.web.controller;

import com.rebuild.phoebe.common.Result;
import com.rebuild.phoebe.common.UserContext;
import com.rebuild.phoebe.domain.dto.ExcelImportResult;
import com.rebuild.phoebe.domain.dto.PlanRequest;
import com.rebuild.phoebe.domain.dto.PlanView;
import com.rebuild.phoebe.domain.dto.QaRequest;
import com.rebuild.phoebe.domain.dto.UnitRequest;
import com.rebuild.phoebe.domain.dto.UnitView;
import com.rebuild.phoebe.domain.entity.Plan;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.entity.Unit;
import com.rebuild.phoebe.domain.enums.AuditStatus;
import com.rebuild.phoebe.domain.enums.RunStatus;
import com.rebuild.phoebe.domain.service.PlanService;
import com.rebuild.phoebe.domain.service.QaService;
import com.rebuild.phoebe.domain.service.UnitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** 推广管理：计划、单元、问答。 */
@Tag(name = "推广管理：计划 / 单元 / 问答")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PromotionController {
    private final PlanService planService;
    private final UnitService unitService;
    private final QaService qaService;

    public record StatusRequest(RunStatus status) {
    }

    // ---------------------------------------------------------------- 计划

    @Operation(summary = "计划列表（含单元数、今日消费、不可投放原因）")
    @GetMapping("/plans")
    public Result<List<PlanView>> plans() {
        return Result.ok(planService.list(UserContext.requireAccountId()));
    }

    @GetMapping("/plans/{id}")
    public Result<PlanView> plan(@PathVariable Long id) {
        return Result.ok(planService.detail(id));
    }

    @Operation(summary = "新建计划：名称、预算(null=不限)、移动出价比例、地域、移动电话")
    @PostMapping("/plans")
    public Result<Plan> createPlan(@Valid @RequestBody PlanRequest req) {
        return Result.ok(planService.create(req));
    }

    @PutMapping("/plans/{id}")
    public Result<Plan> updatePlan(@PathVariable Long id, @Valid @RequestBody PlanRequest req) {
        return Result.ok(planService.update(id, req));
    }

    @PutMapping("/plans/{id}/status")
    public Result<Plan> planStatus(@PathVariable Long id, @RequestBody StatusRequest req) {
        return Result.ok(planService.setStatus(id, req.status()));
    }

    @DeleteMapping("/plans/{id}")
    public Result<Void> deletePlan(@PathVariable Long id) {
        planService.delete(id);
        return Result.ok();
    }

    // ---------------------------------------------------------------- 单元

    @GetMapping("/plans/{planId}/units")
    public Result<List<UnitView>> units(@PathVariable Long planId) {
        return Result.ok(unitService.listByPlan(planId));
    }

    @Operation(summary = "当前账户全部单元")
    @GetMapping("/units")
    public Result<List<UnitView>> allUnits() {
        return Result.ok(unitService.listByAccount(UserContext.requireAccountId()));
    }

    @GetMapping("/units/{id}")
    public Result<UnitView> unit(@PathVariable Long id) {
        return Result.ok(unitService.detail(id));
    }

    @Operation(summary = "新建单元：名称、出价、订阅词")
    @PostMapping("/plans/{planId}/units")
    public Result<Unit> createUnit(@PathVariable Long planId, @Valid @RequestBody UnitRequest req) {
        return Result.ok(unitService.create(planId, req));
    }

    @PutMapping("/units/{id}")
    public Result<Unit> updateUnit(@PathVariable Long id, @Valid @RequestBody UnitRequest req) {
        return Result.ok(unitService.update(id, req));
    }

    @PutMapping("/units/{id}/status")
    public Result<Unit> unitStatus(@PathVariable Long id, @RequestBody StatusRequest req) {
        return Result.ok(unitService.setStatus(id, req.status()));
    }

    @PutMapping("/units/{id}/subscribe-words")
    public Result<List<String>> subscribeWords(@PathVariable Long id, @RequestBody List<String> words) {
        return Result.ok(unitService.setSubscribeWords(id, words));
    }

    @DeleteMapping("/units/{id}")
    public Result<Void> deleteUnit(@PathVariable Long id) {
        unitService.delete(id);
        return Result.ok();
    }

    // ---------------------------------------------------------------- 问答

    @GetMapping("/units/{unitId}/qas")
    public Result<List<QuestionAnswer>> qas(@PathVariable Long unitId,
                                            @RequestParam(required = false) AuditStatus auditStatus,
                                            @RequestParam(required = false) String keyword) {
        return Result.ok(qaService.list(unitId, auditStatus, keyword));
    }

    @Operation(summary = "账户级问答列表（可按计划/单元/审核状态/关键词过滤）")
    @GetMapping("/qas")
    public Result<List<QuestionAnswer>> accountQas(@RequestParam(required = false) Long planId,
                                                   @RequestParam(required = false) Long unitId,
                                                   @RequestParam(required = false) AuditStatus auditStatus,
                                                   @RequestParam(required = false) String keyword) {
        return Result.ok(qaService.listByAccount(UserContext.requireAccountId(), planId, unitId, auditStatus, keyword));
    }

    @GetMapping("/qas/{id}")
    public Result<QuestionAnswer> qa(@PathVariable Long id) {
        return Result.ok(qaService.get(id));
    }

    @Operation(summary = "新建问答（自动送审）")
    @PostMapping("/units/{unitId}/qas")
    public Result<QuestionAnswer> createQa(@PathVariable Long unitId, @Valid @RequestBody QaRequest req) {
        return Result.ok(qaService.create(unitId, req));
    }

    @Operation(summary = "编辑问答（问题或答案变化则重新送审）")
    @PutMapping("/qas/{id}")
    public Result<QuestionAnswer> updateQa(@PathVariable Long id, @Valid @RequestBody QaRequest req) {
        return Result.ok(qaService.update(id, req));
    }

    @PutMapping("/qas/{id}/status")
    public Result<QuestionAnswer> qaStatus(@PathVariable Long id, @RequestBody StatusRequest req) {
        return Result.ok(qaService.setStatus(id, req.status()));
    }

    @DeleteMapping("/qas/{id}")
    public Result<Void> deleteQa(@PathVariable Long id) {
        qaService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "Excel 批量上传问答（第一列问题、第二列答案，单次≤100条）")
    @PostMapping(value = "/units/{unitId}/qas/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ExcelImportResult> upload(@PathVariable Long unitId, @RequestParam("file") MultipartFile file) throws IOException {
        return Result.ok(qaService.importExcel(unitId, file.getInputStream()));
    }

    @Operation(summary = "下载问答上传模板")
    @GetMapping("/qas/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=qa-template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(qaService.excelTemplate());
    }
}
