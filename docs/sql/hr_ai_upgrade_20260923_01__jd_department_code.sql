-- 版本：20260923_01；新 JD 编号增加两位创建部门码，组合流水键由六位扩至八位。
-- 前提：MySQL 8，完成 20260918_01；选择已核对的目标库，停止旧版 JD 写入并备份部门、JD、编号字典及流水表。
-- 数据影响：新增空部门码映射表；仅扩大组合流水主键字段，保留全部六位历史键及其计数，不改写历史 JD 编号。
-- 部门码永久绑定首次创建 JD 时的部门 ID；不设部门外键，部门停用或物理删除后也不得删除映射或复用代码。
-- 重跑与部分完成：按当前库元数据检查，建表后或扩列后中断均可重跑；已有映射和流水绝不清空或回退。
-- 恢复：旧应用可读取旧编号，但不应在新格式写入后回退写入；保留映射与八位流水列，勿缩回六位或清理计数器。
SET NAMES utf8mb4;

SET @jd_department_sql = IF(
    DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME IN ('identity_department','demand_jd','demand_jd_code_sequence','demand_jd_code_pair_sequence')
     AND ENGINE='InnoDB')=4 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_pair_sequence' AND COLUMN_NAME='id'
     AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH IN (6,8)
     AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='NO')=1 AND
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_pair_sequence' AND INDEX_NAME='PRIMARY'
     AND COLUMN_NAME='id' AND NON_UNIQUE=0 AND SUB_PART IS NULL)=1,
    'DO 0', 'SELECT `JD department code upgrade requires the six or eight digit pair sequence primary key`');
PREPARE jd_department_stmt FROM @jd_department_sql;
EXECUTE jd_department_stmt;
DEALLOCATE PREPARE jd_department_stmt;

SET @jd_department_sql = IF(
    NOT EXISTS (SELECT 1 FROM demand_jd_code_pair_sequence
                WHERE id NOT REGEXP '^([0-9]{6}|[0-9]{8})$' OR `last_value` < 0),
    'DO 0', 'SELECT `JD pair sequence contains an invalid key or counter; inspect before upgrade`');
PREPARE jd_department_stmt FROM @jd_department_sql;
EXECUTE jd_department_stmt;
DEALLOCATE PREPARE jd_department_stmt;

CREATE TABLE IF NOT EXISTS `demand_jd_department_code`
(
    department_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY COMMENT '首次创建 JD 时的归属部门 ID，删除部门后仍保留映射',
    code CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '部门内部稳定两位码 01 至 99',
    `ext` TEXT NULL COMMENT 'JSON 扩展信息',
    `remark` VARCHAR(255) NULL COMMENT '备注',
    `create_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version` INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_jd_department_code UNIQUE (code),
    CONSTRAINT ck_jd_department_code CHECK (CHAR_LENGTH(code) = 2 AND code <> '00'
        AND ASCII(SUBSTRING(code, 1, 1)) BETWEEN 48 AND 57
        AND ASCII(SUBSTRING(code, 2, 1)) BETWEEN 48 AND 57)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='JD 创建部门与永久两位码映射';

SET @jd_department_sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_department_code' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_department_code')=11 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_department_code' AND
     ((COLUMN_NAME='department_id' AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH=26
       AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='NO') OR
      (COLUMN_NAME='code' AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH=2
       AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='NO')))=2 AND
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_department_code' AND INDEX_NAME='PRIMARY'
     AND COLUMN_NAME='department_id' AND NON_UNIQUE=0 AND SUB_PART IS NULL)=1 AND
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_department_code' AND INDEX_NAME='uk_jd_department_code'
     AND COLUMN_NAME='code' AND NON_UNIQUE=0 AND SUB_PART IS NULL)=1 AND
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_department_code' AND CONSTRAINT_NAME='ck_jd_department_code'
     AND CONSTRAINT_TYPE='CHECK' AND ENFORCED='YES')=1,
    'DO 0', 'SELECT `existing JD department code table is incompatible; inspect before upgrade`');
PREPARE jd_department_stmt FROM @jd_department_sql;
EXECUTE jd_department_stmt;
DEALLOCATE PREPARE jd_department_stmt;

SET @jd_department_sql = IF(
    (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_pair_sequence' AND COLUMN_NAME='id')=6,
    'ALTER TABLE demand_jd_code_pair_sequence MODIFY COLUMN id CHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''历史六位岗位地区或新八位部门岗位地区组合''',
    'DO 0');
PREPARE jd_department_stmt FROM @jd_department_sql;
EXECUTE jd_department_stmt;
DEALLOCATE PREPARE jd_department_stmt;

SET @jd_department_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_pair_sequence' AND COLUMN_NAME='id'
     AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH=8 AND CHARACTER_SET_NAME='ascii'
     AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='NO')=1,
    'DO 0', 'SELECT `JD pair sequence key was not expanded to eight characters`');
PREPARE jd_department_stmt FROM @jd_department_sql;
EXECUTE jd_department_stmt;
DEALLOCATE PREPARE jd_department_stmt;

SET @jd_department_sql = NULL;
