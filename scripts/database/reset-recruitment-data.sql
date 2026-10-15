-- MySQL 8 招聘业务重置；手工运维，不接入 Flyway。先阅读同目录 README。
-- 默认预览。执行前停写、备份并验证恢复。使用独立连接，过程 DDL 会隐式提交。
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS hr_reset_recruitment_data;
DELIMITER $$
CREATE PROCEDURE hr_reset_recruitment_data(IN p_database VARCHAR(64), IN p_execute BOOLEAN, IN p_confirmation VARCHAR(100))
SQL SECURITY INVOKER
main: BEGIN
    DECLARE v_done BOOLEAN DEFAULT FALSE;
    DECLARE v_table VARCHAR(64);
    DECLARE v_predicate TEXT;
    DECLARE v_before BIGINT;
    DECLARE v_order INT;
    DECLARE v_cursor CURSOR FOR SELECT table_name, delete_order, delete_predicate
        FROM hr_reset_manifest ORDER BY delete_order, table_name;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS hr_reset_manifest;
        RESIGNAL;
    END;
    IF DATABASE() IS NULL OR DATABASE() IN ('mysql','sys','information_schema','performance_schema') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Select a business database first';
    END IF;
    IF @@foreign_key_checks <> 1 OR p_execute IS NULL OR p_execute NOT IN (0,1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Foreign keys must be enabled; execute must be 0 or 1';
    END IF;
    IF p_execute = 1 AND (p_database IS NULL OR BINARY p_database <> BINARY DATABASE()
        OR p_confirmation IS NULL OR BINARY p_confirmation <> BINARY 'BACKUP_VERIFIED_AND_ALL_WRITERS_STOPPED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Explicit database and backup/writer confirmation required';
    END IF;
    DROP TEMPORARY TABLE IF EXISTS hr_reset_manifest;
    CREATE TEMPORARY TABLE hr_reset_manifest (
        table_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
        delete_order INT NOT NULL, delete_predicate TEXT NOT NULL,
        before_count BIGINT DEFAULT 0, delete_count BIGINT DEFAULT 0, after_count BIGINT DEFAULT 0
    ) ENGINE=InnoDB;
    INSERT INTO hr_reset_manifest(table_name, delete_order, delete_predicate) VALUES
    ('platform_idempotency_record', 32, '1=1'),
    -- 岗位清理必须保留编号上限，避免复用已发出的 JD 编号。
    ('demand_jd_code_sequence', 0, '0=1'),
    ('demand_jd_code_option', 0, '0=1'),
    ('demand_jd_title_suggestion', 0, '0=1'),
    ('demand_jd_code_pair_sequence', 0, '0=1'),
    ('demand_jd_department_code', 0, '0=1'),
    ('identity_department', 0, '0=1'),
    ('identity_user_account', 0, '0=1'),
    ('identity_user_account_history', 0, '0=1'),
    ('identity_user_support_department', 0, '0=1'),
    ('identity_membership', 0, '0=1'),
    ('identity_role', 0, '0=1'),
    ('identity_permission', 0, '0=1'),
    ('identity_role_permission', 0, '0=1'),
    ('identity_user_role', 0, '0=1'),
    ('identity_scope_grant', 0, '0=1'),
    ('identity_object_grant', 11, 'object_type NOT IN (''CLIENT'',''CLIENT_PROJECT'',''CLIENT_POSITION'')'),
    ('identity_handover_batch', 88, '1=1'),
    ('identity_handover_item', 10, '1=1'),
    ('identity_audit_event', 0, '0=1'),
    ('identity_wecom_sync_state', 0, '0=1'),
    ('identity_wecom_sync_batch', 0, '0=1'),
    ('identity_wecom_department', 0, '0=1'),
    ('identity_wecom_member', 0, '0=1'),
    ('identity_wecom_account_link', 0, '0=1'),
    ('identity_wecom_department_link', 0, '0=1'),
    ('SPRING_SESSION', 0, '0=1'),
    ('SPRING_SESSION_ATTRIBUTES', 0, '0=1'),
    ('identity_department_assignment_queue', 0, '0=1'),
    ('demand_client', 0, '0=1'),
    ('demand_client_project', 0, '0=1'),
    ('demand_client_position', 0, '0=1'),
    ('demand_recruitment_demand', 129, '1=1'),
    ('demand_recruitment_collaborator', 8, '1=1'),
    ('demand_jd', 125, '1=1'),
    ('demand_jd_version', 122, '1=1'),
    ('demand_workflow_template_version', 115, '1=1'),
    ('demand_workflow_step_definition', 9, '1=1'),
    ('demand_ownership_history', 7, '1=1'),
    ('demand_audit_event', 2, '1=1'),
    ('demand_idempotency_record', 3, '1=1'),
    ('matching_weight_profile', 110, 'id <> ''00000000000000000000000021'''),
    ('matching_weight_profile_version', 105, 'profile_id <> ''00000000000000000000000021'''),
    ('matching_jd_match_plan', 119, 'id <> ''00000000000000000000000011'''),
    ('matching_controlled_criterion_plan_version', 118, 'id NOT IN (SELECT controlled_criterion_plan_version_id FROM matching_jd_match_plan_version WHERE plan_id = ''00000000000000000000000011'') AND id <> ''00000000000000000000000013'''),
    ('matching_jd_match_plan_version', 116, 'plan_id <> ''00000000000000000000000011'''),
    ('matching_jd_plan_binding', 25, '1=1'),
    ('matching_controlled_criterion_definition', 20, 'plan_version_id NOT IN (SELECT controlled_criterion_plan_version_id FROM matching_jd_match_plan_version WHERE plan_id = ''00000000000000000000000011'') AND plan_version_id <> ''00000000000000000000000013'''),
    ('matching_personal_prefer_selection_version', 29, '1=1'),
    ('matching_ownership_history', 28, '1=1'),
    ('matching_audit_event', 18, '1=1'),
    ('matching_idempotency_record', 24, '1=1'),
    ('talent_source_channel', 0, '0=1'),
    ('talent_intake_batch', 127, '1=1'),
    ('talent_resume_intake_outbox', 117, '1=1'),
    ('talent_resume_intake_processing', 128, '1=1'),
    ('talent_resume_intake', 124, '1=1'),
    -- 保留 IMAP 游标，避免清库后重新采集历史邮件；与已删除 JD 绑定的路由必须一并清理。
    ('talent_mailbox_cursor', 0, '0=1'),
    ('talent_mail_ingest_run', 1, '1=1'),
    ('talent_mail_route_rule', 10, '1=1'),
    ('talent_mail_resume', 69, '1=1'),
    ('talent_candidate', 126, '1=1'),
    ('talent_candidate_identity', 70, '1=1'),
    ('talent_resume', 123, '1=1'),
    ('talent_resume_version', 121, '1=1'),
    ('talent_identity_conflict', 102, '1=1'),
    ('talent_download_authorization', 72, '1=1'),
    ('talent_process_event', 75, '1=1'),
    ('talent_idempotency_record', 74, '1=1'),
    ('talent_candidate_merge_history', 71, '1=1'),
    ('reporting_async_task', 120, '1=1'),
    ('reporting_monthly_report', 96, '1=1'),
    ('reporting_monthly_report_file', 56, '1=1'),
    ('reporting_outbox_event', 97, '1=1'),
    ('reporting_search_task_recovery', 57, '1=1'),
    ('reporting_async_task_attempt', 49, '1=1'),
    ('talent_resume_extraction_run', 117, '1=1'),
    ('talent_resume_extraction_page', 77, '1=1'),
    ('talent_resume_ai_model_run', 114, '1=1'),
    ('talent_resume_parse_snapshot', 112, '1=1'),
    ('talent_resume_fact_snapshot', 109, '1=1'),
    ('talent_resume_work_fact', 85, '1=1'),
    ('talent_resume_project_fact', 82, '1=1'),
    ('talent_resume_education_fact', 76, '1=1'),
    ('talent_resume_skill_fact', 84, '1=1'),
    ('talent_resume_simple_fact', 83, '1=1'),
    ('talent_fact_correction', 101, '1=1'),
    ('talent_fact_correction_change', 73, '1=1'),
    ('demand_jd_ai_model_run', 86, '1=1'),
    ('demand_jd_fact_snapshot', 4, '1=1'),
    ('matching_candidate_selection_snapshot', 104, '1=1'),
    ('matching_candidate_selection_item', 19, '1=1'),
    ('matching_match_run', 92, '1=1'),
    ('matching_match_run_attempt', 26, '1=1'),
    ('matching_match_run_item_outcome', 27, '1=1'),
    ('matching_base_evaluation', 91, '1=1'),
    ('matching_highlight', 23, '1=1'),
    ('matching_criterion_evaluation', 21, '1=1'),
    ('matching_weighted_result', 31, '1=1'),
    ('matching_screening_admission', 30, '1=1'),
    ('matching_ai_result_feedback', 17, '1=1'),
    ('search_filter_template', 107, '1=1'),
    ('search_filter_template_version', 99, '1=1'),
    ('search_filter_template_share', 61, '1=1'),
    ('search_projection_checkpoint', 63, '1=1'),
    ('search_state_replay_checkpoint', 64, '1=1'),
    ('search_index_rebuild_run', 62, '1=1'),
    ('recruitment_engagement_lock_policy', 0, '0=1'),
    ('recruitment_candidate_engagement_lock', 93, '1=1'),
    ('recruitment_candidate_engagement_lock_history', 36, '1=1'),
    ('recruitment_engagement_lock_policy_history', 0, '0=1'),
    ('recruitment_engagement_collaboration_request', 39, '1=1'),
    ('recruitment_application', 113, '1=1'),
    ('recruitment_application_collaborator', 34, '1=1'),
    ('recruitment_application_collaborator_change_record', 35, '1=1'),
    ('recruitment_stage_history', 46, '1=1'),
    ('recruitment_application_workflow_step', 111, '1=1'),
    ('recruitment_workflow_path_change_record', 48, '1=1'),
    ('recruitment_workflow_follow_up_record', 47, '1=1'),
    ('recruitment_communication', 37, '1=1'),
    ('recruitment_intention', 42, '1=1'),
    ('recruitment_decision_record', 38, '1=1'),
    ('recruitment_ownership_history', 45, '1=1'),
    ('recruitment_offer_status_record', 43, '1=1'),
    ('recruitment_onboarding_event', 44, '1=1'),
    ('recruitment_onboarding_process', 44, '1=1'),
    ('recruitment_idempotency_record', 41, '1=1'),
    ('interview_template', 0, '0=1'),
    ('interview_template_version', 0, '0=1'),
    ('interview_interview', 103, '1=1'),
    ('interview_external_calendar_attempt', 10, '1=1'),
    ('interview_external_calendar_projection', 11, '1=1'),
    ('interview_participant', 15, '1=1'),
    ('interview_schedule_history', 16, '1=1'),
    ('interview_feedback', 89, '1=1'),
    ('interview_feedback_version', 12, '1=1'),
    ('interview_notification_intent', 90, '1=1'),
    ('interview_notification_attempt', 14, '1=1'),
    ('interview_idempotency_record', 13, '1=1'),
    ('risk_company_blacklist_record', 98, '1=1'),
    ('risk_company_blacklist_current', 60, '1=1'),
    ('candidate_education_verification_record', 1, '1=1'),
    ('submission_client_submission', 108, '1=1'),
    ('submission_send_snapshot', 69, '1=1'),
    ('submission_feedback', 66, '1=1'),
    ('submission_client_restriction', 100, '1=1'),
    ('submission_client_restriction_current', 65, '1=1'),
    ('submission_risk_acknowledgement', 68, '1=1'),
    ('submission_idempotency_record', 67, '1=1'),
    ('reporting_task_item', 58, '1=1'),
    ('reporting_user_overdue_threshold_version', 0, '0=1'),
    ('reporting_metric_definition', 0, '0=1'),
    ('reporting_metric_version', 0, '0=1'),
    ('reporting_metric_event', 55, '1=1'),
    ('reporting_work_target', 59, '1=1'),
    ('reporting_export_job', 106, '1=1'),
    ('reporting_export_object', 53, '1=1'),
    ('reporting_export_file', 95, '1=1'),
    ('reporting_export_download_audit', 52, '1=1'),
    ('reporting_metric_capture_checkpoint', 54, '1=1'),
    ('reporting_audit_capture_checkpoint', 50, '1=1'),
    ('reporting_audit_event', 51, '1=1'),
    ('demand_mail_source_message', 87, '1=1'),
    ('demand_jd_resume_source', 5, '1=1'),
    ('demand_jd_source', 6, '1=1'),
    ('recruitment_follow_up_plan', 94, '1=1'),
    ('recruitment_follow_up_plan_history', 40, '1=1'),
    ('recruitment_activity_origin', 33, '1=1'),
    ('talent_work_calendar_revision', 0, '0=1'),
    ('talent_work_calendar_year', 0, '0=1'),
    ('talent_work_calendar_day', 0, '0=1'),
    ('talent_resume_lifecycle', 79, '1=1'),
    ('talent_resume_lifecycle_runtime', 81, '1=1'),
    ('talent_resume_lifecycle_run', 80, '1=1');
    IF EXISTS (SELECT 1 FROM information_schema.tables t LEFT JOIN hr_reset_manifest m
        ON BINARY t.table_name = BINARY m.table_name
        WHERE t.table_schema = DATABASE() AND t.table_type = 'BASE TABLE' AND m.table_name IS NULL
        AND t.table_name NOT IN ('flyway_schema_history','recruitment_workbench_threshold','recruitment_workbench_threshold_version')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unknown tables: review cleanup scope';
    END IF;
    IF EXISTS (SELECT 1 FROM hr_reset_manifest m LEFT JOIN information_schema.tables t
        ON t.table_schema = DATABASE() AND BINARY t.table_name = BINARY m.table_name
        WHERE t.table_name IS NULL OR t.engine <> 'InnoDB') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Required table missing or not InnoDB';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.triggers WHERE trigger_schema = DATABASE())
       OR EXISTS (SELECT 1 FROM information_schema.key_column_usage
                  WHERE referenced_table_schema = DATABASE() AND table_schema <> DATABASE()) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Triggers or cross-database references require review';
    END IF;
    IF EXISTS (SELECT 1 FROM identity_handover_item WHERE resource_type NOT IN ('JD','APPLICATION','ENGAGEMENT_LOCK'))
       OR EXISTS (SELECT 1 FROM identity_object_grant WHERE object_type NOT IN
          ('CLIENT','CLIENT_PROJECT','CLIENT_POSITION','DEMAND','JD','JD_VERSION','MATCH_RUN',
           'APPLICATION','INTERVIEW','CANDIDATE','RESUME','RESUME_VERSION','RESUME_INTAKE','INTAKE_BATCH',
           'ASYNC_TASK','EXPORT_JOB','ENGAGEMENT_LOCK','WEIGHT_PROFILE','FILTER_TEMPLATE')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unknown grant/handover type: review retained associations';
    END IF;
    START TRANSACTION WITH CONSISTENT SNAPSHOT;
    OPEN v_cursor;
    count_loop: LOOP
        FETCH v_cursor INTO v_table, v_order, v_predicate;
        IF v_done THEN LEAVE count_loop; END IF;
        SET @hr_reset_sql = CONCAT('SELECT COUNT(*) INTO @hr_reset_count FROM ',v_table);
        PREPARE hr_reset_stmt FROM @hr_reset_sql;
        EXECUTE hr_reset_stmt;
        DEALLOCATE PREPARE hr_reset_stmt;
        SET v_before = @hr_reset_count;
        SET @hr_reset_sql = CONCAT('SELECT COUNT(*) INTO @hr_reset_count FROM ',v_table,' WHERE ',v_predicate);
        PREPARE hr_reset_stmt FROM @hr_reset_sql;
        EXECUTE hr_reset_stmt;
        DEALLOCATE PREPARE hr_reset_stmt;
        UPDATE hr_reset_manifest SET before_count = v_before, delete_count = @hr_reset_count WHERE table_name = v_table;
    END LOOP;
    CLOSE v_cursor;
    SELECT DATABASE() AS target_database, @@hostname AS server_host, @@port AS server_port, p_execute AS execute_mode;
    SELECT table_name, IF(delete_order = 0,'KEEP','DELETE_MATCHED') AS action,
        before_count, delete_count, before_count-delete_count AS expected_retained, delete_predicate
        FROM hr_reset_manifest ORDER BY delete_order, table_name;
    IF p_execute = 0 THEN
        ROLLBACK;
        DROP TEMPORARY TABLE hr_reset_manifest;
        LEAVE main;
    END IF;
    -- 可空自引用在事务内解链；后续失败时一起回滚。
    UPDATE talent_resume_intake SET resume_version_id = NULL WHERE resume_version_id IS NOT NULL;
    UPDATE candidate_education_verification_record SET corrects_record_id = NULL WHERE corrects_record_id IS NOT NULL;
    UPDATE recruitment_decision_record SET corrects_record_id = NULL WHERE corrects_record_id IS NOT NULL;
    UPDATE recruitment_offer_status_record SET corrects_record_id = NULL WHERE corrects_record_id IS NOT NULL;
    UPDATE recruitment_offer_status_record SET reopens_record_id = NULL WHERE reopens_record_id IS NOT NULL;
    UPDATE risk_company_blacklist_record SET corrects_record_id = NULL WHERE corrects_record_id IS NOT NULL;
    UPDATE submission_client_restriction SET corrects_record_id = NULL WHERE corrects_record_id IS NOT NULL;
    UPDATE talent_resume_fact_snapshot SET previous_snapshot_id = NULL WHERE previous_snapshot_id IS NOT NULL;
    SET v_done = FALSE;
    OPEN v_cursor;
    delete_loop: LOOP
        FETCH v_cursor INTO v_table, v_order, v_predicate;
        IF v_done THEN LEAVE delete_loop; END IF;
        IF v_order > 0 THEN
            SET @hr_reset_sql = CONCAT('DELETE FROM ',v_table,' WHERE ',v_predicate);
            PREPARE hr_reset_stmt FROM @hr_reset_sql;
            EXECUTE hr_reset_stmt;
            DEALLOCATE PREPARE hr_reset_stmt;
        END IF;
        SET @hr_reset_sql = CONCAT('SELECT COUNT(*) INTO @hr_reset_count FROM ',v_table);
        PREPARE hr_reset_stmt FROM @hr_reset_sql;
        EXECUTE hr_reset_stmt;
        DEALLOCATE PREPARE hr_reset_stmt;
        UPDATE hr_reset_manifest SET after_count = @hr_reset_count WHERE table_name = v_table;
    END LOOP;
    CLOSE v_cursor;
    IF EXISTS (SELECT 1 FROM hr_reset_manifest WHERE after_count <> before_count-delete_count) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unexpected remaining rows; transaction rolled back';
    END IF;
    COMMIT;
    SELECT 'COMMITTED' AS result, table_name, before_count, delete_count, after_count
        FROM hr_reset_manifest ORDER BY delete_order, table_name;
    DROP TEMPORARY TABLE hr_reset_manifest;
END$$
DELIMITER ;

-- 默认只预览；实际执行只替换下一行，参数见 README。
CALL hr_reset_recruitment_data(NULL, FALSE, NULL);
DROP PROCEDURE hr_reset_recruitment_data;
