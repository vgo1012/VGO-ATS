package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.talent.application.manager.CandidateResumeSummaryApplicationManager;
import com.ezaigc.hr.talent.application.manager.CandidateBatchApplicationManager;
import com.ezaigc.hr.talent.application.manager.ResumeVersionFactSummaryApplicationManager;
import com.ezaigc.hr.talent.application.manager.TalentIntakeApplicationManager;
import com.ezaigc.hr.talent.enums.ResumeSummaryFactsStatus;
import com.ezaigc.hr.talent.service.ExtractionRunPersistenceService;
import com.ezaigc.hr.talent.service.IdentityConflictPersistenceService;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/** 复用隔离空库及合成履历，记录实际发给 MySQL 的 SQL（不记录参数或个人信息）。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
@ActiveProfiles("external-it")
@ContextConfiguration(initializers = ExternalMySqlIsolationInitializer.class)
@SpringBootTest(properties = {
    "spring.datasource.url=${HR_DB_URL}", "spring.datasource.username=${HR_DB_USER}",
    "spring.datasource.password=${HR_DB_PASSWORD}", "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.datasource.hikari.maximum-pool-size=4", "spring.datasource.hikari.connection-timeout=5000",
    "mybatis-plus.configuration.local-cache-scope=STATEMENT",
    "hr.object-storage.mode=local", "hr.object-storage.local-root=${HR_EXTERNAL_OBJECT_ROOT}",
    "hr.search.mode=memory", "hr.malware-scan.mode=development",
    "hr.pagination.cursor-signing-key=external-it-only-cursor-signing-key-32-bytes",
    "hr.bootstrap-admin.enabled=true", "hr.bootstrap-admin.department-code=ROOT",
    "hr.bootstrap-admin.department-name=摘要测试总部", "hr.bootstrap-admin.display-name=摘要测试管理员",
    "hr.bootstrap-admin.work-email=resume-summary-it@example.com", "hr.bootstrap-admin.password=Test-Only-Summary-123!",
    "hr.resume-parsing.worker-enabled=false", "hr.jd-parsing.worker-enabled=false", "hr.matching.worker-enabled=false",
    "hr.search.worker-enabled=false", "hr.messaging.worker-enabled=false", "hr.reporting.audit-capture.enabled=false"
})
@Import(CandidateResumeSummaryExternalMySqlIT.SqlConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class CandidateResumeSummaryExternalMySqlIT {
  private final JdbcTemplate jdbc;
  private final PlatformIdGenerator ids;
  private final AsyncTaskPort tasks;
  private final TransactionTemplate transactions;
  private final CandidateResumeSummaryApplicationManager summaries;
  private final CandidateBatchApplicationManager candidateBatch;
  private final ResumeVersionFactSummaryApplicationManager versionSummaries;
  private final TalentIntakeApplicationManager talent;
  private final ResumeParsingPersistenceService parsing;
  private final ExtractionRunPersistenceService extraction;
  private final IdentityConflictPersistenceService conflicts;
  private final UserDetailsService users;
  private final SqlRecorder recorder;
  private final TalentIntakePersistenceService intakes;
  private final WebApplicationContext webContext;
  private SecurityContext previous;

  @BeforeEach void authenticate() {
    previous = SecurityContextHolder.getContext();
    var user = users.loadUserByUsername("resume-summary-it@example.com");
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    SecurityContextHolder.setContext(context);
  }
  @AfterEach void restore() { SecurityContextHolder.setContext(previous); recorder.clear(); }

  @Test void candidateBatchKeepsSqlCountConstantAndUsesTheHttpBoundary() throws Exception {
    try (var first = fixture(); var second = fixture()) {
      recorder.clear();
      assertThat(candidateBatch.read(List.of(first.candidateId()))).hasSize(1);
      int singleQueries = recorder.talentSql().size();
      recorder.clear();
      assertThat(candidateBatch.read(List.of(second.candidateId(), first.candidateId(), second.candidateId())))
          .extracting(value -> value.getId()).containsExactly(second.candidateId(), first.candidateId());
      assertThat(recorder.talentSql()).hasSize(singleQueries);
      MockMvcBuilders.webAppContextSetup(webContext).build()
          .perform(get("/api/v1/candidate-batch").param("candidateIds", first.candidateId(), second.candidateId()))
          .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
          .andExpect(jsonPath("$[0].id").value(first.candidateId()));
      MockMvcBuilders.webAppContextSetup(webContext).build()
          .perform(get("/api/v1/candidate-batch").param("candidateIds", "invalid"))
          .andExpect(status().isBadRequest());
    }
  }

  @Test void historicalVersionSummariesStayBatchBoundedAfterANewerVersionArrives() {
    try (var first = fixture(); var second = fixture()) {
      recorder.clear();
      var single = versionSummaries.summaries(List.of(first.resumeVersionId()));
      assertThat(single).hasSize(1);
      int singleQueries = recorder.talentSql().size();
      recorder.clear();
      var batch = versionSummaries.summaries(List.of(second.resumeVersionId(), first.resumeVersionId()));
      assertThat(batch).extracting(value -> value.getResumeVersionId())
          .containsExactly(second.resumeVersionId(), first.resumeVersionId());
      assertThat(recorder.talentSql()).hasSize(singleQueries);
      second.becomeNextVersionOf(first);
      var historical = versionSummaries.summaries(List.of(first.resumeVersionId())).getFirst();
      assertThat(historical.getFacts().getSnapshot().getId()).isEqualTo(first.factId());
      assertThat(historical.getResumeVersionId()).isEqualTo(first.resumeVersionId());
    }
  }

  @Test void unscoredResumeWithoutJobStillHasMetadataWhileFactsArePending() {
    try (var fixture = fixture()) {
      assertThat(intakes.findCurrentCandidateResumes(List.of(fixture.candidateId()))).isEmpty();
      jdbc.update("UPDATE talent_resume_fact_snapshot SET current_marker=NULL WHERE id=?", fixture.factId());
      var result = summaries.summaries(List.of(fixture.candidateId())).getFirst();
      assertThat(result.getResumeVersion().getId()).isEqualTo(fixture.resumeVersionId());
      assertThat(result.getSource()).isNotNull();
      assertThat(result.getFactsStatus()).isEqualTo(ResumeSummaryFactsStatus.NOT_READY);
      assertThat(result.getFacts()).isNull();
    }
  }

  @Test void multipleScoredJobContextsDoNotDuplicateTheResumeSummary() throws Exception {
    try (var first = fixture(); var second = fixture()) {
      try {
        var versionIds = List.of(first.resumeVersionId(), second.resumeVersionId());
        for (String versionId : versionIds) {
          String intakeId = jdbc.queryForObject("SELECT intake_id FROM talent_resume_version WHERE id=?", String.class, versionId);
          jdbc.update("UPDATE talent_intake_batch SET jd_version_id=? WHERE id=(SELECT batch_id FROM talent_resume_intake WHERE id=?)",
              ids.newId(), intakeId);
          jdbc.update("UPDATE talent_resume_intake SET candidate_id=?,resume_version_id=?,status='COMPLETED',score_status='SUCCEEDED',score_run_id=? WHERE id=?",
              first.candidateId(), first.resumeVersionId(), ids.newId(), intakeId);
        }
        assertThat(intakes.findCurrentCandidateResumes(List.of(first.candidateId()))).hasSize(2);
        var result = summaries.summaries(List.of(first.candidateId()));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getResumeVersion().getId()).isEqualTo(first.resumeVersionId());
        assertThat(result.getFirst().getFactsStatus()).isEqualTo(ResumeSummaryFactsStatus.READY);
        MockMvcBuilders.webAppContextSetup(webContext).build()
            .perform(get("/api/v1/candidate-resume-summaries").param("candidateIds", first.candidateId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].resumeVersion.id").value(first.resumeVersionId()))
            .andExpect(jsonPath("$[0].factsStatus").value("READY"));
      } finally {
        jdbc.update("UPDATE talent_resume_intake SET candidate_id=NULL,resume_version_id=NULL WHERE resume_version_id=?",
            first.resumeVersionId());
      }
    }
  }

  @Test void batchSizeDoesNotMultiplySqlAndNewestBusinessVersionMatchesExistingDetail() {
    try (var first = fixture(); var second = fixture()) {
      seedListFacts(first.factId());
      try {
        recorder.clear();
        var single = summaries.summaries(List.of(first.candidateId())).getFirst();
        long oneCandidateSql = recorder.talentSql().size();
        assertThat(single.getFactsStatus()).isEqualTo(ResumeSummaryFactsStatus.READY);
        assertThat(single.getFacts().getWorkExperiences().getFirst().getStartDateText()).isEqualTo("2020");
        assertThat(single.getFacts().getWorkExperiences().getFirst().getDescriptionText()).isNull();
        assertThat(recorder.talentSql()).filteredOn(sql -> sql.contains("from talent_resume_work_fact"))
            .allSatisfy(sql -> assertThat(sql).doesNotContain("description_text", "responsibilities_json"));
        recorder.clear();
        var batch = summaries.summaries(List.of(second.candidateId(), first.candidateId()));
        assertThat(batch).extracting(row -> row.getCandidateId()).containsExactly(second.candidateId(), first.candidateId());
        assertThat(recorder.talentSql()).hasSize((int) oneCandidateSql);
        // 禁用跨语句一级缓存：十二次摘要读取，加三次人才域授权查询，人数增加不增加轮数。
        assertThat(oneCandidateSql).isEqualTo(15);
        assertThat(recorder.talentSql()).filteredOn(sql -> sql.contains("default_match_run_id from")).hasSize(1);
        assertThat(recorder.talentSql()).filteredOn(sql -> sql.contains("from talent_identity_conflict")).hasSize(1);
        second.becomeNextVersionOf(first);
        var latest = summaries.summaries(List.of(first.candidateId())).getFirst();
        assertThat(latest.getResumeVersion().getId()).isEqualTo(second.resumeVersionId());
        assertThat(latest.getResumeVersion().getId())
            .isEqualTo(talent.resumeVersions(first.candidateId(), null, 1).getItems().getFirst().getId());
      } finally {
        jdbc.update("DELETE FROM talent_resume_work_fact WHERE fact_snapshot_id=?", first.factId());
      }
    }
  }

  @Test void historyLookupsExecuteLimitWithoutCountAndKeepEarliestFactSemantics() {
    try (var fixture = fixture()) {
      String user = userId();
      fixture.appendCorrection(user);
      String parseId = jdbc.queryForObject("SELECT parse_snapshot_id FROM talent_resume_fact_snapshot WHERE id=?", String.class, fixture.factId());
      String intakeId = jdbc.queryForObject("SELECT intake_id FROM talent_resume_version WHERE id=?", String.class, fixture.resumeVersionId());
      String first = ids.newId(), newest = ids.newId();
      for (String conflictId : List.of(first, newest)) {
        jdbc.update("INSERT INTO talent_identity_conflict(id,intake_id,candidate_id_a,conflict_type,status) VALUES(?,?,?,'SAME_NAME','OPEN')",
            conflictId, intakeId, fixture.candidateId());
      }
      jdbc.update("UPDATE talent_identity_conflict SET create_time=? WHERE id=?",
          Timestamp.from(Instant.parse("2026-09-11T00:00:00Z")), first);
      jdbc.update("UPDATE talent_identity_conflict SET create_time=? WHERE id=?",
          Timestamp.from(Instant.parse("2026-09-11T00:00:01Z")), newest);
      try {
        recorder.clear();
        assertThat(parsing.findFactSnapshotByParse(parseId).getId()).isEqualTo(fixture.factId());
        assertThat(extraction.findLatestSuccessfulExtraction(fixture.resumeVersionId())).isNotNull();
        assertThat(conflicts.findOpenConflictForIntake(intakeId).getId()).isEqualTo(newest);
        jdbc.update("UPDATE talent_identity_conflict SET status='RESOLVED',resolved_at=? WHERE id=?",
            Timestamp.from(Instant.parse("2026-09-11T01:00:00Z")), first);
        jdbc.update("UPDATE talent_identity_conflict SET status='RESOLVED',resolved_at=? WHERE id=?",
            Timestamp.from(Instant.parse("2026-09-11T01:00:01Z")), newest);
        assertThat(conflicts.findResolvedConflictForIntake(intakeId).getId()).isEqualTo(newest);
        assertThat(recorder.talentSql()).hasSize(4).allSatisfy(sql -> assertThat(sql).contains("limit").doesNotContain("count("));
      } finally { jdbc.update("DELETE FROM talent_identity_conflict WHERE intake_id=?", intakeId); }
    }
  }

  private String userId() {
    return jdbc.queryForObject("SELECT id FROM identity_user_account WHERE normalized_email='resume-summary-it@example.com'", String.class);
  }
  private SearchFactExternalFixture fixture() {
    String user = userId();
    String department = jdbc.queryForObject("SELECT home_department_id FROM identity_user_account WHERE id=?", String.class, user);
    return new SearchFactExternalFixture(jdbc, ids, tasks, transactions, user, department);
  }
  private void seedListFacts(String snapshot) {
    jdbc.update("INSERT INTO talent_resume_work_fact(id,fact_snapshot_id,item_key,employer,description_text,responsibilities_json,achievements_json,ext)"
        + " VALUES(?,?,'work','Synthetic employer','unneeded long body','[]','[]',?)", ids.newId(), snapshot,
        "{\"sourceIndex\":0,\"startDateText\":\"2020\",\"endDateText\":\"PRESENT\"}");
  }

  @TestConfiguration static class SqlConfiguration {
    @Bean SqlRecorder sqlRecorder() { return new SqlRecorder(); }
  }
  @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
  static class SqlRecorder implements Interceptor {
    private final ThreadLocal<List<String>> statements = ThreadLocal.withInitial(ArrayList::new);
    @Override public Object intercept(Invocation invocation) throws Throwable {
      var statement = (StatementHandler) invocation.getTarget();
      statements.get().add(statement.getBoundSql().getSql().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "));
      return invocation.proceed();
    }
    void clear() { statements.remove(); }
    List<String> talentSql() { return statements.get().stream().filter(sql -> sql.contains("talent_")).toList(); }
  }
}
