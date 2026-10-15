-- 版本：20260908_04；用途：修复成功重试后遗留的旧简历模型 RUNNING 记录。
-- 前提：MySQL 8，选定目标库，下列六张业务表及所需列已存在且使用 InnoDB。
-- 执行前停止旧版应用和 Worker，部署解析终态/重领修复，备份候选模型行；同一连接执行整个文件。
-- 仅处理 RESUME_PARSE 已成功任务：旧抽取/模型与任务版本一致，旧模型时间唯一归属一次失租尝试，
-- 后续成功尝试及任务结果能关联同任务、同版本的另一个成功模型，且任何解析快照都未引用旧模型。
-- finished_at 使用旧共享尝试的失租结束时间，表示回收时间，不推算模型实际停止执行的时刻。
-- 仅改模型状态、错误码、完成时间、技术版本与更新审计；保留验证结论、原始输出和创建审计。
-- 单条 InnoDB UPDATE 原子执行，已修复记录不再命中；支持重跑和部分历史已经修复后的接续。
-- 时间归属不唯一、引用/绑定不符或审计版本异常的记录保持原样，另行人工核对，不扩大推断范围。
-- 回退需按备份精确恢复上述模型字段；不改共享任务、尝试、抽取或事实，不删数据，不改表结构。
SET NAMES utf8mb4;

SET @hr_ai_upgrade_20260908_04_sql = IF(
    DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND ENGINE='InnoDB'
     AND TABLE_NAME IN ('reporting_async_task','reporting_async_task_attempt','talent_resume_extraction_run',
                        'talent_resume_ai_model_run','talent_resume_parse_snapshot','talent_resume_fact_snapshot'))=6 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND (
      (TABLE_NAME='reporting_async_task' AND COLUMN_NAME IN
       ('id','task_type','resource_type','resource_id','status','attempt_count','finished_at','result_resource_id')) OR
      (TABLE_NAME='reporting_async_task_attempt' AND COLUMN_NAME IN
       ('id','task_id','attempt_no','status','error_code','started_at','finished_at')) OR
      (TABLE_NAME='talent_resume_extraction_run' AND COLUMN_NAME IN ('id','resume_version_id','task_id','create_time')) OR
      (TABLE_NAME='talent_resume_ai_model_run' AND COLUMN_NAME IN
       ('id','resume_version_id','extraction_run_id','status','create_time','finished_at','error_code',
        'version','update_id','update_name','update_time')) OR
      (TABLE_NAME='talent_resume_parse_snapshot' AND COLUMN_NAME IN ('id','resume_version_id','extraction_run_id','model_run_id')) OR
      (TABLE_NAME='talent_resume_fact_snapshot' AND COLUMN_NAME IN ('id','resume_version_id','parse_snapshot_id'))
    ))=37,
    'DO 0',
    'SELECT `expired resume model repair requires existing task and parsing tables on InnoDB`'
);
PREPARE hr_ai_upgrade_20260908_04_stmt FROM @hr_ai_upgrade_20260908_04_sql;
EXECUTE hr_ai_upgrade_20260908_04_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260908_04_stmt;
SET @hr_ai_upgrade_20260908_04_sql = NULL;

UPDATE talent_resume_ai_model_run m
JOIN talent_resume_extraction_run e ON e.id=m.extraction_run_id AND e.resume_version_id=m.resume_version_id
JOIN reporting_async_task t ON t.id=e.task_id AND t.resource_id=m.resume_version_id
JOIN reporting_async_task_attempt a ON a.task_id=t.id AND a.attempt_no<t.attempt_count
JOIN reporting_async_task_attempt current_attempt ON current_attempt.task_id=t.id AND current_attempt.attempt_no=t.attempt_count
JOIN talent_resume_fact_snapshot f ON f.id=t.result_resource_id AND f.resume_version_id=m.resume_version_id
JOIN talent_resume_parse_snapshot p ON p.id=f.parse_snapshot_id AND p.resume_version_id=m.resume_version_id
JOIN talent_resume_ai_model_run winner ON winner.id=p.model_run_id AND winner.id<>m.id
    AND winner.resume_version_id=m.resume_version_id AND winner.extraction_run_id=p.extraction_run_id
JOIN talent_resume_extraction_run winner_extraction ON winner_extraction.id=winner.extraction_run_id
    AND winner_extraction.task_id=t.id AND winner_extraction.resume_version_id=m.resume_version_id
LEFT JOIN talent_resume_parse_snapshot referenced ON referenced.model_run_id=m.id
SET m.status='FAILED',m.error_code='TASK_LEASE_EXPIRED',m.finished_at=a.finished_at,
    m.version=m.version+1,m.update_id='00000000000000000000000000',m.update_name=NULL,m.update_time=CURRENT_TIMESTAMP(6)
WHERE m.status='RUNNING' AND m.finished_at IS NULL AND m.error_code IS NULL
  AND m.version>=0 AND m.version<2147483647
  AND t.task_type='RESUME_PARSE' AND t.resource_type='RESUME_VERSION' AND t.status='SUCCEEDED'
  AND a.attempt_no>=1 AND a.status='FAILED' AND a.error_code='TASK_LEASE_EXPIRED'
  AND a.started_at<=e.create_time AND e.create_time<=m.create_time AND m.create_time<=a.finished_at
  AND current_attempt.status='SUCCEEDED' AND current_attempt.started_at>=a.finished_at
  AND current_attempt.started_at<=winner_extraction.create_time AND winner_extraction.create_time<=winner.create_time
  AND winner.status='SUCCEEDED' AND winner.create_time<=winner.finished_at
  AND winner.finished_at<=current_attempt.finished_at AND current_attempt.finished_at<=t.finished_at
  AND t.finished_at<=CURRENT_TIMESTAMP(6) AND referenced.id IS NULL
  AND NOT EXISTS (SELECT 1 FROM reporting_async_task_attempt other_attempt
      WHERE other_attempt.task_id=t.id AND other_attempt.id<>a.id AND other_attempt.started_at<=m.create_time
        AND (other_attempt.finished_at IS NULL OR other_attempt.finished_at>=m.create_time));

SELECT ROW_COUNT() AS repaired_expired_resume_models;
