-- 版本：20260907_03；面试历史游标分页索引。
-- 前提：MySQL 8，已选择业务库，interview_feedback 与 interview_notification_intent 已存在。
-- 数据影响：仅新增非唯一索引，不修改、删除或回填历史数据；建立索引需要额外磁盘和元数据锁。
-- 部署前备份并按数据量预留维护窗口；在同一连接顺序执行。元数据检查支持重复执行及中断续跑。
-- 回退应用不依赖删除索引；可以保留索引，不需要数据恢复操作。新旧前后端接口须配套发布。
SET NAMES utf8mb4;

SET @hr_ai_interview_page_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='interview_feedback' AND INDEX_NAME='ix_interview_feedback_page'),
  'DO 0',
  'ALTER TABLE interview_feedback ADD INDEX ix_interview_feedback_page(interview_id,create_time,id)');
PREPARE hr_ai_interview_page_stmt FROM @hr_ai_interview_page_sql;
EXECUTE hr_ai_interview_page_stmt;
DEALLOCATE PREPARE hr_ai_interview_page_stmt;

SET @hr_ai_interview_page_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='interview_notification_intent' AND INDEX_NAME='ix_interview_notification_page'),
  'DO 0',
  'ALTER TABLE interview_notification_intent ADD INDEX ix_interview_notification_page(interview_id,create_time,id)');
PREPARE hr_ai_interview_page_stmt FROM @hr_ai_interview_page_sql;
EXECUTE hr_ai_interview_page_stmt;
DEALLOCATE PREPARE hr_ai_interview_page_stmt;
SET @hr_ai_interview_page_sql = NULL;
