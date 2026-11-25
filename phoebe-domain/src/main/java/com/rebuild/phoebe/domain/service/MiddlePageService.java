package com.rebuild.phoebe.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.common.Evidence;
import com.rebuild.phoebe.common.EvidenceLevel;
import com.rebuild.phoebe.domain.config.PhoebeRules;
import com.rebuild.phoebe.domain.dto.MiddlePageDraftRequest;
import com.rebuild.phoebe.domain.dto.MiddlePageView;
import com.rebuild.phoebe.domain.entity.Account;
import com.rebuild.phoebe.domain.entity.AuditTask;
import com.rebuild.phoebe.domain.entity.MiddlePage;
import com.rebuild.phoebe.domain.entity.MiddlePageVersion;
import com.rebuild.phoebe.domain.entity.Plan;
import com.rebuild.phoebe.domain.enums.AuditStatus;
import com.rebuild.phoebe.domain.enums.AuditTargetType;
import com.rebuild.phoebe.domain.enums.Device;
import com.rebuild.phoebe.domain.enums.PcStyle;
import com.rebuild.phoebe.domain.mapper.AccountMapper;
import com.rebuild.phoebe.domain.mapper.MiddlePageMapper;
import com.rebuild.phoebe.domain.mapper.MiddlePageVersionMapper;
import com.rebuild.phoebe.domain.middlepage.PageContent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 中间页：分模块编辑、全部保存、保存并提交、PC→无线导入、导入其他中间页、启用、预览、版本。
 * <p>
 * 规则来源 [P:A]：
 * <ul>
 *   <li>一个计划可创建 2 个中间页，同一时间只能启用 1 个（配置化）</li>
 *   <li>需完成所有模块才算创建完成；“全部保存”仅保存，“全部保存并提交”送审</li>
 *   <li>必须审核通过（有效）才能使用</li>
 *   <li>修改已启用中间页：新版审核通过前展示上一审核通过版本，通过后自动替换</li>
 *   <li>仅支持导入有效中间页，导入覆盖已填内容</li>
 * </ul>
 * “审核中不可编辑” [I]。
 */
@Service
@RequiredArgsConstructor
public class MiddlePageService implements AuditTargetHandler {
    private final MiddlePageMapper pageMapper;
    private final MiddlePageVersionMapper versionMapper;
    private final AccountMapper accountMapper;
    private final PhoebeRules rules;
    private final Guard guard;
    private final ContentChecker checker;
    private final AuditService auditService;
    private final OpLogService opLog;
    private final ObjectMapper objectMapper;

    public List<MiddlePageView> listByPlan(Long planId) {
        guard.plan(planId);
        return pageMapper.selectList(new LambdaQueryWrapper<MiddlePage>().eq(MiddlePage::getPlanId, planId).orderByAsc(MiddlePage::getId))
                .stream().map(this::view).collect(Collectors.toList());
    }

    /** 账户级中间页列表（推广管理“中间页”层级 tab）。 */
    public List<MiddlePageView> listByAccount(Long accountId, Long planId) {
        return pageMapper.selectList(new LambdaQueryWrapper<MiddlePage>()
                        .eq(MiddlePage::getAccountId, accountId)
                        .eq(planId != null, MiddlePage::getPlanId, planId)
                        .orderByDesc(MiddlePage::getId))
                .stream().map(this::view).collect(Collectors.toList());
    }

    /** 当前账户所有有效中间页（导入候选）。 */
    public List<MiddlePageView> listValidByAccount(Long accountId) {
        return pageMapper.selectList(new LambdaQueryWrapper<MiddlePage>()
                        .eq(MiddlePage::getAccountId, accountId).isNotNull(MiddlePage::getOnlineVersionId))
                .stream().map(this::view).collect(Collectors.toList());
    }

    public MiddlePageView detail(Long id) {
        return view(guard.middlePage(id));
    }

    @Transactional
    @Evidence(level = EvidenceLevel.PUBLIC, source = "A,B", note = "一个计划下最多 2 套中间页")
    public MiddlePageView create(Long planId, String name) {
        Plan plan = guard.plan(planId);
        long count = pageMapper.selectCount(new LambdaQueryWrapper<MiddlePage>().eq(MiddlePage::getPlanId, planId));
        if (count >= rules.getMaxMiddlePagesPerPlan()) {
            throw BizException.limit("每个计划最多创建 " + rules.getMaxMiddlePagesPerPlan() + " 套中间页");
        }
        MiddlePage page = new MiddlePage();
        page.setAccountId(plan.getAccountId());
        page.setPlanId(planId);
        page.setName(name == null || name.isBlank() ? plan.getName() + "-中间页" + (count + 1) : name.trim());
        page.setEnabled(false);
        pageMapper.insert(page);

        PageContent defaults = defaults(accountMapper.selectById(plan.getAccountId()));
        MiddlePageVersion v = new MiddlePageVersion();
        v.setPageId(page.getId());
        v.setVersionNo(1);
        v.setPcStyle(PcStyle.IMAGE_TEXT);
        v.setPcContent(defaults);
        v.setMobileContent(null);
        v.setAuditStatus(AuditStatus.DRAFT);
        versionMapper.insert(v);
        page.setLatestVersionId(v.getId());
        pageMapper.updateById(page);
        opLog.log(page.getAccountId(), "MIDDLE_PAGE", page.getId(), "CREATE", page.getName());
        return view(page);
    }

    /** 默认值 [P:E]：企业名称取客户企业名；网址字段默认官网；咨询链接默认商桥链接；咨询电话不自动填充。 */
    @Evidence(level = EvidenceLevel.PUBLIC, source = "E")
    private PageContent defaults(Account a) {
        PageContent c = new PageContent();
        if (a != null) {
            c.getCompanyInfo().setName(a.getCompanyName());
            c.getCompanyInfo().setWebsite(a.getWebsite());
            c.getCompanyInfo().setConsultUrl(a.getConsultUrl());
        }
        return c;
    }

    /** 全部保存 / 分模块保存：不送审。 */
    @Transactional
    public MiddlePageView saveDraft(Long id, MiddlePageDraftRequest req) {
        MiddlePage page = guard.middlePage(id);
        MiddlePageVersion draft = editableVersion(page);
        if (req.getPcStyle() != null) {
            draft.setPcStyle(req.getPcStyle());
        }
        if (req.getPc() != null) {
            draft.setPcContent(req.getPc());
        }
        if (req.getMobile() != null) {
            draft.setMobileContent(req.getMobile());
        }
        versionMapper.updateById(draft);
        if (req.getName() != null && !req.getName().isBlank()) {
            page.setName(req.getName().trim());
            pageMapper.updateById(page);
        }
        opLog.log(page.getAccountId(), "MIDDLE_PAGE", id, "SAVE_DRAFT", "v" + draft.getVersionNo());
        return view(pageMapper.selectById(id));
    }

    /** 全部保存并提交：所有模块必须填写完整。 */
    @Transactional
    public MiddlePageView saveAndSubmit(Long id, MiddlePageDraftRequest req) {
        if (req != null) {
            saveDraft(id, req);
        }
        MiddlePage page = guard.middlePage(id);
        MiddlePageVersion v = versionMapper.selectById(page.getLatestVersionId());
        if (v.getAuditStatus() == AuditStatus.PENDING) {
            throw BizException.state("中间页正在审核中");
        }
        if (v.getAuditStatus() == AuditStatus.APPROVED) {
            throw BizException.state("当前版本已审核通过，无需重复提交");
        }
        List<String> missing = missing(v);
        if (!missing.isEmpty()) {
            throw BizException.badRequest("中间页尚未填写完整：" + String.join("、", missing));
        }
        v.setAuditStatus(AuditStatus.PENDING);
        v.setRejectReason(null);
        v.setSubmittedAt(LocalDateTime.now());
        versionMapper.updateById(v);

        Account account = accountMapper.selectById(page.getAccountId());
        List<String> urls = new ArrayList<>();
        collectUrls(v.getPcContent(), urls);
        collectUrls(v.getMobileContent(), urls);
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("pageId", page.getId());
        snap.put("versionNo", v.getVersionNo());
        snap.put("pcStyle", v.getPcStyle());
        snap.put("pc", v.getPcContent());
        snap.put("mobile", v.getMobileContent());
        auditService.submit(page.getAccountId(), AuditTargetType.MIDDLE_PAGE, v.getId(), v.getVersionNo(),
                page.getName() + " v" + v.getVersionNo(), snap,
                checker.domainWarnings(account == null ? null : account.getWebsite(), urls));
        opLog.log(page.getAccountId(), "MIDDLE_PAGE", id, "SUBMIT", "v" + v.getVersionNo());
        return view(pageMapper.selectById(id));
    }

    /** 编辑完成 PC 中间页后，一键导入生成无线中间页 [P:A,E]。 */
    @Transactional
    public MiddlePageView copyPcToMobile(Long id) {
        MiddlePage page = guard.middlePage(id);
        MiddlePageVersion draft = editableVersion(page);
        if (draft.getPcContent() == null) {
            throw BizException.state("请先填写 PC 中间页");
        }
        draft.setMobileContent(deepCopy(draft.getPcContent()));
        versionMapper.updateById(draft);
        opLog.log(page.getAccountId(), "MIDDLE_PAGE", id, "COPY_PC_TO_MOBILE", null);
        return view(pageMapper.selectById(id));
    }

    /** 导入其他有效中间页内容，覆盖当前已填写内容 [P:A]。 */
    @Transactional
    public MiddlePageView importFrom(Long id, Long fromId) {
        MiddlePage page = guard.middlePage(id);
        MiddlePage from = guard.middlePage(fromId);
        if (from.getOnlineVersionId() == null) {
            throw BizException.state("仅支持导入审核通过（有效）的中间页");
        }
        MiddlePageVersion src = versionMapper.selectById(from.getOnlineVersionId());
        MiddlePageVersion draft = editableVersion(page);
        draft.setPcStyle(src.getPcStyle());
        draft.setPcContent(deepCopy(src.getPcContent()));
        draft.setMobileContent(deepCopy(src.getMobileContent()));
        versionMapper.updateById(draft);
        opLog.log(page.getAccountId(), "MIDDLE_PAGE", id, "IMPORT", "from #" + fromId);
        return view(pageMapper.selectById(id));
    }

    @Transactional
    @Evidence(level = EvidenceLevel.PUBLIC, source = "A,B", note = "同一时间只能启用一套")
    public MiddlePageView enable(Long id) {
        MiddlePage page = guard.middlePage(id);
        if (page.getOnlineVersionId() == null) {
            throw BizException.state("中间页必须审核通过（有效）后才能启用");
        }
        List<MiddlePage> enabled = pageMapper.selectList(new LambdaQueryWrapper<MiddlePage>()
                .eq(MiddlePage::getPlanId, page.getPlanId()).eq(MiddlePage::getEnabled, true).ne(MiddlePage::getId, id));
        if (enabled.size() >= rules.getMaxEnabledMiddlePagesPerPlan()) {
            if (rules.getMaxEnabledMiddlePagesPerPlan() == 1) {
                // 单启用规则下，启用新的一套自动停用其他
                pageMapper.update(null, new LambdaUpdateWrapper<MiddlePage>()
                        .set(MiddlePage::getEnabled, false).eq(MiddlePage::getPlanId, page.getPlanId()).ne(MiddlePage::getId, id));
            } else {
                throw BizException.limit("每个计划同时最多启用 " + rules.getMaxEnabledMiddlePagesPerPlan() + " 套中间页");
            }
        }
        page.setEnabled(true);
        pageMapper.updateById(page);
        opLog.log(page.getAccountId(), "MIDDLE_PAGE", id, "ENABLE", null);
        return view(page);
    }

    @Transactional
    public MiddlePageView disable(Long id) {
        MiddlePage page = guard.middlePage(id);
        page.setEnabled(false);
        pageMapper.updateById(page);
        opLog.log(page.getAccountId(), "MIDDLE_PAGE", id, "DISABLE", null);
        return view(page);
    }

    @Transactional
    public void delete(Long id) {
        MiddlePage page = guard.middlePage(id);
        if (Boolean.TRUE.equals(page.getEnabled())) {
            throw BizException.state("启用中的中间页不能删除，请先启用另一套或停用 [I]");
        }
        pageMapper.deleteById(id);
        opLog.log(page.getAccountId(), "MIDDLE_PAGE", id, "DELETE", page.getName());
    }

    public List<MiddlePageVersion> versions(Long id) {
        guard.middlePage(id);
        return versionMapper.selectList(new LambdaQueryWrapper<MiddlePageVersion>()
                .eq(MiddlePageVersion::getPageId, id).orderByDesc(MiddlePageVersion::getVersionNo));
    }

    /** 预览：which = online / latest。 */
    public Map<String, Object> preview(Long id, Device device, String which) {
        MiddlePage page = guard.middlePage(id);
        return previewOf(page, device, which);
    }

    /** 不做归属校验的预览（模拟器中网民点击后访问中间页）。 */
    public Map<String, Object> publicPreview(Long id, Device device) {
        MiddlePage page = pageMapper.selectById(id);
        if (page == null) {
            throw BizException.notFound("中间页");
        }
        return previewOf(page, device, "online");
    }

    private Map<String, Object> previewOf(MiddlePage page, Device device, String which) {
        Long vid = "online".equalsIgnoreCase(which) ? page.getOnlineVersionId() : page.getLatestVersionId();
        if (vid == null) {
            throw BizException.state("该中间页还没有审核通过的版本");
        }
        MiddlePageVersion v = versionMapper.selectById(vid);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pageId", page.getId());
        m.put("name", page.getName());
        m.put("versionNo", v.getVersionNo());
        m.put("auditStatus", v.getAuditStatus());
        m.put("device", device);
        m.put("pcStyle", v.getPcStyle());
        m.put("content", device == Device.MOBILE ? v.getMobileContent() : v.getPcContent());
        return m;
    }

    // ------------------------------------------------------------------ internals

    /**
     * 获取可编辑的版本：最新版为草稿/被拒 → 直接编辑；审核中 → 拒绝编辑；
     * 已通过（即线上版）→ 复制出新草稿版本，线上版保持不变 [P:A]。
     */
    private MiddlePageVersion editableVersion(MiddlePage page) {
        MiddlePageVersion latest = versionMapper.selectById(page.getLatestVersionId());
        switch (latest.getAuditStatus()) {
            case PENDING -> throw BizException.state("中间页审核中，暂不能修改 [I]");
            case REJECTED -> {
                latest.setAuditStatus(AuditStatus.DRAFT);
                return latest;
            }
            case APPROVED -> {
                MiddlePageVersion nv = new MiddlePageVersion();
                nv.setPageId(page.getId());
                nv.setVersionNo(latest.getVersionNo() + 1);
                nv.setPcStyle(latest.getPcStyle());
                nv.setPcContent(deepCopy(latest.getPcContent()));
                nv.setMobileContent(deepCopy(latest.getMobileContent()));
                nv.setAuditStatus(AuditStatus.DRAFT);
                versionMapper.insert(nv);
                page.setLatestVersionId(nv.getId());
                pageMapper.updateById(page);
                return nv;
            }
            default -> {
                return latest;
            }
        }
    }

    private PageContent deepCopy(PageContent c) {
        return c == null ? null : objectMapper.convertValue(c, PageContent.class);
    }

    private MiddlePageView view(MiddlePage page) {
        MiddlePageView v = new MiddlePageView();
        v.setPage(page);
        MiddlePageVersion latest = page.getLatestVersionId() == null ? null : versionMapper.selectById(page.getLatestVersionId());
        MiddlePageVersion online = page.getOnlineVersionId() == null ? null
                : Objects.equals(page.getOnlineVersionId(), page.getLatestVersionId()) ? latest : versionMapper.selectById(page.getOnlineVersionId());
        v.setLatest(latest);
        v.setOnline(online);
        boolean hasOnline = online != null;
        AuditStatus ls = latest == null ? AuditStatus.DRAFT : latest.getAuditStatus();
        String status;
        String text;
        switch (ls) {
            case APPROVED -> { status = "VALID"; text = "有效"; }
            case PENDING -> { status = hasOnline ? "VALID_PENDING" : "PENDING"; text = hasOnline ? "有效（新版审核中）" : "审核中"; }
            case REJECTED -> { status = hasOnline ? "VALID_REJECTED" : "REJECTED"; text = hasOnline ? "有效（新版未通过）" : "审核未通过"; }
            default -> { status = hasOnline ? "VALID_EDITING" : "DRAFT"; text = hasOnline ? "有效（新版草稿未提交）" : "草稿"; }
        }
        v.setStatus(status);
        v.setStatusText(text + (Boolean.TRUE.equals(page.getEnabled()) ? " · 已启用" : ""));
        if (latest != null) {
            v.setCompleteness(completeness(latest));
            v.setMissing(missing(latest));
        }
        return v;
    }

    private Map<String, Boolean> completeness(MiddlePageVersion v) {
        Map<String, Boolean> m = new LinkedHashMap<>();
        boolean imagesRequired = v.getPcStyle() != PcStyle.TEXT_LINK;
        m.put("pc.companyInfo", companyMissing(v.getPcContent(), imagesRequired).isEmpty());
        m.put("pc.banners", bannerMissing(v.getPcContent()).isEmpty());
        m.put("pc.products", productMissing(v.getPcContent(), imagesRequired).isEmpty());
        m.put("mobile.companyInfo", companyMissing(v.getMobileContent(), true).isEmpty());
        m.put("mobile.banners", bannerMissing(v.getMobileContent()).isEmpty());
        m.put("mobile.products", productMissing(v.getMobileContent(), true).isEmpty());
        return m;
    }

    /** 模块完整性校验。必填项划分 [I]（原产品必填项未公开）。 */
    List<String> missing(MiddlePageVersion v) {
        List<String> all = new ArrayList<>();
        boolean imagesRequired = v.getPcStyle() != PcStyle.TEXT_LINK;
        if (v.getPcContent() == null) {
            all.add("PC中间页");
        } else {
            companyMissing(v.getPcContent(), imagesRequired).forEach(s -> all.add("PC-" + s));
            bannerMissing(v.getPcContent()).forEach(s -> all.add("PC-" + s));
            productMissing(v.getPcContent(), imagesRequired).forEach(s -> all.add("PC-" + s));
        }
        if (v.getMobileContent() == null) {
            all.add("无线中间页（可从PC导入）");
        } else {
            companyMissing(v.getMobileContent(), true).forEach(s -> all.add("无线-" + s));
            bannerMissing(v.getMobileContent()).forEach(s -> all.add("无线-" + s));
            productMissing(v.getMobileContent(), true).forEach(s -> all.add("无线-" + s));
        }
        return all;
    }

    private static List<String> companyMissing(PageContent c, boolean logoRequired) {
        List<String> m = new ArrayList<>();
        if (c == null || c.getCompanyInfo() == null) {
            m.add("企业信息");
            return m;
        }
        PageContent.CompanyInfo ci = c.getCompanyInfo();
        if (blank(ci.getName())) m.add("企业名称");
        if (logoRequired && blank(ci.getLogo())) m.add("企业Logo");
        if (blank(ci.getWebsite())) m.add("企业官网");
        if (blank(ci.getConsultUrl()) && blank(ci.getPhone())) m.add("咨询链接或电话");
        return m;
    }

    private static List<String> bannerMissing(PageContent c) {
        if (c == null || c.getBanners() == null || c.getBanners().isEmpty()
                || c.getBanners().stream().anyMatch(b -> blank(b.getImage()))) {
            return List.of("Banner图片");
        }
        return List.of();
    }

    private static List<String> productMissing(PageContent c, boolean imageRequired) {
        if (c == null || c.getProducts() == null || c.getProducts().isEmpty()) {
            return List.of("产品介绍区");
        }
        for (PageContent.Product p : c.getProducts()) {
            if (blank(p.getTitle()) || blank(p.getUrl()) || (imageRequired && blank(p.getImage()))) {
                return List.of("产品介绍区（标题/链接" + (imageRequired ? "/图片" : "") + "）");
            }
        }
        return List.of();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static void collectUrls(PageContent c, List<String> urls) {
        if (c == null) {
            return;
        }
        if (c.getCompanyInfo() != null) {
            urls.add(c.getCompanyInfo().getWebsite());
            urls.add(c.getCompanyInfo().getConsultUrl());
        }
        c.getBanners().forEach(b -> urls.add(b.getUrl()));
        c.getProducts().forEach(p -> urls.add(p.getUrl()));
    }

    // ------------------------------------------------------------------ audit

    @Override
    public AuditTargetType type() {
        return AuditTargetType.MIDDLE_PAGE;
    }

    /** 审核通过：该版本成为线上版本，自动替换旧版 [P:A]。 */
    @Override
    public void onApproved(AuditTask task) {
        MiddlePageVersion v = versionMapper.selectById(task.getTargetId());
        if (v == null || v.getAuditStatus() != AuditStatus.PENDING) {
            return;
        }
        v.setAuditStatus(AuditStatus.APPROVED);
        v.setAuditedAt(LocalDateTime.now());
        versionMapper.updateById(v);
        MiddlePage page = pageMapper.selectById(v.getPageId());
        if (page != null) {
            page.setOnlineVersionId(v.getId());
            pageMapper.updateById(page);
        }
    }

    @Override
    public void onRejected(AuditTask task, String reason) {
        MiddlePageVersion v = versionMapper.selectById(task.getTargetId());
        if (v == null || v.getAuditStatus() != AuditStatus.PENDING) {
            return;
        }
        v.setAuditStatus(AuditStatus.REJECTED);
        v.setRejectReason(reason);
        v.setAuditedAt(LocalDateTime.now());
        versionMapper.updateById(v);
    }
}
