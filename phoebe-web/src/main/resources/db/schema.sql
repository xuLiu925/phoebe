-- =====================================================================
-- Phoebe 重建版 建表脚本（MySQL 8 / H2 MODE=MySQL 通用）
-- 声明：表名、字段名、枚举值均为新建实现 [U]，不代表百度原始 Phoebe 数据库结构。
-- 业务字段来源见 docs/01-业务分析.md §4。
-- 可重复执行（CREATE TABLE IF NOT EXISTS）。
-- =====================================================================

-- 广告账户。层级 [P:A]；字段 [I]（企业名/官网/商桥默认值规则见资料 E）
CREATE TABLE IF NOT EXISTS pb_account (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(64)    NOT NULL COMMENT '账户名',
    company_name  VARCHAR(128)   NOT NULL COMMENT '企业名称（中间页默认值）',
    website       VARCHAR(256)   COMMENT '企业官网（中间页网址默认值、主域一致性校验）',
    consult_url   VARCHAR(256)   COMMENT '在线咨询链接（原产品默认填商桥链接）',
    industry      VARCHAR(64)    NOT NULL COMMENT '行业',
    balance       DECIMAL(14, 2) NOT NULL DEFAULT 0 COMMENT '账户余额（模拟）',
    status        VARCHAR(16)    NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    created_at    DATETIME,
    updated_at    DATETIME
);

-- 操作用户
CREATE TABLE IF NOT EXISTS pb_user (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id     BIGINT COMMENT '所属广告账户；内部用户为空',
    username       VARCHAR(64)  NOT NULL,
    password_hash  VARCHAR(128) NOT NULL,
    display_name   VARCHAR(64),
    role           VARCHAR(16)  NOT NULL COMMENT 'ADVERTISER/AUDITOR/ADMIN',
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at     DATETIME,
    updated_at     DATETIME,
    UNIQUE KEY uk_user_username (username)
);

-- 推广计划。字段 [P:A]：名称、预算(可不限)、移动出价比例、推广地域、移动推广电话
CREATE TABLE IF NOT EXISTS pb_plan (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id        BIGINT        NOT NULL,
    name              VARCHAR(64)   NOT NULL,
    budget            DECIMAL(12, 2) COMMENT '日预算，NULL=不限 [预算周期 I]',
    mobile_bid_ratio  DECIMAL(5, 2) NOT NULL DEFAULT 1.00 COMMENT '移动出价比例',
    regions           VARCHAR(512)  COMMENT '推广地域，逗号分隔省份；空=全部',
    mobile_phone      VARCHAR(32)   COMMENT '移动推广电话（非必填）',
    schedule          VARCHAR(168)  COMMENT '投放时段 [P:A截图]：7x24 位 0/1 串，下标=(周几-1)*24+小时；NULL=不限时间',
    excluded_ips      TEXT          COMMENT 'IP排除 [P:A截图]：逗号分隔，支持精确IP与 x.x.x.* 段；NULL=不限IP',
    status            VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PAUSED',
    deleted           TINYINT       NOT NULL DEFAULT 0,
    created_at        DATETIME,
    updated_at        DATETIME,
    KEY idx_plan_account (account_id)
);

-- 推广单元。字段 [P:A]：名称、出价、订阅词
CREATE TABLE IF NOT EXISTS pb_unit (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id  BIGINT       NOT NULL,
    plan_id     BIGINT       NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    bid         DECIMAL(8, 2) NOT NULL COMMENT '单元出价（问答默认出价）',
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    deleted     TINYINT      NOT NULL DEFAULT 0,
    created_at  DATETIME,
    updated_at  DATETIME,
    KEY idx_unit_plan (plan_id)
);

-- 订阅词 [P:A,B]：持续性推荐高 PV 问题
CREATE TABLE IF NOT EXISTS pb_subscribe_word (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id  BIGINT      NOT NULL,
    unit_id     BIGINT      NOT NULL,
    word        VARCHAR(64) NOT NULL,
    created_at  DATETIME,
    KEY idx_sw_unit (unit_id)
);

-- 问答。问题是竞价对象，完整问答对参与展现 [P:A]
CREATE TABLE IF NOT EXISTS pb_question_answer (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id     BIGINT       NOT NULL,
    plan_id        BIGINT       NOT NULL,
    unit_id        BIGINT       NOT NULL,
    question       VARCHAR(200) NOT NULL COMMENT '问题标题 10~98 字符 [P:A]',
    answer         TEXT         NOT NULL COMMENT '答案（富文本）160~19998 字符 [P:A]',
    bid            DECIMAL(8, 2) COMMENT '问答出价，NULL=使用单元出价 [I]',
    source         VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/RECOMMEND/QUALITY/EXCEL/WIZARD',
    audit_status   VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '投放开关 ACTIVE/PAUSED',
    reject_reason  VARCHAR(512),
    audit_version  INT          NOT NULL DEFAULT 1 COMMENT '每次内容变更 +1，用于审核快照对应',
    deleted        TINYINT      NOT NULL DEFAULT 0,
    created_at     DATETIME,
    updated_at     DATETIME,
    KEY idx_qa_unit (unit_id),
    KEY idx_qa_account (account_id)
);

-- 高级创意：企业产品图片 + 产品描述，适用于华表位 [P:A]；与 Phoebe“创意模块”对应关系 [I]
CREATE TABLE IF NOT EXISTS pb_creative (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id     BIGINT       NOT NULL,
    plan_id        BIGINT       NOT NULL,
    unit_id        BIGINT       NOT NULL,
    title          VARCHAR(64)  NOT NULL,
    description    VARCHAR(512) NOT NULL,
    image_urls     TEXT         COMMENT 'JSON 数组',
    target_url     VARCHAR(256),
    audit_status   VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    reject_reason  VARCHAR(512),
    audit_version  INT          NOT NULL DEFAULT 1,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    created_at     DATETIME,
    updated_at     DATETIME,
    KEY idx_creative_unit (unit_id)
);

-- 创意与问答的多对多关联（关系形式 [U]，设计为可调整）
CREATE TABLE IF NOT EXISTS pb_creative_qa_rel (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    creative_id  BIGINT NOT NULL,
    qa_id        BIGINT NOT NULL,
    UNIQUE KEY uk_creative_qa (creative_id, qa_id)
);

-- 中间页。每计划≤2套、同时启用1套 [P:A,B]
CREATE TABLE IF NOT EXISTS pb_middle_page (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id         BIGINT      NOT NULL,
    plan_id            BIGINT      NOT NULL,
    name               VARCHAR(64) NOT NULL,
    enabled            TINYINT     NOT NULL DEFAULT 0,
    online_version_id  BIGINT      COMMENT '线上（最近一次审核通过）版本',
    latest_version_id  BIGINT      COMMENT '最新编辑版本',
    deleted            TINYINT     NOT NULL DEFAULT 0,
    created_at         DATETIME,
    updated_at         DATETIME,
    KEY idx_mp_plan (plan_id)
);

-- 中间页版本：修改已启用中间页时，新版审核通过前展示旧版 [P:A]
CREATE TABLE IF NOT EXISTS pb_middle_page_version (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    page_id         BIGINT      NOT NULL,
    version_no      INT         NOT NULL,
    pc_style        VARCHAR(16) NOT NULL DEFAULT 'IMAGE_TEXT' COMMENT 'IMAGE_TEXT 图文 / TEXT_LINK 文字链 [P:E]',
    pc_content      TEXT        COMMENT 'PC 中间页模块 JSON（存储形式 [U]）',
    mobile_content  TEXT        COMMENT '无线中间页模块 JSON',
    audit_status    VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/APPROVED/REJECTED',
    reject_reason   VARCHAR(512),
    submitted_at    DATETIME,
    audited_at      DATETIME,
    created_at      DATETIME,
    updated_at      DATETIME,
    KEY idx_mpv_page (page_id)
);

-- 企业知识 [P:A]：可插入答案末尾
CREATE TABLE IF NOT EXISTS pb_enterprise_knowledge (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id  BIGINT       NOT NULL,
    title       VARCHAR(128) NOT NULL,
    content     TEXT         NOT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    created_at  DATETIME,
    updated_at  DATETIME
);

-- 图片管理 [P:A]：系统推荐 + 本地上传，4 种尺寸裁剪（尺寸值 [U]）
CREATE TABLE IF NOT EXISTS pb_image_asset (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id  BIGINT       NOT NULL,
    name        VARCHAR(128),
    url         VARCHAR(512) NOT NULL,
    width       INT,
    height      INT,
    source      VARCHAR(16)  NOT NULL DEFAULT 'UPLOAD' COMMENT 'UPLOAD/RECOMMEND',
    crop_ratio  VARCHAR(16)  COMMENT '1:1 / 4:3 / 16:9 / 3:1（示例）',
    deleted     TINYINT      NOT NULL DEFAULT 0,
    created_at  DATETIME,
    updated_at  DATETIME
);

-- 审核任务（快照式审核）。审核存在 [P:B]，表结构 [U]
CREATE TABLE IF NOT EXISTS pb_audit_task (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id        BIGINT      NOT NULL,
    target_type       VARCHAR(16) NOT NULL COMMENT 'QA/MIDDLE_PAGE/CREATIVE',
    target_id         BIGINT      NOT NULL COMMENT 'QA/创意 id；中间页为版本 id',
    target_version    INT         NOT NULL DEFAULT 1,
    title             VARCHAR(256),
    snapshot          TEXT        COMMENT '提交时内容快照 JSON',
    status            VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/CANCELED',
    reason            VARCHAR(512),
    pre_check_result  VARCHAR(512) COMMENT '机器预审提示',
    auditor_id        BIGINT,
    created_at        DATETIME,
    updated_at        DATETIME,
    audited_at        DATETIME,
    KEY idx_audit_status (status)
);

-- 问答知识库（模拟）：从知识类媒体提取优质问答供推荐 [P:E]；数据为虚构
CREATE TABLE IF NOT EXISTS pb_question_library (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    question  VARCHAR(200) NOT NULL,
    answer    TEXT,
    keywords  VARCHAR(256) COMMENT '逗号分隔',
    pv        INT          NOT NULL DEFAULT 0 COMMENT '模拟月检索量',
    quality   TINYINT      NOT NULL DEFAULT 0 COMMENT '1=优质问答',
    channel   VARCHAR(16)  NOT NULL DEFAULT 'ZHIDAO' COMMENT 'ZHIDAO/BAOBAO'
);

-- 模拟展现日志
CREATE TABLE IF NOT EXISTS pb_impression_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id      VARCHAR(40)  NOT NULL,
    query           VARCHAR(200) NOT NULL,
    channel         VARCHAR(16)  NOT NULL COMMENT 'SEARCH/ZHIDAO/BAOBAO',
    page_type       VARCHAR(16)  NOT NULL COMMENT 'SEARCH_LIST/QUESTION_PAGE',
    device          VARCHAR(16)  NOT NULL COMMENT 'PC/MOBILE',
    region          VARCHAR(32),
    account_id      BIGINT       NOT NULL,
    plan_id         BIGINT       NOT NULL,
    unit_id         BIGINT       NOT NULL,
    qa_id           BIGINT       NOT NULL,
    middle_page_id  BIGINT       NOT NULL,
    rank_pos        INT          NOT NULL COMMENT '混排位置',
    effective_bid   DECIMAL(8, 2) NOT NULL,
    charge_price    DECIMAL(8, 2) NOT NULL COMMENT '若被点击时的计费价（二价）',
    quality_score   DECIMAL(6, 3) NOT NULL,
    match_type      VARCHAR(16)  NOT NULL COMMENT 'EXACT/PHRASE/SUBSCRIBE/SIMILAR',
    clicked         TINYINT      NOT NULL DEFAULT 0,
    created_at      DATETIME,
    KEY idx_imp_request (request_id)
);

-- 模拟点击日志。QA_ENTRY 计费；MIDDLE_PAGE_INNER 不计费 [P:B,C]
CREATE TABLE IF NOT EXISTS pb_click_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    impression_id   BIGINT      NOT NULL,
    account_id      BIGINT      NOT NULL,
    plan_id         BIGINT      NOT NULL,
    unit_id         BIGINT      NOT NULL,
    qa_id           BIGINT      NOT NULL,
    middle_page_id  BIGINT,
    device          VARCHAR(16) NOT NULL,
    channel         VARCHAR(16) NOT NULL,
    click_type      VARCHAR(24) NOT NULL COMMENT 'QA_ENTRY/MIDDLE_PAGE_INNER',
    target          VARCHAR(64) COMMENT '中间页内点击目标：banner/product/consult/qrcode...',
    charge_price    DECIMAL(8, 2) NOT NULL DEFAULT 0,
    created_at      DATETIME,
    KEY idx_click_imp (impression_id)
);

-- 问答维度日报（可聚合到单元/计划/账户）。报表指标 [P:B]
CREATE TABLE IF NOT EXISTS pb_daily_stat (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date    DATE           NOT NULL,
    account_id   BIGINT         NOT NULL,
    plan_id      BIGINT         NOT NULL,
    unit_id      BIGINT         NOT NULL,
    qa_id        BIGINT         NOT NULL,
    device       VARCHAR(16)    NOT NULL,
    channel      VARCHAR(16)    NOT NULL DEFAULT 'ZHIDAO' COMMENT '流量渠道 SEARCH/ZHIDAO/BAOBAO/BAOBAO_APP',
    region       VARCHAR(32)    NOT NULL DEFAULT '未知' COMMENT '网民地域（省）',
    impressions  BIGINT         NOT NULL DEFAULT 0,
    clicks       BIGINT         NOT NULL DEFAULT 0,
    cost         DECIMAL(14, 2) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_stat (stat_date, qa_id, device, channel, region),
    KEY idx_stat_account (account_id, stat_date)
);

-- 中间页日报（不区分模块）[P:B]
CREATE TABLE IF NOT EXISTS pb_mp_daily_stat (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date       DATE        NOT NULL,
    account_id      BIGINT      NOT NULL,
    plan_id         BIGINT      NOT NULL,
    middle_page_id  BIGINT      NOT NULL,
    device          VARCHAR(16) NOT NULL,
    channel         VARCHAR(16) NOT NULL DEFAULT 'ZHIDAO',
    region          VARCHAR(32) NOT NULL DEFAULT '未知',
    views           BIGINT      NOT NULL DEFAULT 0 COMMENT '中间页展现',
    clicks          BIGINT      NOT NULL DEFAULT 0 COMMENT '中间页内点击（不计费）',
    UNIQUE KEY uk_mp_stat (stat_date, middle_page_id, device, channel, region)
);

-- 历史操作记录 [P:A]
CREATE TABLE IF NOT EXISTS pb_operation_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id   BIGINT,
    user_id      BIGINT,
    username     VARCHAR(64),
    target_type  VARCHAR(24) NOT NULL,
    target_id    BIGINT,
    action       VARCHAR(32) NOT NULL,
    detail       TEXT,
    created_at   DATETIME,
    KEY idx_oplog_account (account_id, created_at)
);

-- 预算达限记录 [P:A截图：历史操作记录中的“预算达限记录”tab]。每个计划每天最多一条
CREATE TABLE IF NOT EXISTS pb_budget_limit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id  BIGINT         NOT NULL,
    plan_id     BIGINT         NOT NULL,
    plan_name   VARCHAR(64),
    budget      DECIMAL(12, 2) NOT NULL,
    cost        DECIMAL(14, 2) NOT NULL COMMENT '达限时当日消费',
    limit_date  DATE           NOT NULL,
    created_at  DATETIME,
    UNIQUE KEY uk_budget_limit (plan_id, limit_date)
);
