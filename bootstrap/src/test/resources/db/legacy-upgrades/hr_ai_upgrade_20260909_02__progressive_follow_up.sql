-- 前置：已应用 20260909_01 JD 独立目录升级，选中待升级业务库；执行前备份。
-- 数据影响：历史活动保持原路径行为；新建活动由应用显式启用逐步跟进。
-- 保留全部步骤与定义引用，只将去重依据调整为活动内顺序，支持重复轮次。
-- 先增加新唯一索引再移除旧约束；每步检查元数据，支持重跑与部分完成恢复。
-- 回退需先停新版写入并还原备份；不可删除已产生的多轮面试或预约审计。
SET NAMES utf8mb4;

SET @hr_follow_up_sql = IF(DATABASE() IS NOT NULL AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('recruitment_application','recruitment_application_workflow_step','recruitment_workflow_path_change_record','demand_jd_version','interview_interview'))=5, 'DO 0', 'SELECT `follow up upgrade requires selected database and prerequisite tables`');
PREPARE hr_follow_up_stmt FROM @hr_follow_up_sql;
EXECUTE hr_follow_up_stmt;
DEALLOCATE PREPARE hr_follow_up_stmt;

SET @hr_follow_up_sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='recruitment_application' AND COLUMN_NAME='progressive_follow_up'), 'DO 0', 'ALTER TABLE recruitment_application ADD COLUMN progressive_follow_up BOOLEAN NOT NULL DEFAULT FALSE COMMENT ''是否逐步选择下一动作''');
PREPARE hr_follow_up_stmt FROM @hr_follow_up_sql;
EXECUTE hr_follow_up_stmt;
DEALLOCATE PREPARE hr_follow_up_stmt;

SET @hr_follow_up_sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='recruitment_application_workflow_step' AND COLUMN_NAME='round_no'), 'DO 0', 'ALTER TABLE recruitment_application_workflow_step ADD COLUMN round_no INT NULL COMMENT ''实际面试轮次''');
PREPARE hr_follow_up_stmt FROM @hr_follow_up_sql;
EXECUTE hr_follow_up_stmt;
DEALLOCATE PREPARE hr_follow_up_stmt;

SET @hr_follow_up_sql = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='recruitment_application_workflow_step' AND INDEX_NAME='uk_recruitment_application_step_sequence'), 'DO 0', 'ALTER TABLE recruitment_application_workflow_step ADD UNIQUE KEY uk_recruitment_application_step_sequence (application_id, sequence_no)');
PREPARE hr_follow_up_stmt FROM @hr_follow_up_sql;
EXECUTE hr_follow_up_stmt;
DEALLOCATE PREPARE hr_follow_up_stmt;

SET @hr_follow_up_sql = IF(NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='recruitment_application_workflow_step' AND INDEX_NAME='uk_recruitment_application_step'), 'DO 0', 'ALTER TABLE recruitment_application_workflow_step DROP INDEX uk_recruitment_application_step');
PREPARE hr_follow_up_stmt FROM @hr_follow_up_sql;
EXECUTE hr_follow_up_stmt;
DEALLOCATE PREPARE hr_follow_up_stmt;

SET @hr_follow_up_sql = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='recruitment_workflow_path_change_record' AND CONSTRAINT_NAME='ck_recruitment_path_change_type'), 'DO 0', 'ALTER TABLE recruitment_workflow_path_change_record DROP CHECK ck_recruitment_path_change_type');
PREPARE hr_follow_up_stmt FROM @hr_follow_up_sql;
EXECUTE hr_follow_up_stmt;
DEALLOCATE PREPARE hr_follow_up_stmt;

SET @hr_follow_up_sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='recruitment_workflow_path_change_record' AND CONSTRAINT_NAME='ck_recruitment_path_change_type'), 'DO 0', 'ALTER TABLE recruitment_workflow_path_change_record ADD CONSTRAINT ck_recruitment_path_change_type CHECK (change_type IN (''SELECTION'',''AMENDMENT'',''NEXT_ACTION''))');
PREPARE hr_follow_up_stmt FROM @hr_follow_up_sql;
EXECUTE hr_follow_up_stmt;
DEALLOCATE PREPARE hr_follow_up_stmt;


-- HR 自选动作不存在原模板定义时保留 NULL，不伪造或重写历史定义。
SET @hr_follow_up_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='recruitment_application_workflow_step' AND COLUMN_NAME='step_definition_id' AND IS_NULLABLE='YES'),
 'DO 0', 'ALTER TABLE recruitment_application_workflow_step MODIFY COLUMN step_definition_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''模板步骤定义 ID；为空表示 HR 自选动作''');
PREPARE hr_follow_up_stmt FROM @hr_follow_up_sql;
EXECUTE hr_follow_up_stmt;
DEALLOCATE PREPARE hr_follow_up_stmt;

CREATE TABLE IF NOT EXISTS `recruitment_follow_up_plan`
(
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '预约 ID',
    application_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '招聘活动 ID',
    starts_at DATETIME(6) NOT NULL COMMENT 'HR 确认的沟通开始时间',
    ends_at DATETIME(6) NOT NULL COMMENT 'HR 确认的沟通结束时间',
    channel VARCHAR(32) NOT NULL COMMENT '沟通渠道',
    note VARCHAR(500) NOT NULL COMMENT '沟通目的及安排',
    status VARCHAR(20) NOT NULL COMMENT '预约状态',
    result_reason VARCHAR(500) NULL COMMENT '最近完成或取消的实际结果说明',
    version INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    ext TEXT NULL COMMENT 'JSON 扩展信息',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL COMMENT '创建人名称',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL COMMENT '更新人名称',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX ix_follow_up_plan_calendar (starts_at, id),
    INDEX ix_follow_up_plan_application (application_id, status, id),
    CONSTRAINT ck_follow_up_plan_status CHECK (status IN ('SCHEDULED','COMPLETED','CANCELLED')),
    CONSTRAINT ck_follow_up_plan_time CHECK (ends_at > starts_at),
    CONSTRAINT fk_follow_up_plan_application FOREIGN KEY (application_id) REFERENCES recruitment_application (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='HR 确认的候选人沟通预约';

CREATE TABLE IF NOT EXISTS `recruitment_follow_up_plan_history`
(
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '预约审计 ID',
    plan_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '预约 ID',
    action VARCHAR(32) NOT NULL COMMENT '操作类型',
    before_snapshot TEXT NULL COMMENT '操作前预约完整快照',
    after_snapshot TEXT NOT NULL COMMENT '操作后预约完整快照',
    reason VARCHAR(500) NOT NULL COMMENT '操作原因',
    version INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    ext TEXT NULL COMMENT 'JSON 扩展信息',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL COMMENT '创建人名称',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL COMMENT '更新人名称',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX ix_follow_up_plan_history (plan_id, create_time, id),
    CONSTRAINT fk_follow_up_plan_history_plan FOREIGN KEY (plan_id) REFERENCES recruitment_follow_up_plan (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沟通预约不可变更的操作审计';

-- 面试轮数由 HR 每次确认，不设三轮上限；保留已有 0（旧预面）及正整数轮次。
SET @hr_follow_up_sql = IF(
 NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='interview_interview' AND CONSTRAINT_NAME='ck_interview_round'),
 'DO 0', 'ALTER TABLE interview_interview DROP CHECK ck_interview_round');
PREPARE hr_follow_up_stmt FROM @hr_follow_up_sql;
EXECUTE hr_follow_up_stmt;
DEALLOCATE PREPARE hr_follow_up_stmt;
SET @hr_follow_up_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='interview_interview' AND CONSTRAINT_NAME='ck_interview_round'),
 'DO 0', 'ALTER TABLE interview_interview ADD CONSTRAINT ck_interview_round CHECK (round_no >= 0)');
PREPARE hr_follow_up_stmt FROM @hr_follow_up_sql;
EXECUTE hr_follow_up_stmt;
DEALLOCATE PREPARE hr_follow_up_stmt;
