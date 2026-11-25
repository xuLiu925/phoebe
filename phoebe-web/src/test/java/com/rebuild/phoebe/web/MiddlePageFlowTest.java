package com.rebuild.phoebe.web;

import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.domain.dto.MiddlePageView;
import com.rebuild.phoebe.domain.entity.AuditTask;
import com.rebuild.phoebe.domain.enums.AuditStatus;
import com.rebuild.phoebe.domain.enums.AuditTargetType;
import com.rebuild.phoebe.domain.dto.MiddlePageDraftRequest;
import com.rebuild.phoebe.domain.middlepage.PageContent;
import com.rebuild.phoebe.domain.service.AuditService;
import com.rebuild.phoebe.domain.service.MiddlePageService;
import com.rebuild.phoebe.domain.service.PlanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 中间页规则 [P:A,B]：≤2 套、启用 1 套、需审核通过、修改期间旧版在线、仅导入有效页。 */
@SpringBootTest
@Transactional
class MiddlePageFlowTest extends TestSupport {
    @Autowired MiddlePageService service;
    @Autowired AuditService auditService;
    @Autowired PlanService planService;

    private void approveLatest(Long pageId) {
        Long versionId = service.detail(pageId).getLatest().getId();
        AuditTask t = auditService.page(AuditStatus.PENDING, AuditTargetType.MIDDLE_PAGE, 1, 50).getRecords().stream()
                .filter(x -> x.getTargetId().equals(versionId)).findFirst().orElseThrow();
        auditService.approve(t.getId(), null);
    }

    @Test
    void fullLifecycle() {
        asAdvertiser(1);
        // 计划2 没有中间页 → 不可投放
        assertThat(planService.detail(2L).getBlockers()).contains("无启用且审核通过的中间页");

        MiddlePageView a = service.create(2L, "A");
        MiddlePageView b = service.create(2L, "B");
        assertThatThrownBy(() -> service.create(2L, "C")).isInstanceOf(BizException.class).hasMessageContaining("最多创建 2 套");

        // 默认值取账户企业信息
        assertThat(a.getLatest().getPcContent().getCompanyInfo().getName()).isEqualTo("青禾美妆（演示）");
        // 模块未完成不能提交
        assertThatThrownBy(() -> service.saveAndSubmit(a.getPage().getId(), null)).hasMessageContaining("尚未填写完整");
        // 未审核通过不能启用
        assertThatThrownBy(() -> service.enable(a.getPage().getId())).hasMessageContaining("审核通过");
        // 只能导入有效中间页
        assertThatThrownBy(() -> service.importFrom(a.getPage().getId(), b.getPage().getId())).hasMessageContaining("仅支持导入");

        // 导入计划1 的有效中间页 → 提交 → 审核通过 → 启用
        service.importFrom(a.getPage().getId(), 1L);
        assertThat(service.saveAndSubmit(a.getPage().getId(), null).getStatus()).isEqualTo("PENDING");
        assertThatThrownBy(() -> service.saveDraft(a.getPage().getId(), new MiddlePageDraftRequest())).hasMessageContaining("审核中");
        approveLatest(a.getPage().getId());
        MiddlePageView valid = service.enable(a.getPage().getId());
        assertThat(valid.getStatus()).isEqualTo("VALID");
        Long v1 = valid.getOnline().getId();
        assertThat(planService.detail(2L).getBlockers()).doesNotContain("无启用且审核通过的中间页");

        // 修改已启用中间页：新版审核前线上仍为旧版
        MiddlePageDraftRequest edit = new MiddlePageDraftRequest();
        PageContent pc = valid.getOnline().getPcContent();
        pc.getCompanyInfo().setIntro("新版简介");
        edit.setPc(pc);
        MiddlePageView editing = service.saveDraft(a.getPage().getId(), edit);
        assertThat(editing.getStatus()).isEqualTo("VALID_EDITING");
        assertThat(editing.getOnline().getId()).isEqualTo(v1);
        assertThat(editing.getLatest().getVersionNo()).isEqualTo(2);
        service.saveAndSubmit(a.getPage().getId(), null);
        assertThat(service.detail(a.getPage().getId()).getStatus()).isEqualTo("VALID_PENDING");
        assertThat(service.publicPreview(a.getPage().getId(), com.rebuild.phoebe.domain.enums.Device.PC).get("versionNo")).isEqualTo(1);
        approveLatest(a.getPage().getId());
        MiddlePageView after = service.detail(a.getPage().getId());
        assertThat(after.getOnline().getVersionNo()).isEqualTo(2);
        assertThat(after.getOnline().getPcContent().getCompanyInfo().getIntro()).isEqualTo("新版简介");

        // 第二套审核通过后启用 → 第一套自动停用
        service.importFrom(b.getPage().getId(), a.getPage().getId());
        service.saveAndSubmit(b.getPage().getId(), null);
        approveLatest(b.getPage().getId());
        service.enable(b.getPage().getId());
        assertThat(service.detail(a.getPage().getId()).getPage().getEnabled()).isFalse();
        assertThat(service.detail(b.getPage().getId()).getPage().getEnabled()).isTrue();
        // 启用中的不能删除
        assertThatThrownBy(() -> service.delete(b.getPage().getId())).hasMessageContaining("启用中");
    }

    @Test
    void copyPcToMobile() {
        asAdvertiser(1);
        MiddlePageView a = service.create(2L, "A");
        MiddlePageView v = service.copyPcToMobile(a.getPage().getId());
        assertThat(v.getLatest().getMobileContent().getCompanyInfo().getName()).isEqualTo("青禾美妆（演示）");
    }
}
