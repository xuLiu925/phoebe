package com.rebuild.phoebe.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.common.Evidence;
import com.rebuild.phoebe.common.EvidenceLevel;
import com.rebuild.phoebe.domain.config.PhoebeRules;
import com.rebuild.phoebe.domain.dto.ExcelImportResult;
import com.rebuild.phoebe.domain.dto.QaRequest;
import com.rebuild.phoebe.domain.entity.AuditTask;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.entity.Unit;
import com.rebuild.phoebe.domain.enums.AuditStatus;
import com.rebuild.phoebe.domain.enums.AuditTargetType;
import com.rebuild.phoebe.domain.enums.QaSource;
import com.rebuild.phoebe.domain.enums.RunStatus;
import com.rebuild.phoebe.domain.mapper.QuestionAnswerMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 问答：创建、编辑、启停、删除、Excel 批量上传、审核回写。
 * 问题与答案作为一个完整业务对象，任何一方修改都重新送审 [I]。
 */
@Service
@RequiredArgsConstructor
public class QaService implements AuditTargetHandler {
    private final QuestionAnswerMapper mapper;
    private final PhoebeRules rules;
    private final Guard guard;
    private final ContentChecker checker;
    private final AuditService auditService;
    private final OpLogService opLog;

    public List<QuestionAnswer> list(Long unitId, AuditStatus auditStatus, String keyword) {
        guard.unit(unitId);
        return mapper.selectList(new LambdaQueryWrapper<QuestionAnswer>()
                .eq(QuestionAnswer::getUnitId, unitId)
                .eq(auditStatus != null, QuestionAnswer::getAuditStatus, auditStatus)
                .like(keyword != null && !keyword.isBlank(), QuestionAnswer::getQuestion, keyword)
                .orderByDesc(QuestionAnswer::getId));
    }

    /** 账户级问答列表（推广管理“知识问答”层级 tab），可按计划/单元过滤。 */
    public List<QuestionAnswer> listByAccount(Long accountId, Long planId, Long unitId, AuditStatus auditStatus, String keyword) {
        return mapper.selectList(new LambdaQueryWrapper<QuestionAnswer>()
                .eq(QuestionAnswer::getAccountId, accountId)
                .eq(planId != null, QuestionAnswer::getPlanId, planId)
                .eq(unitId != null, QuestionAnswer::getUnitId, unitId)
                .eq(auditStatus != null, QuestionAnswer::getAuditStatus, auditStatus)
                .and(keyword != null && !keyword.isBlank(), w -> w.like(QuestionAnswer::getQuestion, keyword).or().like(QuestionAnswer::getAnswer, keyword))
                .orderByDesc(QuestionAnswer::getId));
    }

    public QuestionAnswer get(Long id) {
        return guard.qa(id);
    }

    @Transactional
    public QuestionAnswer create(Long unitId, QaRequest req) {
        Unit unit = guard.unit(unitId);
        return doCreate(unit, req);
    }

    @Evidence(level = EvidenceLevel.PUBLIC, source = "B", note = "每个单元下最多有100条问答")
    private QuestionAnswer doCreate(Unit unit, QaRequest req) {
        long count = mapper.selectCount(new LambdaQueryWrapper<QuestionAnswer>().eq(QuestionAnswer::getUnitId, unit.getId()));
        if (count >= rules.getMaxQasPerUnit()) {
            throw BizException.limit("每个单元最多 " + rules.getMaxQasPerUnit() + " 条问答");
        }
        String question = req.getQuestion().trim();
        validate(question, req.getAnswer());
        ensureUniqueQuestion(unit.getId(), question, null);
        UnitService.checkBid(req.getBid(), rules);

        QuestionAnswer qa = new QuestionAnswer();
        qa.setAccountId(unit.getAccountId());
        qa.setPlanId(unit.getPlanId());
        qa.setUnitId(unit.getId());
        qa.setQuestion(question);
        qa.setAnswer(req.getAnswer().trim());
        qa.setBid(req.getBid());
        qa.setSource(req.getSource() == null ? QaSource.MANUAL : req.getSource());
        qa.setAuditStatus(AuditStatus.PENDING);
        qa.setStatus(RunStatus.ACTIVE);
        qa.setAuditVersion(1);
        mapper.insert(qa);
        submitAudit(qa);
        opLog.log(qa.getAccountId(), "QA", qa.getId(), "CREATE", question);
        return mapper.selectById(qa.getId());
    }

    @Transactional
    public QuestionAnswer update(Long id, QaRequest req) {
        QuestionAnswer qa = guard.qa(id);
        String question = req.getQuestion().trim();
        String answer = req.getAnswer().trim();
        UnitService.checkBid(req.getBid(), rules);
        boolean contentChanged = !Objects.equals(question, qa.getQuestion()) || !Objects.equals(answer, qa.getAnswer());
        qa.setBid(req.getBid());
        if (contentChanged) {
            validate(question, answer);
            ensureUniqueQuestion(qa.getUnitId(), question, id);
            qa.setQuestion(question);
            qa.setAnswer(answer);
            qa.setAuditVersion(qa.getAuditVersion() + 1);
            // [U] 原产品修改已通过问答后旧版是否继续展现未知；此处下线待审
            qa.setAuditStatus(AuditStatus.PENDING);
            qa.setRejectReason(null);
        }
        mapper.updateById(qa);
        if (contentChanged) {
            submitAudit(qa);
        }
        opLog.log(qa.getAccountId(), "QA", id, contentChanged ? "UPDATE_CONTENT" : "UPDATE_BID", req);
        return mapper.selectById(id);
    }

    @Transactional
    public QuestionAnswer setStatus(Long id, RunStatus status) {
        QuestionAnswer qa = guard.qa(id);
        qa.setStatus(status);
        mapper.updateById(qa);
        opLog.log(qa.getAccountId(), "QA", id, status == RunStatus.ACTIVE ? "ENABLE" : "PAUSE", null);
        return qa;
    }

    @Transactional
    public void delete(Long id) {
        QuestionAnswer qa = guard.qa(id);
        mapper.deleteById(id);
        opLog.log(qa.getAccountId(), "QA", id, "DELETE", qa.getQuestion());
    }

    private void validate(String question, String answer) {
        checker.checkQuestion(question);
        checker.checkAnswer(answer);
    }

    private void ensureUniqueQuestion(Long unitId, String question, Long excludeId) {
        long dup = mapper.selectCount(new LambdaQueryWrapper<QuestionAnswer>()
                .eq(QuestionAnswer::getUnitId, unitId).eq(QuestionAnswer::getQuestion, question)
                .ne(excludeId != null, QuestionAnswer::getId, excludeId));
        if (dup > 0) {
            throw BizException.badRequest("该单元下已存在相同问题");
        }
    }

    private void submitAudit(QuestionAnswer qa) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("question", qa.getQuestion());
        snapshot.put("answer", qa.getAnswer());
        auditService.submit(qa.getAccountId(), AuditTargetType.QA, qa.getId(), qa.getAuditVersion(), qa.getQuestion(), snapshot, List.of());
    }

    // ------------------------------------------------------------------ Excel

    /**
     * Excel 批量上传 [P:A,B]：第一列问题、第二列答案；一次最多 100 条，超出部分忽略；逐行校验并返回错误。
     * 首行若为“问题/答案”表头则跳过 [I]。
     */
    @Transactional
    public ExcelImportResult importExcel(Long unitId, InputStream in) {
        Unit unit = guard.unit(unitId);
        ExcelImportResult result = new ExcelImportResult();
        DataFormatter fmt = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Set<String> seen = new HashSet<>();
            int dataRows = 0;
            for (Row row : sheet) {
                String q = fmt.formatCellValue(row.getCell(0)).trim();
                String a = fmt.formatCellValue(row.getCell(1)).trim();
                if (q.isEmpty() && a.isEmpty()) {
                    continue;
                }
                if (row.getRowNum() == 0 && q.contains("问题")) {
                    continue;
                }
                dataRows++;
                if (dataRows > rules.getExcelMaxRows()) {
                    result.setIgnored(result.getIgnored() + 1);
                    continue;
                }
                int rowNo = row.getRowNum() + 1;
                if (!seen.add(q)) {
                    result.getErrors().add(new ExcelImportResult.RowError(rowNo, q, "文件内问题重复"));
                    continue;
                }
                try {
                    QaRequest req = new QaRequest();
                    req.setQuestion(q);
                    req.setAnswer(a);
                    req.setSource(QaSource.EXCEL);
                    doCreate(unit, req);
                    result.setSuccess(result.getSuccess() + 1);
                } catch (BizException e) {
                    result.getErrors().add(new ExcelImportResult.RowError(rowNo, q, e.getMessage()));
                }
            }
            result.setTotalRows(dataRows);
        } catch (IOException | RuntimeException e) {
            if (e instanceof BizException be) {
                throw be;
            }
            throw BizException.badRequest("无法解析 Excel 文件：" + e.getMessage());
        }
        opLog.log(unit.getAccountId(), "QA", unitId, "EXCEL_IMPORT",
                "成功 " + result.getSuccess() + "，失败 " + result.getErrors().size() + "，忽略 " + result.getIgnored());
        return result;
    }

    public byte[] excelTemplate() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("问答");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("问题");
            h.createCell(1).setCellValue("答案");
            Row r = s.createRow(1);
            r.createCell(0).setCellValue("示例：眼霜应该怎么挑选？");
            r.createCell(1).setCellValue("示例答案：请填写不少于80个汉字的完整回答，问题与答案中不能出现连续标点，也不能包含违禁词。"
                    + "每个文件最多上传100条问答，超出部分会被忽略，上传后系统会逐行校验并返回失败原因。");
            s.setColumnWidth(0, 40 * 256);
            s.setColumnWidth(1, 100 * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ audit

    @Override
    public AuditTargetType type() {
        return AuditTargetType.QA;
    }

    @Override
    public void onApproved(AuditTask task) {
        QuestionAnswer qa = mapper.selectById(task.getTargetId());
        if (qa != null && Objects.equals(qa.getAuditVersion(), task.getTargetVersion())) {
            qa.setAuditStatus(AuditStatus.APPROVED);
            qa.setRejectReason(null);
            mapper.updateById(qa);
        }
    }

    @Override
    public void onRejected(AuditTask task, String reason) {
        QuestionAnswer qa = mapper.selectById(task.getTargetId());
        if (qa != null && Objects.equals(qa.getAuditVersion(), task.getTargetVersion())) {
            qa.setAuditStatus(AuditStatus.REJECTED);
            qa.setRejectReason(reason);
            mapper.updateById(qa);
        }
    }
}
