-- 版本：20260914_08；resume_lifecycle。
-- 前提：MySQL 8，选中已收敛 HR 基线数据库；执行前备份并停止旧版本写入。
-- 数据影响：仅新增表，不修改或删除历史简历、共享、筛选及招聘事实；不自动发布默认日历或启用共享任务。
-- 重跑与部分完成：逐表检查当前数据库元数据，已完成对象保留；每张表原子建成，故中断后直接重跑。
-- 恢复：可停用任务并回退应用，保留新增表和审计；不得删除已产生的共享事实、日历版本或运行记录。
SET NAMES utf8mb4;
SET @work_calendar_sql = IF(DATABASE() IS NOT NULL AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_candidate')=1,
 'DO 0', 'SELECT `work calendar upgrade requires HR baseline in selected database`');
PREPARE work_calendar_stmt FROM @work_calendar_sql;
EXECUTE work_calendar_stmt;
DEALLOCATE PREPARE work_calendar_stmt;

SET @work_calendar_sql = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_resume_lifecycle')=0,
 'CREATE TABLE `talent_resume_lifecycle` (
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''简历流转周期 ID'',
    candidate_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''候选人 ID'',
    resume_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''简历版本 ID'',
    cycle_no INT NOT NULL COMMENT ''当前版本周期序号'',
    active_marker CHAR(1) NULL COMMENT ''Y 为当前周期，关闭后为空；共享保留资格事实'',
    entered_at DATETIME(6) NOT NULL COMMENT ''UTC 实际进入我的简历时间'',
    phase VARCHAR(32) NOT NULL COMMENT ''待筛选、待处置、共享或关闭'',
    warning_level VARCHAR(16) NOT NULL COMMENT ''默认、黄色或红色'',
    elapsed_workdays INT NOT NULL DEFAULT 0 COMMENT ''已结束完整工作日数量'',
    next_check_at DATETIME(6) NULL COMMENT ''UTC 下次检查时间'',
    share_due_at DATETIME(6) NULL COMMENT ''UTC 理论共享到期时间'',
    settled_through DATE NULL COMMENT ''上海已结算日期'',
    calendar_revisions JSON NULL COMMENT ''计算使用的年度版本集合'',
    pause_reason VARCHAR(64) NULL COMMENT ''日历缺失等暂停原因'',
    shared_at DATETIME(6) NULL COMMENT ''UTC 超时共享时间'',
    closed_reason VARCHAR(64) NULL COMMENT ''周期关闭原因'',
    ext TEXT NULL COMMENT ''JSON 扩展信息'',
    remark VARCHAR(255) NULL COMMENT ''备注'',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''创建人 ID'',
    create_name VARCHAR(64) NULL COMMENT ''创建人名称'',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''创建时间'',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''更新人 ID'',
    update_name VARCHAR(64) NULL COMMENT ''更新人名称'',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT ''更新时间'',
    version INT NOT NULL DEFAULT 0 COMMENT ''技术乐观锁版本号'',
    PRIMARY KEY (id),
    UNIQUE KEY uk_resume_lifecycle_cycle (resume_version_id, cycle_no),
    UNIQUE KEY uk_resume_lifecycle_active (resume_version_id, active_marker),
    INDEX ix_resume_lifecycle_due (phase, next_check_at, id),
    INDEX ix_resume_lifecycle_candidate (candidate_id, resume_version_id),
    CONSTRAINT ck_resume_lifecycle_phase CHECK (phase IN (''PENDING_SCREENING'',''PENDING_DISPOSAL'',''SHARED'',''CLOSED'')),
    CONSTRAINT ck_resume_lifecycle_warning CHECK (warning_level IN (''NORMAL'',''YELLOW'',''RED'')),
    CONSTRAINT ck_resume_lifecycle_active CHECK (active_marker IS NULL OR active_marker = ''Y'')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=''我的简历超时流转及自动共享事实''', 'DO 0');
PREPARE work_calendar_stmt FROM @work_calendar_sql;
EXECUTE work_calendar_stmt;
DEALLOCATE PREPARE work_calendar_stmt;

SET @work_calendar_sql = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_resume_lifecycle_runtime')=0,
 'CREATE TABLE `talent_resume_lifecycle_runtime` (
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''运行单例 ID'',
    activated_at DATETIME(6) NULL COMMENT ''UTC 首次启用时间，存量从此时开始'',
    calendar_generation BIGINT NOT NULL DEFAULT 0 COMMENT ''已发布日历重算代次'',
    completed_generation BIGINT NOT NULL DEFAULT 0 COMMENT ''已完成重算代次'',
    last_settlement_date DATE NULL COMMENT ''最近结算边界'',
    lease_owner VARCHAR(64) NULL COMMENT ''后台实例租约所有者'',
    lease_until DATETIME(6) NULL COMMENT ''UTC 租约到期时间'',
    ext TEXT NULL COMMENT ''JSON 扩展信息'',
    remark VARCHAR(255) NULL COMMENT ''备注'',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''创建人 ID'',
    create_name VARCHAR(64) NULL COMMENT ''创建人名称'',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''创建时间'',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''更新人 ID'',
    update_name VARCHAR(64) NULL COMMENT ''更新人名称'',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT ''更新时间'',
    version INT NOT NULL DEFAULT 0 COMMENT ''技术乐观锁版本号'',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=''简历流转运行租约与恢复检查点''', 'DO 0');
PREPARE work_calendar_stmt FROM @work_calendar_sql;
EXECUTE work_calendar_stmt;
DEALLOCATE PREPARE work_calendar_stmt;

SET @work_calendar_sql = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_resume_lifecycle_run')=0,
 'CREATE TABLE `talent_resume_lifecycle_run` (
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''运行 ID'',
    settlement_date DATE NOT NULL COMMENT ''本轮上海结算边界'',
    calendar_generation BIGINT NOT NULL DEFAULT 0 COMMENT ''本轮日历代次'',
    status VARCHAR(32) NOT NULL COMMENT ''运行状态'',
    scanned_count INT NOT NULL DEFAULT 0 COMMENT ''扫描数量'',
    succeeded_count INT NOT NULL DEFAULT 0 COMMENT ''成功数量'',
    shared_count INT NOT NULL DEFAULT 0 COMMENT ''自动共享数量'',
    blocked_count INT NOT NULL DEFAULT 0 COMMENT ''日历暂停数量'',
    failed_count INT NOT NULL DEFAULT 0 COMMENT ''失败数量'',
    error_code VARCHAR(64) NULL COMMENT ''不含个人信息的失败摘要'',
    started_at DATETIME(6) NULL COMMENT ''UTC 开始时间'',
    finished_at DATETIME(6) NULL COMMENT ''UTC 完成时间'',
    ext TEXT NULL COMMENT ''JSON 扩展信息'',
    remark VARCHAR(255) NULL COMMENT ''备注'',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''创建人 ID'',
    create_name VARCHAR(64) NULL COMMENT ''创建人名称'',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''创建时间'',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''更新人 ID'',
    update_name VARCHAR(64) NULL COMMENT ''更新人名称'',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT ''更新时间'',
    version INT NOT NULL DEFAULT 0 COMMENT ''技术乐观锁版本号'',
    PRIMARY KEY (id),
    UNIQUE KEY uk_resume_lifecycle_run (settlement_date, calendar_generation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=''简历流转运行记录与受控重试''', 'DO 0');
PREPARE work_calendar_stmt FROM @work_calendar_sql;
EXECUTE work_calendar_stmt;
DEALLOCATE PREPARE work_calendar_stmt;

SET @work_calendar_sql = NULL;
