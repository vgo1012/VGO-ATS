-- 版本：20260917_05；新增 JD 全系统稳定短编号序列，格式 JD-000001，超过六位自然扩展。
-- 前提：MySQL 8，完成此前升级并选择目标数据库；停止旧版应用写入，备份 demand_jd 及已有序列表。
-- 数据影响：不修改、删除或重编号已有 JD；保留历史长编号。仅新增序列表并初始化/补齐计数器。
-- 重跑与部分完成：检查当前库元数据；建表成功但初始化中断可续跑；计数器只增不减。
-- 已有数字短编号（含逻辑删除记录）作为初始化下限，运行期按序列行锁递增，不查询 MAX(code)。
-- 恢复：应用回退时保留本表及计数器，禁止删除、清零或随 JD 清理回退，否则会复用已发出的编号。
SET NAMES utf8mb4;

SET @jd_code_sql = IF(DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND COLUMN_NAME='code')=1,
    'DO 0', 'SELECT `JD code upgrade requires an InnoDB demand_jd table with code`');
PREPARE jd_code_stmt FROM @jd_code_sql;
EXECUTE jd_code_stmt;
DEALLOCATE PREPARE jd_code_stmt;

CREATE TABLE IF NOT EXISTS demand_jd_code_sequence
(
    id TINYINT NOT NULL PRIMARY KEY COMMENT '固定单例键 1',
    `last_value` BIGINT NOT NULL DEFAULT 0 COMMENT '最近已分配的 JD 流水号，禁止回退或清零',
    `ext` TEXT NULL COMMENT 'JSON 扩展信息',
    `remark` VARCHAR(255) NULL COMMENT '备注',
    `create_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version` INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT ck_jd_code_sequence_id CHECK (id = 1),
    CONSTRAINT ck_jd_code_sequence_value CHECK (`last_value` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='JD 稳定短编号序列';

SET @jd_code_sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_sequence' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_sequence' AND COLUMN_NAME='id'
     AND DATA_TYPE='tinyint' AND IS_NULLABLE='NO' AND EXTRA='')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_sequence' AND COLUMN_NAME='last_value'
     AND COLUMN_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='0' AND EXTRA='')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_sequence' AND COLUMN_NAME IN
       ('ext','remark','create_id','create_name','create_time','update_id','update_name','update_time','version'))=9 AND
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_sequence' AND INDEX_NAME='PRIMARY')=1 AND
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_sequence' AND INDEX_NAME='PRIMARY'
     AND COLUMN_NAME='id' AND NON_UNIQUE=0 AND SUB_PART IS NULL)=1 AND
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS t
      JOIN information_schema.CHECK_CONSTRAINTS c ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA
       AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME
     WHERE t.TABLE_SCHEMA=DATABASE() AND t.TABLE_NAME='demand_jd_code_sequence'
       AND t.ENFORCED='YES' AND
       ((t.CONSTRAINT_NAME='ck_jd_code_sequence_id' AND
         REGEXP_REPLACE(REPLACE(c.CHECK_CLAUSE, CHAR(96), ''), '[()[:space:]]+', '')='id=1') OR
        (t.CONSTRAINT_NAME='ck_jd_code_sequence_value' AND
         REGEXP_REPLACE(REPLACE(c.CHECK_CLAUSE, CHAR(96), ''), '[()[:space:]]+', '')='last_value>=0')))=2,
    'DO 0', 'SELECT `existing demand_jd_code_sequence has an incompatible definition`');
PREPARE jd_code_stmt FROM @jd_code_sql;
EXECUTE jd_code_stmt;
DEALLOCATE PREPARE jd_code_stmt;

SET @jd_code_sql = IF(
    (SELECT COUNT(*) FROM demand_jd_code_sequence WHERE id<>1 OR `last_value`<0)=0,
    'DO 0', 'SELECT `existing JD code sequence has invalid data; resolve manually`');
PREPARE jd_code_stmt FROM @jd_code_sql;
EXECUTE jd_code_stmt;
DEALLOCATE PREPARE jd_code_stmt;

INSERT INTO demand_jd_code_sequence (id, `last_value`)
SELECT 1, 0 WHERE NOT EXISTS (SELECT id FROM demand_jd_code_sequence WHERE id=1);

SET @jd_code_existing_max = (SELECT COALESCE(MAX(
    CASE WHEN REGEXP_LIKE(CONVERT(code USING utf8mb4), '^JD-[0-9]{6,19}$', 'c')
        THEN CAST(SUBSTRING(code, 4) AS DECIMAL(19, 0)) ELSE 0 END), 0) FROM demand_jd);
SET @jd_code_sql = IF(@jd_code_existing_max <= 9223372036854775807,
    'DO 0', 'SELECT `existing numeric JD code exceeds the supported sequence range`');
PREPARE jd_code_stmt FROM @jd_code_sql;
EXECUTE jd_code_stmt;
DEALLOCATE PREPARE jd_code_stmt;

UPDATE demand_jd_code_sequence
SET `last_value` = GREATEST(`last_value`, @jd_code_existing_max) WHERE id=1;

SET @jd_code_sql = NULL;
SET @jd_code_existing_max = NULL;
