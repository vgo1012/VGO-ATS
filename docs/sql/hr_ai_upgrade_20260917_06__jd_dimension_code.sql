-- 版本：20260917_06；JD 编号升级为岗位类型两位码 + 工作区域两位码 + 至少两位组合流水。
-- 前提：MySQL 8，完成 20260917_05，并选择已核对的目标库；停止旧版写入，备份 JD 和所有编号序列表。
-- 数据影响：只新增空字典、空组合序列表和可空分类字段；不推断地点、不改写历史 JD 编号和原序列。
-- 字典由用户逐项维护，两类分别支持 01 至 99，无预置业务名称；名称和代码发布后不删除或改写。
-- 重跑：检查当前库元数据后补齐缺失对象，已有表和计数器保持不变，支持任一建表或加列步骤后继续。
-- 恢复：应用回退必须保留分类列、字典、组合计数器和全局计数器；禁止清理计数器，否则可能复用已分配编号。
SET NAMES utf8mb4;

SET @jd_dimension_sql = IF(DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME IN ('demand_jd', 'demand_jd_code_sequence') AND ENGINE='InnoDB')=2,
    'DO 0', 'SELECT `JD dimension upgrade requires existing JD and global sequence tables`');
PREPARE jd_dimension_stmt FROM @jd_dimension_sql;
EXECUTE jd_dimension_stmt;
DEALLOCATE PREPARE jd_dimension_stmt;

CREATE TABLE IF NOT EXISTS `demand_jd_code_option`
(
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY COMMENT '主键 ID',
    kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '编号维度 JOB_TYPE 或 REGION',
    code CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '维度内部稳定两位码 01 至 99',
    name VARCHAR(80) NOT NULL COMMENT '用户维护的维度名称',
    `ext` TEXT NULL COMMENT 'JSON 扩展信息',
    `remark` VARCHAR(255) NULL COMMENT '备注',
    `create_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version` INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_jd_code_option_code UNIQUE (kind, code),
    CONSTRAINT uk_jd_code_option_name UNIQUE (kind, name),
    CONSTRAINT ck_jd_code_option_kind CHECK (kind IN ('JOB_TYPE', 'REGION')),
    CONSTRAINT ck_jd_code_option_code CHECK (CHAR_LENGTH(code) = 2 AND code <> '00' AND ASCII(LEFT(code, 1)) BETWEEN 48 AND 57
        AND ASCII(RIGHT(code, 1)) BETWEEN 48 AND 57)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='JD 岗位类型和工作区域编号字典';

CREATE TABLE IF NOT EXISTS `demand_jd_code_pair_sequence`
(
    id CHAR(4) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY COMMENT '岗位类型两位码与工作区域两位码组合',
    `last_value` BIGINT NOT NULL DEFAULT 0 COMMENT '组合内已分配流水，禁止回退或清零',
    `ext` TEXT NULL COMMENT 'JSON 扩展信息',
    `remark` VARCHAR(255) NULL COMMENT '备注',
    `create_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version` INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT ck_jd_code_pair_value CHECK (`last_value` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='JD 岗位类型和工作区域组合流水';

SET @jd_dimension_sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_option' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_option')=13 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_option' AND COLUMN_NAME='id' AND DATA_TYPE='char'
     AND CHARACTER_MAXIMUM_LENGTH=26 AND IS_NULLABLE='NO' AND COLLATION_NAME='ascii_bin')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_option' AND COLUMN_NAME='code' AND DATA_TYPE='char'
     AND CHARACTER_MAXIMUM_LENGTH=2 AND IS_NULLABLE='NO' AND COLLATION_NAME='ascii_bin')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_option' AND COLUMN_NAME='kind' AND DATA_TYPE='varchar'
     AND CHARACTER_MAXIMUM_LENGTH=16 AND IS_NULLABLE='NO' AND COLLATION_NAME='ascii_bin')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_option' AND COLUMN_NAME='name' AND DATA_TYPE='varchar'
     AND CHARACTER_MAXIMUM_LENGTH=80 AND IS_NULLABLE='NO' AND COLLATION_NAME='utf8mb4_0900_ai_ci')=1 AND
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_option' AND INDEX_NAME='PRIMARY')=1 AND
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_option' AND INDEX_NAME='PRIMARY' AND COLUMN_NAME='id'
     AND NON_UNIQUE=0 AND SUB_PART IS NULL)=1,
    'DO 0', 'SELECT `existing demand_jd_code_option has incompatible columns or primary key`');
PREPARE jd_dimension_stmt FROM @jd_dimension_sql;
EXECUTE jd_dimension_stmt;
DEALLOCATE PREPARE jd_dimension_stmt;

SET @jd_dimension_sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_pair_sequence' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_pair_sequence')=11 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_pair_sequence' AND COLUMN_NAME='id' AND DATA_TYPE='char'
     AND CHARACTER_MAXIMUM_LENGTH=4 AND IS_NULLABLE='NO' AND COLLATION_NAME='ascii_bin')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_pair_sequence' AND COLUMN_NAME='last_value'
     AND COLUMN_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='0' AND EXTRA='')=1 AND
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_pair_sequence' AND INDEX_NAME='PRIMARY')=1 AND
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_pair_sequence' AND INDEX_NAME='PRIMARY' AND COLUMN_NAME='id'
     AND NON_UNIQUE=0 AND SUB_PART IS NULL)=1,
    'DO 0', 'SELECT `existing demand_jd_code_pair_sequence has incompatible columns or primary key`');
PREPARE jd_dimension_stmt FROM @jd_dimension_sql;
EXECUTE jd_dimension_stmt;
DEALLOCATE PREPARE jd_dimension_stmt;

SET @jd_dimension_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_option' AND INDEX_NAME='uk_jd_code_option_code')=2 AND
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_option' AND INDEX_NAME='uk_jd_code_option_code' AND NON_UNIQUE=0 AND SUB_PART IS NULL
     AND ((SEQ_IN_INDEX=1 AND COLUMN_NAME='kind') OR (SEQ_IN_INDEX=2 AND COLUMN_NAME='code')))=2,
    'DO 0', 'SELECT `existing uk_jd_code_option_code has an incompatible definition`');
PREPARE jd_dimension_stmt FROM @jd_dimension_sql;
EXECUTE jd_dimension_stmt;
DEALLOCATE PREPARE jd_dimension_stmt;

SET @jd_dimension_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_option' AND INDEX_NAME='uk_jd_code_option_name')=2 AND
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_option' AND INDEX_NAME='uk_jd_code_option_name' AND NON_UNIQUE=0 AND SUB_PART IS NULL
     AND ((SEQ_IN_INDEX=1 AND COLUMN_NAME='kind') OR (SEQ_IN_INDEX=2 AND COLUMN_NAME='name')))=2,
    'DO 0', 'SELECT `existing uk_jd_code_option_name has an incompatible definition`');
PREPARE jd_dimension_stmt FROM @jd_dimension_sql;
EXECUTE jd_dimension_stmt;
DEALLOCATE PREPARE jd_dimension_stmt;

SET @jd_dimension_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME IN ('demand_jd_code_option','demand_jd_code_pair_sequence')
     AND COLUMN_NAME IN ('ext','remark','create_id','create_name','create_time','update_id','update_name','update_time','version'))=18 AND
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd_code_option' AND ENFORCED='YES'
     AND CONSTRAINT_TYPE='CHECK' AND CONSTRAINT_NAME IN ('ck_jd_code_option_kind','ck_jd_code_option_code'))=2 AND
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS t
      JOIN information_schema.CHECK_CONSTRAINTS c ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA
       AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME
     WHERE t.TABLE_SCHEMA=DATABASE() AND t.TABLE_NAME='demand_jd_code_pair_sequence'
       AND t.CONSTRAINT_NAME='ck_jd_code_pair_value' AND t.ENFORCED='YES'
       AND REGEXP_REPLACE(REPLACE(c.CHECK_CLAUSE, CHAR(96), ''), '[()[:space:]]+', '')='last_value>=0')=1,
    'DO 0', 'SELECT `existing JD dimension tables require standard columns and enforced check constraints`');
PREPARE jd_dimension_stmt FROM @jd_dimension_sql;
EXECUTE jd_dimension_stmt;
DEALLOCATE PREPARE jd_dimension_stmt;

SET @jd_dimension_sql = IF(
    (SELECT COUNT(*) FROM demand_jd_code_pair_sequence WHERE `last_value`<0)=0 AND
    (SELECT COUNT(*) FROM demand_jd_code_option WHERE kind NOT IN ('JOB_TYPE','REGION')
       OR CHAR_LENGTH(code)<>2 OR code='00' OR ASCII(LEFT(code,1)) NOT BETWEEN 48 AND 57
       OR ASCII(RIGHT(code,1)) NOT BETWEEN 48 AND 57 OR CHAR_LENGTH(TRIM(name))=0)=0,
    'DO 0', 'SELECT `existing JD dimension dictionary or sequence contains invalid data`');
PREPARE jd_dimension_stmt FROM @jd_dimension_sql;
EXECUTE jd_dimension_stmt;
DEALLOCATE PREPARE jd_dimension_stmt;

SET @jd_dimension_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND COLUMN_NAME='job_type_code')=0,
    'ALTER TABLE demand_jd ADD COLUMN job_type_code CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''当前岗位类型代码，历史可空，调整不改编号''',
    IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME='demand_jd' AND COLUMN_NAME='job_type_code' AND DATA_TYPE='char'
        AND CHARACTER_MAXIMUM_LENGTH=2 AND IS_NULLABLE='YES' AND COLLATION_NAME='ascii_bin' AND EXTRA='')=1,
       'DO 0', 'SELECT `existing demand_jd.job_type_code has an incompatible definition`'));
PREPARE jd_dimension_stmt FROM @jd_dimension_sql;
EXECUTE jd_dimension_stmt;
DEALLOCATE PREPARE jd_dimension_stmt;

SET @jd_dimension_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND COLUMN_NAME='work_region_code')=0,
    'ALTER TABLE demand_jd ADD COLUMN work_region_code CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''当前工作区域代码，历史可空，调整不改编号''',
    IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME='demand_jd' AND COLUMN_NAME='work_region_code' AND DATA_TYPE='char'
        AND CHARACTER_MAXIMUM_LENGTH=2 AND IS_NULLABLE='YES' AND COLLATION_NAME='ascii_bin' AND EXTRA='')=1,
       'DO 0', 'SELECT `existing demand_jd.work_region_code has an incompatible definition`'));
PREPARE jd_dimension_stmt FROM @jd_dimension_sql;
EXECUTE jd_dimension_stmt;
DEALLOCATE PREPARE jd_dimension_stmt;

SET @jd_dimension_sql = NULL;
