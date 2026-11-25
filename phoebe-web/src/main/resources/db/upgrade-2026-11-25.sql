-- =====================================================================
-- 已有 MySQL 库从 0.1.0 升级：新增投放时段、IP排除、日报渠道/地域、预算达限记录。
-- 新库无需执行（schema.sql 已包含）。执行前请备份。
-- 可重复执行：每一步先查 information_schema，已完成的步骤自动跳过。
-- 用法：mysql -u<user> -p <库名> < upgrade-2026-11-25.sql
-- 已在 MySQL 8.3.0 上验证（旧库升级后列、索引与全新安装一致）。
-- =====================================================================

-- 1. pb_plan.schedule
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'pb_plan' AND column_name = 'schedule') = 0,
    'ALTER TABLE pb_plan ADD COLUMN schedule VARCHAR(168) NULL', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. pb_plan.excluded_ips
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'pb_plan' AND column_name = 'excluded_ips') = 0,
    'ALTER TABLE pb_plan ADD COLUMN excluded_ips TEXT NULL', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. pb_daily_stat.channel / region（旧数据归入 ZHIDAO / 未知）
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'pb_daily_stat' AND column_name = 'channel') = 0,
    'ALTER TABLE pb_daily_stat ADD COLUMN channel VARCHAR(16) NOT NULL DEFAULT ''ZHIDAO''', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'pb_daily_stat' AND column_name = 'region') = 0,
    'ALTER TABLE pb_daily_stat ADD COLUMN region VARCHAR(32) NOT NULL DEFAULT ''未知''', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. pb_daily_stat 唯一键改为 (stat_date, qa_id, device, channel, region)
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'pb_daily_stat' AND index_name = 'uk_stat' AND column_name = 'channel') = 0,
    'ALTER TABLE pb_daily_stat DROP INDEX uk_stat, ADD UNIQUE KEY uk_stat (stat_date, qa_id, device, channel, region)', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. pb_mp_daily_stat.channel / region
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'pb_mp_daily_stat' AND column_name = 'channel') = 0,
    'ALTER TABLE pb_mp_daily_stat ADD COLUMN channel VARCHAR(16) NOT NULL DEFAULT ''ZHIDAO''', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'pb_mp_daily_stat' AND column_name = 'region') = 0,
    'ALTER TABLE pb_mp_daily_stat ADD COLUMN region VARCHAR(32) NOT NULL DEFAULT ''未知''', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6. pb_mp_daily_stat 唯一键
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'pb_mp_daily_stat' AND index_name = 'uk_mp_stat' AND column_name = 'channel') = 0,
    'ALTER TABLE pb_mp_daily_stat DROP INDEX uk_mp_stat, ADD UNIQUE KEY uk_mp_stat (stat_date, middle_page_id, device, channel, region)', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 7. 预算达限记录表
CREATE TABLE IF NOT EXISTS pb_budget_limit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id  BIGINT         NOT NULL,
    plan_id     BIGINT         NOT NULL,
    plan_name   VARCHAR(64),
    budget      DECIMAL(12, 2) NOT NULL,
    cost        DECIMAL(14, 2) NOT NULL,
    limit_date  DATE           NOT NULL,
    created_at  DATETIME,
    UNIQUE KEY uk_budget_limit (plan_id, limit_date)
);
