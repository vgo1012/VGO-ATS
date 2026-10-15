-- 版本：20260914_07；work_calendar。
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

SET @work_calendar_sql = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_work_calendar_revision')=0,
 'CREATE TABLE `talent_work_calendar_revision` (
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''日历版本 ID'',
    calendar_year INT NOT NULL COMMENT ''日历年度'',
    revision INT NOT NULL COMMENT ''年度修订序号'',
    status VARCHAR(16) NOT NULL COMMENT ''草稿、已发布或已替代'',
    source_note VARCHAR(1000) NULL COMMENT ''节假日与补班核对来源'',
    published_by CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''发布人 ID'',
    published_name VARCHAR(64) NULL COMMENT ''发布人名称快照'',
    publish_reason VARCHAR(500) NULL COMMENT ''发布或更正原因'',
    published_at DATETIME(6) NULL COMMENT ''UTC 发布时间'',
    recalculation_id VARCHAR(64) NULL COMMENT ''持久化重算请求标识'',
    preview_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''当前草稿预览标识'',
    preview_fingerprint VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''预览日历与业务快照指纹'',
    preview_published_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''预览时有效版本'',
    preview_expires_at DATETIME(6) NULL COMMENT ''UTC 预览过期时间'',
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
    UNIQUE KEY uk_work_calendar_revision (calendar_year, revision),
    CONSTRAINT ck_work_calendar_year CHECK (calendar_year BETWEEN 2000 AND 2100),
    CONSTRAINT ck_work_calendar_status CHECK (status IN (''DRAFT'',''PUBLISHED'',''SUPERSEDED''))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=''管理员工作日历不可变发布版本''', 'DO 0');
PREPARE work_calendar_stmt FROM @work_calendar_sql;
EXECUTE work_calendar_stmt;
DEALLOCATE PREPARE work_calendar_stmt;

SET @work_calendar_sql = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_work_calendar_year')=0,
 'CREATE TABLE `talent_work_calendar_year` (
    calendar_year INT NOT NULL COMMENT ''日历年度及发布结算协调锁'',
    published_revision_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''当前已发布版本，空表示尚未确认'',
    ext TEXT NULL COMMENT ''JSON 扩展信息'',
    remark VARCHAR(255) NULL COMMENT ''备注'',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''创建人 ID'',
    create_name VARCHAR(64) NULL COMMENT ''创建人名称'',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''创建时间'',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''更新人 ID'',
    update_name VARCHAR(64) NULL COMMENT ''更新人名称'',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT ''更新时间'',
    version INT NOT NULL DEFAULT 0 COMMENT ''技术乐观锁版本号'',
    PRIMARY KEY (calendar_year),
    CONSTRAINT fk_work_calendar_current FOREIGN KEY (published_revision_id) REFERENCES talent_work_calendar_revision(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=''工作日历年度有效指针''', 'DO 0');
PREPARE work_calendar_stmt FROM @work_calendar_sql;
EXECUTE work_calendar_stmt;
DEALLOCATE PREPARE work_calendar_stmt;

SET @work_calendar_sql = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_work_calendar_day')=0,
 'CREATE TABLE `talent_work_calendar_day` (
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''日期记录 ID'',
    calendar_revision_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''日历版本 ID'',
    calendar_date DATE NOT NULL COMMENT ''上海业务日期'',
    day_type VARCHAR(16) NOT NULL COMMENT ''工作日或休息日'',
    description VARCHAR(200) NULL COMMENT ''节日或调休说明'',
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
    UNIQUE KEY uk_work_calendar_day (calendar_revision_id, calendar_date),
    CONSTRAINT ck_work_calendar_day_type CHECK (day_type IN (''WORKDAY'',''RESTDAY'')),
    CONSTRAINT fk_work_calendar_day_revision FOREIGN KEY (calendar_revision_id) REFERENCES talent_work_calendar_revision(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=''工作日历全年日期，发布前完整性校验''', 'DO 0');
PREPARE work_calendar_stmt FROM @work_calendar_sql;
EXECUTE work_calendar_stmt;
DEALLOCATE PREPARE work_calendar_stmt;

SET @work_calendar_sql = NULL;
