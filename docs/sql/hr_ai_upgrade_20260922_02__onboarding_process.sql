-- 版本：20260922_02；持久化确认录用至实际入职或退回的独立流程及历时起止点。
-- 前提：MySQL 8，已部署 recruitment_application 与 recruitment_onboarding_event；升级前停止招聘写入并完成备份。
-- 数据影响：新增流程表；已有真实入职事件按“开始时间=实际入职时间”回填，诚实记录为 0 秒历史历时，不猜测等待时间。
-- 重跑与部分完成：建表前后均核对当前数据库元数据，历史回填按 application_id 主键幂等保留已存在流程。
-- 恢复：旧应用可忽略并保留该表；回退应用不会丢失既有入职事件，删除表前须先导出流程历时审计数据。
SET NAMES utf8mb4;

SET @onboarding_process_sql = IF(
    DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='recruitment_application' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='recruitment_onboarding_event' AND ENGINE='InnoDB')=1,
    'DO 0',
    'SELECT `onboarding process upgrade requires recruitment_application and recruitment_onboarding_event`'
);
PREPARE onboarding_process_stmt FROM @onboarding_process_sql;
EXECUTE onboarding_process_stmt;
DEALLOCATE PREPARE onboarding_process_stmt;

CREATE TABLE IF NOT EXISTS `recruitment_onboarding_process`
(
    application_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应聘流程 ID',
    status                VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '入职流程状态',
    started_at            DATETIME(6) NOT NULL COMMENT '进入入职流程时间',
    actual_onboarded_at   DATETIME(6) NULL COMMENT '实际入职时间',
    closed_at             DATETIME(6) NULL COMMENT '流程关闭时间',
    started_by_user_id    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '启动流程的用户 ID',
    completed_by_user_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '确认入职或退回的用户 ID',
    `ext`                 TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`              VARCHAR(255) NULL COMMENT '备注',
    `create_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`         VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`         VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`             INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (application_id),
    INDEX ix_recruitment_onboarding_process_status (status, started_at, application_id),
    CONSTRAINT ck_recruitment_onboarding_process_status CHECK (status IN ('IN_PROGRESS', 'ONBOARDED', 'RETURNED')),
    CONSTRAINT ck_recruitment_onboarding_process_state CHECK (
        (status = 'IN_PROGRESS' AND actual_onboarded_at IS NULL AND closed_at IS NULL AND completed_by_user_id IS NULL)
        OR (status = 'ONBOARDED' AND actual_onboarded_at IS NOT NULL AND closed_at IS NOT NULL
            AND completed_by_user_id IS NOT NULL AND actual_onboarded_at >= started_at)
        OR (status = 'RETURNED' AND closed_at IS NOT NULL AND completed_by_user_id IS NOT NULL
            AND (actual_onboarded_at IS NULL OR actual_onboarded_at >= started_at))),
    CONSTRAINT fk_recruitment_onboarding_process_application FOREIGN KEY (application_id) REFERENCES recruitment_application (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='确认录用至实际入职或退回的流程事实';

SET @onboarding_process_sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='recruitment_onboarding_process' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='recruitment_onboarding_process')=16 AND
    (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='recruitment_onboarding_process'
     AND INDEX_NAME='PRIMARY' AND NON_UNIQUE=0)='application_id' AND
    (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='recruitment_onboarding_process'
     AND INDEX_NAME='ix_recruitment_onboarding_process_status' AND NON_UNIQUE=1)='status,started_at,application_id' AND
    (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
     AND TABLE_NAME='recruitment_onboarding_process'
     AND CONSTRAINT_NAME='fk_recruitment_onboarding_process_application' AND DELETE_RULE='RESTRICT')=1 AND
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
     AND TABLE_NAME='recruitment_onboarding_process' AND CONSTRAINT_TYPE='CHECK' AND ENFORCED='YES'
     AND CONSTRAINT_NAME IN ('ck_recruitment_onboarding_process_status','ck_recruitment_onboarding_process_state'))=2,
    'DO 0',
    'SELECT `existing recruitment_onboarding_process definition is incompatible and has been preserved`'
);
PREPARE onboarding_process_stmt FROM @onboarding_process_sql;
EXECUTE onboarding_process_stmt;
DEALLOCATE PREPARE onboarding_process_stmt;

INSERT INTO recruitment_onboarding_process (
    application_id, status, started_at, actual_onboarded_at, closed_at,
    started_by_user_id, completed_by_user_id,
    create_id, create_time, update_id, update_time, version)
SELECT onboarding_event.application_id,
       IF(application_record.current_stage='ONBOARDED', 'ONBOARDED', 'RETURNED'),
       onboarding_event.onboarded_at,
       onboarding_event.onboarded_at,
       GREATEST(onboarding_event.onboarded_at, onboarding_event.create_time),
       onboarding_event.operated_by_user_id,
       onboarding_event.operated_by_user_id,
       onboarding_event.operated_by_user_id,
       onboarding_event.create_time,
       onboarding_event.operated_by_user_id,
       onboarding_event.update_time,
       0
FROM recruitment_onboarding_event onboarding_event
JOIN recruitment_application application_record ON application_record.id=onboarding_event.application_id
ON DUPLICATE KEY UPDATE application_id=recruitment_onboarding_process.application_id;

SET @onboarding_process_sql = NULL;
