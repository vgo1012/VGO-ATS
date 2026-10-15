package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.talent.application.manager.ResumeFactPersistenceManager;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.CandidateIdentity;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 在隔离库构造满足全部外键的合成事实；查询与投影仍走真实人才模块。 */
final class SearchFactExternalFixture implements AutoCloseable {
  private final JdbcTemplate jdbc;
  private final PlatformIdGenerator ids;
  private final TransactionTemplate transactions;
  private final List<String> correctionSnapshots = new ArrayList<>();
  private final String candidateId;
  private final String factId;
  private final String channelId;
  private final String batchId;
  private final String intakeId;
  private final String resumeId;
  private final String resumeVersionId;
  private final String extractionId;
  private final String modelId;
  private final String parseId;
  private final String taskId;

  SearchFactExternalFixture(JdbcTemplate jdbc, PlatformIdGenerator ids, AsyncTaskPort tasks,
      TransactionTemplate transactions, String userId, String departmentId) {
    this.jdbc = jdbc;
    this.ids = ids;
    this.transactions = transactions;
    candidateId = ids.newId();
    factId = ids.newId();
    channelId = ids.newId();
    batchId = ids.newId();
    intakeId = ids.newId();
    resumeId = ids.newId();
    resumeVersionId = ids.newId();
    extractionId = ids.newId();
    modelId = ids.newId();
    parseId = ids.newId();
    taskId = ids.newId();
    transactions.executeWithoutResult(ignored -> {
      jdbc.update("INSERT INTO talent_source_channel(id,code,name,channel_type,status) "
          + "VALUES(?,?,'Synthetic projection channel','MANUAL_UPLOAD','ACTIVE')", channelId, channelId);
      jdbc.update("INSERT INTO talent_intake_batch(id,source_channel_id,source_recruiter_user_id,"
          + "default_owner_user_id,owner_department_id,status) VALUES(?,?,?,?,?,'COMPLETED')",
          batchId, channelId, userId, userId, departmentId);
      jdbc.update("INSERT INTO talent_resume_intake(id,batch_id,source_channel_id,source_recruiter_user_id,"
          + "owner_user_id,owner_department_id,original_file_name,media_type,size_bytes,content_sha256,"
          + "object_key,scan_status,status) VALUES(?,?,?,?,?,?,'synthetic.txt','text/plain',1,?,?,'CLEAN','CONFIRMED')",
          intakeId, batchId, channelId, userId, userId, departmentId, "a".repeat(64), "synthetic/" + intakeId);
      jdbc.update("INSERT INTO talent_candidate(id,full_name,owner_user_id,owner_department_id,status) "
          + "VALUES(?,'Synthetic projection candidate',?,?,'ACTIVE')", candidateId, userId, departmentId);
      jdbc.update("INSERT INTO talent_resume(id,candidate_id,current_version_no) VALUES(?,?,1)", resumeId, candidateId);
      jdbc.update("INSERT INTO talent_resume_version(id,resume_id,intake_id,version_no,original_file_name,"
          + "media_type,size_bytes,content_sha256,object_key,status) "
          + "VALUES(?,?,?,1,'synthetic.txt','text/plain',1,?,?,'AVAILABLE')",
          resumeVersionId, resumeId, intakeId, "a".repeat(64), "synthetic/" + intakeId);
      tasks.enqueue(new AsyncTaskPort.EnqueueTask(taskId, "RESUME_PARSE", "RESUME_VERSION", resumeVersionId,
          userId, userId, departmentId, "talent.read", "talent.read", taskId, "a".repeat(64), 1, Instant.now()));
      var task = tasks.claim("RESUME_PARSE", 1).getFirst();
      assertThat(task.id()).isEqualTo(taskId);
      jdbc.update("INSERT INTO talent_resume_extraction_run(id,resume_version_id,task_id,route,status,"
          + "page_count,successful_pages,fallback_pages,printable_ratio,coverage_ratio) "
          + "VALUES(?,?,?,'NATIVE_TEXT','SUCCEEDED',1,1,0,1,1)", extractionId, resumeVersionId, taskId);
      jdbc.update("INSERT INTO talent_resume_ai_model_run(id,resume_version_id,extraction_run_id,"
          + "schema_version,prompt_version,status,validation_status) VALUES(?,?,?,'v1','synthetic','SUCCEEDED','VALID')",
          modelId, resumeVersionId, extractionId);
      jdbc.update("INSERT INTO talent_resume_parse_snapshot(id,resume_version_id,extraction_run_id,"
          + "model_run_id,schema_version,prompt_version,cache_key) VALUES(?,?,?,?,'v1','synthetic',?)",
          parseId, resumeVersionId, extractionId, modelId, parseId + "0".repeat(38));
      jdbc.update("INSERT INTO talent_resume_fact_snapshot(id,resume_version_id,parse_snapshot_id,"
          + "snapshot_version,source_type,current_marker,full_name,create_time) "
          + "VALUES(?,?,?,1,'AI_PARSE','Y','Synthetic projection candidate',?)",
          factId, resumeVersionId, parseId, Timestamp.from(Instant.now()));
      tasks.succeed(task, factId);
    });
  }

  String candidateId() { return candidateId; }
  String factId() { return factId; }
  String resumeVersionId() { return resumeVersionId; }

  /** 可靠投影回归走生产事实写入器，在同一真实事务中产生持久任务和 Outbox。 */
  String appendCorrection(ResumeFactPersistenceManager writer, String actorId) {
    String current = jdbc.queryForObject("SELECT id FROM talent_resume_fact_snapshot "
        + "WHERE resume_version_id=? AND current_marker='Y'", String.class, resumeVersionId);
    var facts = new ResumeFacts(new CandidateIdentity("Corrected synthetic candidate", null, null,
        "Engineer", "Shanghai", null, null, null), List.of(), List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of());
    String corrected = writer.storeCorrection(resumeVersionId, current, actorId,
        "Synthetic durable projection regression", "HUMAN", List.of("SEARCH"), List.of(), facts)
        .getFactSnapshotId();
    correctionSnapshots.add(corrected);
    return corrected;
  }

  /** 隔离样本模拟合并后的版本序号；保留独立 intake 与完整事实外键。 */
  void becomeNextVersionOf(SearchFactExternalFixture previous) {
    transactions.executeWithoutResult(ignored -> {
      assertThat(jdbc.update("UPDATE talent_resume_version SET resume_id=?,version_no=2 WHERE id=?",
          previous.resumeId, resumeVersionId)).isOne();
      assertThat(jdbc.update("UPDATE talent_resume SET current_version_no=2 WHERE id=?", previous.resumeId)).isOne();
    });
  }

  /** 合成同一简历版本的追加更正；当前标记切换与新版本在真实短事务中提交。 */
  String appendCorrection(String actorId) {
    String correctedId = ids.newId();
    transactions.executeWithoutResult(ignored -> {
      assertThat(jdbc.update("UPDATE talent_resume_fact_snapshot SET current_marker=NULL "
          + "WHERE id=? AND current_marker='Y'", factId)).isOne();
      jdbc.update("INSERT INTO talent_resume_fact_snapshot(id,resume_version_id,parse_snapshot_id,"
          + "previous_snapshot_id,snapshot_version,source_type,current_marker,full_name,corrected_by_user_id,"
          + "correction_reason,create_time) VALUES(?,?,?,?,2,'HUMAN_CORRECTION','Y',?,?,?,?)",
          correctedId, resumeVersionId, parseId, factId, "Corrected synthetic candidate", actorId,
          "Synthetic projection ordering test", Timestamp.from(Instant.now()));
    });
    correctionSnapshots.add(correctedId);
    return correctedId;
  }

  @Override
  public void close() {
    var generatedTasks = new ArrayList<String>();
    jdbc.queryForList("SELECT id,work_task_id FROM reporting_outbox_event WHERE aggregate_id=?", candidateId)
        .forEach(row -> { generatedTasks.add((String) row.get("id")); generatedTasks.add((String) row.get("work_task_id")); });
    jdbc.update("DELETE FROM reporting_outbox_event WHERE aggregate_id=?", candidateId);
    for (String generated : generatedTasks) {
      jdbc.update("DELETE FROM reporting_async_task_attempt WHERE task_id=?", generated);
      jdbc.update("DELETE FROM reporting_async_task WHERE id=?", generated);
    }
    jdbc.update("DELETE FROM talent_fact_correction WHERE resume_version_id=?", resumeVersionId);
    jdbc.update("DELETE FROM talent_process_event WHERE target_id=?", resumeVersionId);
    jdbc.update("DELETE FROM talent_process_event WHERE target_id=?", candidateId);
    for (String correctedId : correctionSnapshots.reversed()) {
      jdbc.update("DELETE FROM talent_resume_fact_snapshot WHERE id=?", correctedId);
    }
    jdbc.update("DELETE FROM talent_resume_fact_snapshot WHERE id=?", factId);
    jdbc.update("DELETE FROM talent_resume_parse_snapshot WHERE id=?", parseId);
    jdbc.update("DELETE FROM talent_resume_ai_model_run WHERE id=?", modelId);
    jdbc.update("DELETE FROM talent_resume_extraction_run WHERE id=?", extractionId);
    jdbc.update("DELETE FROM talent_resume_version WHERE id=?", resumeVersionId);
    jdbc.update("DELETE FROM talent_resume WHERE id=?", resumeId);
    jdbc.update("DELETE FROM talent_candidate WHERE id=?", candidateId);
    jdbc.update("DELETE FROM talent_resume_intake WHERE id=?", intakeId);
    jdbc.update("DELETE FROM talent_intake_batch WHERE id=?", batchId);
    jdbc.update("DELETE FROM talent_source_channel WHERE id=?", channelId);
    jdbc.update("DELETE FROM reporting_async_task_attempt WHERE task_id=?", taskId);
    jdbc.update("DELETE FROM reporting_async_task WHERE id=?", taskId);
  }
}
