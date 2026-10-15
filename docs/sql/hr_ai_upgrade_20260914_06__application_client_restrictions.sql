-- 版本：20260914_06；招聘活动直接记录指定公司限制。
-- 前提：MySQL 8，选中收敛基线数据库；执行前停止旧应用写入并备份限制事实与投影表。
-- 数据影响：原推荐来源保持不变，新增活动来源与证据列；不创建客户反馈，不迁移或删除业务事实。
-- 部分完成及重跑：逐步检查当前数据库元数据，仅添加缺失结构。
-- 恢复：可保留新增列回退应用；已有活动来源事实后禁止将 source_submission_id 收紧为非空或删列，须先导出事实并迁移消费方。
SET NAMES utf8mb4;

SET @restriction_sql = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('submission_client_restriction','recruitment_application'))=2 AND DATABASE() IS NOT NULL,
 'DO 0', 'SELECT `client restriction upgrade requires baseline tables in selected database`');
PREPARE restriction_stmt FROM @restriction_sql;
EXECUTE restriction_stmt;
DEALLOCATE PREPARE restriction_stmt;

SET @restriction_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='submission_client_restriction' AND COLUMN_NAME='source_submission_id' AND IS_NULLABLE='NO')=1,
 'ALTER TABLE submission_client_restriction MODIFY COLUMN source_submission_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''来源客户推荐ID''', 'DO 0');
PREPARE restriction_stmt FROM @restriction_sql;
EXECUTE restriction_stmt;
DEALLOCATE PREPARE restriction_stmt;

SET @restriction_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='submission_client_restriction' AND COLUMN_NAME='source_application_id')=0,
 'ALTER TABLE submission_client_restriction ADD COLUMN source_application_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''来源招聘活动ID''', 'DO 0');
PREPARE restriction_stmt FROM @restriction_sql;
EXECUTE restriction_stmt;
DEALLOCATE PREPARE restriction_stmt;

SET @restriction_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='submission_client_restriction' AND COLUMN_NAME='evidence_reference')=0,
 'ALTER TABLE submission_client_restriction ADD COLUMN evidence_reference VARCHAR(1000) NULL COMMENT ''人工证据引用''', 'DO 0');
PREPARE restriction_stmt FROM @restriction_sql;
EXECUTE restriction_stmt;
DEALLOCATE PREPARE restriction_stmt;

SET @restriction_sql = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='submission_client_restriction' AND CONSTRAINT_NAME='fk_restriction_application')=0,
 'ALTER TABLE submission_client_restriction ADD CONSTRAINT fk_restriction_application FOREIGN KEY (source_application_id) REFERENCES recruitment_application(id)', 'DO 0');
PREPARE restriction_stmt FROM @restriction_sql;
EXECUTE restriction_stmt;
DEALLOCATE PREPARE restriction_stmt;
SET @restriction_sql = NULL;
