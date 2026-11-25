package com.rebuild.phoebe.web;

import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.domain.config.AuditProperties;
import com.rebuild.phoebe.domain.config.PhoebeRules;
import com.rebuild.phoebe.domain.dto.ExcelImportResult;
import com.rebuild.phoebe.domain.dto.QaRequest;
import com.rebuild.phoebe.domain.dto.UnitRequest;
import com.rebuild.phoebe.domain.entity.AuditTask;
import com.rebuild.phoebe.domain.entity.QuestionAnswer;
import com.rebuild.phoebe.domain.entity.Unit;
import com.rebuild.phoebe.domain.enums.AuditStatus;
import com.rebuild.phoebe.domain.enums.AuditTargetType;
import com.rebuild.phoebe.domain.service.AuditService;
import com.rebuild.phoebe.domain.service.QaService;
import com.rebuild.phoebe.domain.service.UnitService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class QaServiceTest extends TestSupport {
    static final String ANSWER = "这是一段用于测试的完整回答，内容需要满足不少于八十个汉字的要求。回答应当围绕问题本身展开，"
            + "给出清晰的说明和建议，避免出现连续标点符号，也不能包含违禁词。这里继续补充一些文字，确保长度足够通过校验规则。";

    @Autowired QaService qaService;
    @Autowired UnitService unitService;
    @Autowired AuditService auditService;
    @Autowired PhoebeRules rules;
    @Autowired AuditProperties auditProperties;

    private QaRequest req(String q, String a) {
        QaRequest r = new QaRequest();
        r.setQuestion(q);
        r.setAnswer(a);
        return r;
    }

    private Unit newUnit() {
        UnitRequest ur = new UnitRequest();
        ur.setName("测试单元" + System.nanoTime());
        ur.setBid(new BigDecimal("1.00"));
        return unitService.create(1L, ur);
    }

    @Test
    void lengthAndPunctuationRules() {
        asAdvertiser(1);
        Long unitId = newUnit().getId();
        assertThatThrownBy(() -> qaService.create(unitId, req("眼霜好吗", ANSWER)))
                .isInstanceOf(BizException.class).hasMessageContaining("问题需为");
        assertThatThrownBy(() -> qaService.create(unitId, req("眼霜应该怎么挑选？", "太短的回答")))
                .hasMessageContaining("答案需为");
        assertThatThrownBy(() -> qaService.create(unitId, req("眼霜应该怎么挑选？？", ANSWER)))
                .hasMessageContaining("连续标点");
    }

    @Test
    void createSubmitsAuditAndApproveMakesItDeliverable() {
        asAdvertiser(1);
        QuestionAnswer qa = qaService.create(newUnit().getId(), req("眼霜应该怎么挑选比较好？", ANSWER));
        assertThat(qa.getAuditStatus()).isEqualTo(AuditStatus.PENDING);

        AuditTask task = auditService.page(AuditStatus.PENDING, AuditTargetType.QA, 1, 50).getRecords().stream()
                .filter(t -> t.getTargetId().equals(qa.getId())).findFirst().orElseThrow();
        asAuditor();
        auditService.approve(task.getId(), null);
        asAdvertiser(1);
        assertThat(qaService.get(qa.getId()).getAuditStatus()).isEqualTo(AuditStatus.APPROVED);

        // 修改内容 → 重新待审；只改出价 → 不送审
        QaRequest bidOnly = req(qa.getQuestion(), qa.getAnswer());
        bidOnly.setBid(new BigDecimal("2.00"));
        assertThat(qaService.update(qa.getId(), bidOnly).getAuditStatus()).isEqualTo(AuditStatus.APPROVED);
        QaRequest changed = req(qa.getQuestion(), ANSWER + "补充说明。");
        assertThat(qaService.update(qa.getId(), changed).getAuditStatus()).isEqualTo(AuditStatus.PENDING);
    }

    @Test
    void bannedWordIsRejectedByMachinePreCheck() {
        asAdvertiser(1);
        QuestionAnswer qa = qaService.create(newUnit().getId(), req("哪款眼霜是最好的选择呢？", ANSWER));
        assertThat(qaService.get(qa.getId()).getAuditStatus()).isEqualTo(AuditStatus.REJECTED);
        assertThat(qaService.get(qa.getId()).getRejectReason()).contains("违禁词");
    }

    @Test
    void autoApproveMode() {
        asAdvertiser(1);
        auditProperties.setAutoApprove(true);
        try {
            QuestionAnswer qa = qaService.create(newUnit().getId(), req("眼霜白天可以使用吗？", ANSWER));
            assertThat(qaService.get(qa.getId()).getAuditStatus()).isEqualTo(AuditStatus.APPROVED);
        } finally {
            auditProperties.setAutoApprove(false);
        }
    }

    @Test
    void perUnitLimitIsConfigurable() {
        asAdvertiser(1);
        int old = rules.getMaxQasPerUnit();
        rules.setMaxQasPerUnit(2);
        try {
            Long unitId = newUnit().getId();
            qaService.create(unitId, req("眼霜第一个测试问题？", ANSWER));
            qaService.create(unitId, req("眼霜第二个测试问题？", ANSWER));
            assertThatThrownBy(() -> qaService.create(unitId, req("眼霜第三个测试问题？", ANSWER)))
                    .hasMessageContaining("最多 2 条问答");
        } finally {
            rules.setMaxQasPerUnit(old);
        }
    }

    @Test
    void excelImportCapsAt100RowsAndReportsErrors() throws Exception {
        asAdvertiser(1);
        int old = rules.getMaxQasPerUnit();
        rules.setMaxQasPerUnit(500);
        try {
            Long unitId = newUnit().getId();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (XSSFWorkbook wb = new XSSFWorkbook()) {
                Sheet s = wb.createSheet();
                Row h = s.createRow(0);
                h.createCell(0).setCellValue("问题");
                h.createCell(1).setCellValue("答案");
                for (int i = 1; i <= 105; i++) {
                    Row r = s.createRow(i);
                    r.createCell(0).setCellValue(i == 3 ? "太短" : "批量上传测试问题第" + i + "条");
                    r.createCell(1).setCellValue(ANSWER);
                }
                wb.write(out);
            }
            ExcelImportResult result = qaService.importExcel(unitId, new ByteArrayInputStream(out.toByteArray()));
            assertThat(result.getTotalRows()).isEqualTo(105);
            assertThat(result.getIgnored()).isEqualTo(5);
            assertThat(result.getSuccess()).isEqualTo(99);
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getRow()).isEqualTo(4);
            List<QuestionAnswer> list = qaService.list(unitId, null, null);
            assertThat(list).hasSize(99);
        } finally {
            rules.setMaxQasPerUnit(old);
        }
    }

    @Test
    void cannotTouchOtherAccountsUnit() {
        asAdvertiser(2);
        assertThatThrownBy(() -> qaService.list(1L, null, null)).hasMessageContaining("无权访问");
    }
}
