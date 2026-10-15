-- 前置：MySQL 8，已选择部署库；先停旧版所有搜索节点/任务发送者，再升级含共享任务、重建、checkpoint 的基线。
-- 数据影响：仅创建搜索资源账本或补齐基线已有的容量索引；不导入历史索引，不删除 Elasticsearch 或任何业务行。
-- 发布：备份并核验目标库；建表及缺失容量索引补建均为单条原子 DDL，支持未执行/部分完成/已执行后重跑。
-- 同名不兼容对象必须停止，禁止脚本猜测归属、删表或修改既有列。历史纳管需管理员 quiesce 审计流程。
-- 恢复：可保留账本回退应用；回退前停新搜索 worker，勿运行旧版自动创建/别名路由节点。

SET @search_resource_sql = IF(DATABASE() IS NOT NULL, 'DO 0', 'SELECT `search resource requires selected database`');
PREPARE search_resource_stmt FROM @search_resource_sql;
EXECUTE search_resource_stmt;
DEALLOCATE PREPARE search_resource_stmt;

SET @search_resource_sql = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('reporting_async_task','search_index_rebuild_run','search_projection_checkpoint') AND TABLE_TYPE='BASE TABLE')=3, 'DO 0', 'SELECT `search resource prerequisites missing`');
PREPARE search_resource_stmt FROM @search_resource_sql;
EXECUTE search_resource_stmt;
DEALLOCATE PREPARE search_resource_stmt;

SET @search_resource_sql = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource')=0 OR ((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource')=25 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource' AND (
        (COLUMN_NAME='id' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='index_name' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=120 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='scope_name' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=120 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='resource_kind' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=24 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='run_id' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='task_id' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='attempt_count' AND DATA_TYPE='int' AND IS_NULLABLE='NO') OR
        (COLUMN_NAME='status' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=24 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='managed' AND DATA_TYPE='tinyint' AND IS_NULLABLE='NO') OR
        (COLUMN_NAME='ready_at' AND DATA_TYPE='timestamp' AND IS_NULLABLE='YES' AND DATETIME_PRECISION=6) OR
        (COLUMN_NAME='pinned_until' AND DATA_TYPE='timestamp' AND IS_NULLABLE='YES' AND DATETIME_PRECISION=6) OR
        (COLUMN_NAME='retired_at' AND DATA_TYPE='timestamp' AND IS_NULLABLE='YES' AND DATETIME_PRECISION=6) OR
        (COLUMN_NAME='reconciliation_reason' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=500) OR
        (COLUMN_NAME='reconciled_by' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='reconciled_at' AND DATA_TYPE='timestamp' AND IS_NULLABLE='YES' AND DATETIME_PRECISION=6) OR
        (COLUMN_NAME='original_request_terminated' AND DATA_TYPE='tinyint' AND IS_NULLABLE='NO') OR
        (COLUMN_NAME='ext' AND DATA_TYPE='text' AND IS_NULLABLE='YES') OR
        (COLUMN_NAME='remark' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=255) OR
        (COLUMN_NAME='create_id' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='create_name' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=64) OR
        (COLUMN_NAME='create_time' AND DATA_TYPE='timestamp' AND IS_NULLABLE='NO' AND DATETIME_PRECISION=6) OR
        (COLUMN_NAME='update_id' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='update_name' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=64) OR
        (COLUMN_NAME='update_time' AND DATA_TYPE='timestamp' AND IS_NULLABLE='NO' AND DATETIME_PRECISION=6) OR
        (COLUMN_NAME='version' AND DATA_TYPE='int' AND IS_NULLABLE='NO')))=25), 'DO 0', 'SELECT `search resource has incompatible columns`');
PREPARE search_resource_stmt FROM @search_resource_sql;
EXECUTE search_resource_stmt;
DEALLOCATE PREPARE search_resource_stmt;

CREATE TABLE IF NOT EXISTS `search_index_resource`
(
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    index_name VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '唯一精确物理目标，不可重开创建意图',
    scope_name VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属搜索业务别名或本地后端',
    resource_kind VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '资源来源类型编码',
    run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '原重建业务记录 ID',
    task_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '原共享任务 ID',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '精确创建批次',
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '持久生命周期编码',
    managed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '远端归属 marker 已确定核验',
    ready_at TIMESTAMP(6) NULL COMMENT '已知创建完成或核对时间',
    pinned_until TIMESTAMP(6) NULL COMMENT '数据库时钟保守请求保护期限',
    retired_at TIMESTAMP(6) NULL COMMENT '持久删除意图登记时间',
    reconciliation_reason VARCHAR(500) NULL COMMENT '首次人工核对原因，不可覆盖',
    reconciled_by CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '首次核对超级管理员 ID',
    reconciled_at TIMESTAMP(6) NULL COMMENT '首次核对审计时间',
    original_request_terminated BOOLEAN NOT NULL DEFAULT FALSE COMMENT '原发送者及在途请求已确认终止',
    `ext` TEXT NULL COMMENT 'JSON 扩展信息',
    `remark` VARCHAR(255) NULL COMMENT '备注',
    `create_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version` INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_search_resource_index UNIQUE (index_name),
    INDEX ix_search_resource_collect (managed,status,ready_at,create_time,id),
    INDEX ix_search_resource_task (task_id,attempt_count),
    INDEX ix_search_resource_status (status,id),
    CONSTRAINT ck_search_resource_status CHECK (status IN ('CREATING','CREATE_UNKNOWN','READY','RETIRING','DELETED')),
    CONSTRAINT ck_search_resource_kind CHECK (resource_kind IN ('BOOTSTRAP','REBUILD','LEGACY_APPROVED','LEGACY_ATTEMPT')),
    CONSTRAINT ck_search_resource_attempt CHECK (attempt_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='搜索目标创建、请求保护和回收账本';

SET @search_resource_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource')=25 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource' AND (
        (COLUMN_NAME='id' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='index_name' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=120 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='scope_name' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=120 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='resource_kind' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=24 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='run_id' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='task_id' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='attempt_count' AND DATA_TYPE='int' AND IS_NULLABLE='NO') OR
        (COLUMN_NAME='status' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=24 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='managed' AND DATA_TYPE='tinyint' AND IS_NULLABLE='NO') OR
        (COLUMN_NAME='ready_at' AND DATA_TYPE='timestamp' AND IS_NULLABLE='YES' AND DATETIME_PRECISION=6) OR
        (COLUMN_NAME='pinned_until' AND DATA_TYPE='timestamp' AND IS_NULLABLE='YES' AND DATETIME_PRECISION=6) OR
        (COLUMN_NAME='retired_at' AND DATA_TYPE='timestamp' AND IS_NULLABLE='YES' AND DATETIME_PRECISION=6) OR
        (COLUMN_NAME='reconciliation_reason' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=500) OR
        (COLUMN_NAME='reconciled_by' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='reconciled_at' AND DATA_TYPE='timestamp' AND IS_NULLABLE='YES' AND DATETIME_PRECISION=6) OR
        (COLUMN_NAME='original_request_terminated' AND DATA_TYPE='tinyint' AND IS_NULLABLE='NO') OR
        (COLUMN_NAME='ext' AND DATA_TYPE='text' AND IS_NULLABLE='YES') OR
        (COLUMN_NAME='remark' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=255) OR
        (COLUMN_NAME='create_id' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='create_name' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=64) OR
        (COLUMN_NAME='create_time' AND DATA_TYPE='timestamp' AND IS_NULLABLE='NO' AND DATETIME_PRECISION=6) OR
        (COLUMN_NAME='update_id' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
        (COLUMN_NAME='update_name' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=64) OR
        (COLUMN_NAME='update_time' AND DATA_TYPE='timestamp' AND IS_NULLABLE='NO' AND DATETIME_PRECISION=6) OR
        (COLUMN_NAME='version' AND DATA_TYPE='int' AND IS_NULLABLE='NO')))=25, 'DO 0', 'SELECT `search resource has incompatible columns`');
PREPARE search_resource_stmt FROM @search_resource_sql;
EXECUTE search_resource_stmt;
DEALLOCATE PREPARE search_resource_stmt;

SET @search_resource_sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource' AND INDEX_NAME='PRIMARY')=1 AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource' AND INDEX_NAME='PRIMARY' AND NON_UNIQUE=0 AND INDEX_TYPE='BTREE' AND IS_VISIBLE='YES' AND SUB_PART IS NULL AND ((SEQ_IN_INDEX=1 AND COLUMN_NAME='id')))=1, 'DO 0', 'SELECT `search resource PRIMARY incompatible`');
PREPARE search_resource_stmt FROM @search_resource_sql;
EXECUTE search_resource_stmt;
DEALLOCATE PREPARE search_resource_stmt;

SET @search_resource_sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource' AND INDEX_NAME='uk_search_resource_index')=1 AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource' AND INDEX_NAME='uk_search_resource_index' AND NON_UNIQUE=0 AND INDEX_TYPE='BTREE' AND IS_VISIBLE='YES' AND SUB_PART IS NULL AND ((SEQ_IN_INDEX=1 AND COLUMN_NAME='index_name')))=1, 'DO 0', 'SELECT `search resource uk_search_resource_index incompatible`');
PREPARE search_resource_stmt FROM @search_resource_sql;
EXECUTE search_resource_stmt;
DEALLOCATE PREPARE search_resource_stmt;

SET @search_resource_sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource' AND INDEX_NAME='ix_search_resource_collect')=5 AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource' AND INDEX_NAME='ix_search_resource_collect' AND NON_UNIQUE=1 AND INDEX_TYPE='BTREE' AND IS_VISIBLE='YES' AND SUB_PART IS NULL AND ((SEQ_IN_INDEX=1 AND COLUMN_NAME='managed') OR (SEQ_IN_INDEX=2 AND COLUMN_NAME='status') OR (SEQ_IN_INDEX=3 AND COLUMN_NAME='ready_at') OR (SEQ_IN_INDEX=4 AND COLUMN_NAME='create_time') OR (SEQ_IN_INDEX=5 AND COLUMN_NAME='id')))=5, 'DO 0', 'SELECT `search resource ix_search_resource_collect incompatible`');
PREPARE search_resource_stmt FROM @search_resource_sql;
EXECUTE search_resource_stmt;
DEALLOCATE PREPARE search_resource_stmt;

SET @search_resource_sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource' AND INDEX_NAME='ix_search_resource_task')=2 AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource' AND INDEX_NAME='ix_search_resource_task' AND NON_UNIQUE=1 AND INDEX_TYPE='BTREE' AND IS_VISIBLE='YES' AND SUB_PART IS NULL AND ((SEQ_IN_INDEX=1 AND COLUMN_NAME='task_id') OR (SEQ_IN_INDEX=2 AND COLUMN_NAME='attempt_count')))=2, 'DO 0', 'SELECT `search resource ix_search_resource_task incompatible`');
PREPARE search_resource_stmt FROM @search_resource_sql;
EXECUTE search_resource_stmt;
DEALLOCATE PREPARE search_resource_stmt;

SET @search_resource_sql = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource' AND CONSTRAINT_TYPE='CHECK' AND ENFORCED='YES' AND CONSTRAINT_NAME IN ('ck_search_resource_status','ck_search_resource_kind','ck_search_resource_attempt'))=3, 'DO 0', 'SELECT `search resource lifecycle checks incompatible`');
PREPARE search_resource_stmt FROM @search_resource_sql;
EXECUTE search_resource_stmt;
DEALLOCATE PREPARE search_resource_stmt;

SET @search_resource_sql = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource' AND ENGINE='InnoDB')=1, 'DO 0', 'SELECT `search resource requires InnoDB`');
PREPARE search_resource_stmt FROM @search_resource_sql;
EXECUTE search_resource_stmt;
DEALLOCATE PREPARE search_resource_stmt;

-- MySQL 元数据可能以反斜杠转义单引号；只还原该转义，不移除其他字符或放宽允许的枚举集合。
SET @search_resource_sql = IF((SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_search_resource_status' AND REPLACE(REPLACE(REPLACE(LOWER(REGEXP_REPLACE(CHECK_CLAUSE,'[[:space:]`()]+','')),'_ascii',''),'_utf8mb4',''),CONCAT(CHAR(92),CHAR(39)),CHAR(39))='statusin''creating'',''create_unknown'',''ready'',''retiring'',''deleted''')=1, 'DO 0', 'SELECT `search resource ck_search_resource_status expression incompatible`');
PREPARE search_resource_stmt FROM @search_resource_sql;
EXECUTE search_resource_stmt;
DEALLOCATE PREPARE search_resource_stmt;

SET @search_resource_sql = IF((SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_search_resource_kind' AND REPLACE(REPLACE(REPLACE(LOWER(REGEXP_REPLACE(CHECK_CLAUSE,'[[:space:]`()]+','')),'_ascii',''),'_utf8mb4',''),CONCAT(CHAR(92),CHAR(39)),CHAR(39))='resource_kindin''bootstrap'',''rebuild'',''legacy_approved'',''legacy_attempt''')=1, 'DO 0', 'SELECT `search resource ck_search_resource_kind expression incompatible`');
PREPARE search_resource_stmt FROM @search_resource_sql;
EXECUTE search_resource_stmt;
DEALLOCATE PREPARE search_resource_stmt;

SET @search_resource_sql = IF((SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_search_resource_attempt' AND REPLACE(REPLACE(LOWER(REGEXP_REPLACE(CHECK_CLAUSE,'[[:space:]`()]+','')),'_ascii',''),'_utf8mb4','')='attempt_count>=0')=1, 'DO 0', 'SELECT `search resource ck_search_resource_attempt expression incompatible`');
PREPARE search_resource_stmt FROM @search_resource_sql;
EXECUTE search_resource_stmt;
DEALLOCATE PREPARE search_resource_stmt;

SET @search_resource_sql = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource' AND ENGINE='InnoDB' AND TABLE_COLLATION='utf8mb4_0900_ai_ci')=1, 'DO 0', 'SELECT `search resource table charset or engine incompatible`');
PREPARE search_resource_stmt FROM @search_resource_sql;
EXECUTE search_resource_stmt;
DEALLOCATE PREPARE search_resource_stmt;

SET @search_resource_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource' AND COLUMN_NAME IN ('version','managed','attempt_count','original_request_terminated') AND COLUMN_DEFAULT='0' AND IS_NULLABLE='NO')=4, 'DO 0', 'SELECT `search resource capacity lifecycle defaults incompatible`');
PREPARE search_resource_stmt FROM @search_resource_sql;
EXECUTE search_resource_stmt;
DEALLOCATE PREPARE search_resource_stmt;

-- 状态首列容量索引：DELETED 审计历史增长后，当前读仍有窄活体范围访问路径。
-- 接续早期已建表的失败升级：仅补缺失索引，同名不兼容索引仍由下方检查拒绝。
SET @search_resource_sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource' AND INDEX_NAME='ix_search_resource_status')=0, 'ALTER TABLE search_index_resource ADD INDEX ix_search_resource_status (status,id)', 'DO 0');
PREPARE search_resource_stmt FROM @search_resource_sql;
EXECUTE search_resource_stmt;
DEALLOCATE PREPARE search_resource_stmt;

SET @search_resource_sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource' AND INDEX_NAME='ix_search_resource_status')=2 AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource' AND INDEX_NAME='ix_search_resource_status' AND NON_UNIQUE=1 AND INDEX_TYPE='BTREE' AND IS_VISIBLE='YES' AND SUB_PART IS NULL AND COLLATION='A' AND ((SEQ_IN_INDEX=1 AND COLUMN_NAME='status') OR (SEQ_IN_INDEX=2 AND COLUMN_NAME='id')))=2, 'DO 0', 'SELECT `search resource capacity index incompatible`');
PREPARE search_resource_stmt FROM @search_resource_sql;
EXECUTE search_resource_stmt;
DEALLOCATE PREPARE search_resource_stmt;
