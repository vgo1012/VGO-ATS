package com.ezaigc.hr.bootstrap;

import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.intelligence.search.application.assembler.SearchDocumentFactory;
import com.ezaigc.hr.intelligence.search.application.manager.SearchTaskApplicationManager;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.application.manager.SearchIndexAuthorityApplicationManager;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchCriteria;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.ezaigc.hr.talent.api.ResumeSearchStateSource;
import com.ezaigc.hr.recruiting.recruitment.api.MyTaskCandidateQuery;
import com.ezaigc.hr.intelligence.matching.constant.DefaultScoringConstants;
import com.ezaigc.hr.talent.application.manager.TalentIntakeApplicationManager;
import com.ezaigc.hr.talent.domain.dto.ConfirmIntakeDTO;
import com.ezaigc.hr.talent.domain.dto.ResolveIdentityConflictDTO;
import com.ezaigc.hr.talent.domain.dto.TalentVersionReasonDTO;
import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

/** 实际人才入口、实际权限读取与Outbox事务；MySQL仅用新建空库，索引使用可观测的内存适配器。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
@ActiveProfiles("external-it")
@ContextConfiguration(initializers = ExternalMySqlIsolationInitializer.class)
@SpringBootTest(properties = {
    "spring.datasource.url=${HR_DB_URL}", "spring.datasource.username=${HR_DB_USER}",
    "spring.datasource.password=${HR_DB_PASSWORD}", "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.datasource.hikari.maximum-pool-size=4", "spring.datasource.hikari.connection-timeout=5000",
    "hr.object-storage.mode=local", "hr.object-storage.local-root=${HR_EXTERNAL_OBJECT_ROOT}",
    "hr.search.mode=memory", "hr.malware-scan.mode=development",
    "hr.pagination.cursor-signing-key=external-it-only-cursor-signing-key-32-bytes",
    "hr.bootstrap-admin.enabled=true", "hr.bootstrap-admin.department-code=ROOT",
    "hr.bootstrap-admin.department-name=测试总部", "hr.bootstrap-admin.display-name=搜索生命周期测试管理员",
    "hr.bootstrap-admin.work-email=search-lifecycle-it@example.com",
    "hr.bootstrap-admin.password=Test-Only-Lifecycle-123!",
    "hr.resume-parsing.worker-enabled=false", "hr.jd-parsing.worker-enabled=false",
    "hr.matching.worker-enabled=false", "hr.search.worker-enabled=false", "hr.messaging.worker-enabled=false",
    "hr.reporting.audit-capture.enabled=false"
})
class TalentSearchLifecycleExternalMySqlIT {
  @Resource private JdbcTemplate jdbc;
  @Resource private MyTaskCandidateQuery myTasks;
  @Resource private PlatformIdGenerator ids;
  @Resource private AsyncTaskPort tasks;
  @Resource private AsyncTaskBatchRunner runner;
  @Resource private TransactionTemplate transactions;
  @Resource private TalentIntakeApplicationManager manager;
  @Resource private ResumeSearchStateSource states;
  @Resource private SearchDocumentFactory factory;
  @Resource private SearchIndexPort index;
  @Resource private SearchIndexAuthorityApplicationManager authority;
  @Resource private SearchTaskApplicationManager searchTasks;
  @Resource private UserDetailsService userDetails;
  private SecurityContext previousContext;

  @BeforeEach
  void authenticateTheIsolatedAdministrator() {
    previousContext = SecurityContextHolder.getContext();
    var principal = userDetails.loadUserByUsername("search-lifecycle-it@example.com");
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    SecurityContextHolder.setContext(context);
  }

  @AfterEach
  void restoreContext() {
    SecurityContextHolder.setContext(previousContext);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void confirmingAnUnparsedVersionHidesTheOldFactsAndRollsBackIfOutboxFails(boolean failOutbox) {
    try (var fixture = fixture()) {
      seedIndex(fixture);
      String intake = pendingIntake(fixture);
      try {
        var before = state();
        if (failOutbox) rejectOutboxFor(fixture.candidateId());
        try {
          var command = new ConfirmIntakeDTO(fixture.candidateId(), "Synthetic projection candidate", null, null, 0);
          if (failOutbox) {
            assertThatThrownBy(() -> manager.confirm(intake, command)).isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("Synthetic lifecycle outbox failure");
            assertThat(state()).isEqualTo(before);
            return;
          }
          var result = manager.confirm(intake, command);
          assertThat(result.getResumeVersionId()).isNotEqualTo(fixture.resumeVersionId());
          assertThat(states.currentState(fixture.candidateId()).resumeVersionNo()).isEqualTo(2);
          assertThat(states.currentState(fixture.candidateId()).facts()).isNull();
          assertWorkBindings(fixture.candidateId(), 1);
          drain(1);
          assertThat(hits(fixture.candidateId())).isEmpty();
          assertThatThrownBy(() -> manager.confirm(intake, command)).isInstanceOf(RuntimeException.class);
          assertWorkBindings(fixture.candidateId(), 1);
        } finally {
          if (failOutbox) jdbc.execute("DROP TRIGGER search_lifecycle_reject");
        }
      } finally {
        cleanupIntake(intake);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void deletionAndItsTombstoneIntentCommitOrRollBackTogether(boolean failOutbox) {
    try (var fixture = fixture()) {
      seedIndex(fixture);
      var before = state();
      if (failOutbox) rejectOutboxFor(fixture.candidateId());
      try {
        var command = new TalentVersionReasonDTO(0, "Synthetic lifecycle deletion");
        if (failOutbox) {
          assertThatThrownBy(() -> manager.deleteCandidate(fixture.candidateId(), command)).isInstanceOf(RuntimeException.class)
              .hasStackTraceContaining("Synthetic lifecycle outbox failure");
          assertThat(state()).isEqualTo(before);
          return;
        }
        manager.deleteCandidate(fixture.candidateId(), command);
        assertThat(states.currentState(fixture.candidateId()).deleted()).isTrue();
        assertWorkBindings(fixture.candidateId(), 1);
        drain(1);
        assertThat(hits(fixture.candidateId())).isEmpty();
        assertThatThrownBy(() -> manager.deleteCandidate(fixture.candidateId(), command)).isInstanceOf(RuntimeException.class);
        assertWorkBindings(fixture.candidateId(), 1);
      } finally {
        if (failOutbox) jdbc.execute("DROP TRIGGER search_lifecycle_reject");
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void mergePublishesBothSidesAndSecondOutboxFailureRollsBackTheEntireMerge(boolean failOutbox) {
    try (var survivor = fixture(); var merged = fixture()) {
      seedIndex(survivor); seedIndex(merged);
      String conflict = ids.newId();
      jdbc.update("INSERT INTO talent_identity_conflict(id,intake_id,candidate_id_a,candidate_id_b,conflict_type,status) "
          + "VALUES(?,?,?,?, 'STRONG_IDENTITY_MISMATCH','OPEN')", conflict, intakeId(survivor), survivor.candidateId(), merged.candidateId());
      try {
        var before = state();
        if (failOutbox) rejectOutboxFor(merged.candidateId());
        try {
          var command = new ResolveIdentityConflictDTO("MERGE", survivor.candidateId(), "Synthetic lifecycle merge");
          if (failOutbox) {
            assertThatThrownBy(() -> manager.resolveConflict(conflict, command)).isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("Synthetic lifecycle outbox failure");
            assertThat(state()).isEqualTo(before);
            return;
          }
          manager.resolveConflict(conflict, command);
          assertThat(states.currentState(merged.candidateId()).deleted()).isTrue();
          assertThat(states.currentState(survivor.candidateId()).facts().getSnapshotId()).isEqualTo(merged.factId());
          assertWorkBindings(survivor.candidateId(), 1); assertWorkBindings(merged.candidateId(), 1);
          drain(2);
          assertThat(hits(merged.candidateId())).isEmpty();
          assertThat(hits(survivor.candidateId())).extracting(SearchDocument::getFactSnapshotId).containsExactly(merged.factId());
        } finally {
          if (failOutbox) jdbc.execute("DROP TRIGGER search_lifecycle_reject");
        }
      } finally {
        jdbc.update("DELETE FROM talent_process_event WHERE target_id=?", conflict);
        jdbc.update("DELETE FROM talent_candidate_merge_history WHERE identity_conflict_id=?", conflict);
        jdbc.update("DELETE FROM talent_identity_conflict WHERE id=?", conflict);
      }
    }
  }

  @Test
  void taskJobFilterUsesTheSameActiveOwnedApplicationAndDeduplicatesItsSteps() {
    try (var fixture = fixture()) {
      transactions.executeWithoutResult(transaction -> {
        transaction.setRollbackOnly();
        String user = jdbc.queryForObject("SELECT owner_user_id FROM talent_candidate WHERE id=?",
            String.class, fixture.candidateId());
        String department = jdbc.queryForObject("SELECT owner_department_id FROM talent_candidate WHERE id=?",
            String.class, fixture.candidateId());
        String selected = ids.newId(), other = ids.newId();
        for (String job : List.of(selected, other)) {
          jdbc.update("INSERT INTO demand_jd(id,title,status) VALUES(?,'Synthetic task job','ACTIVE')", job);
          jdbc.update("INSERT INTO demand_jd_version(id,jd_id,version_no,title,raw_text,status,source_type) "
              + "VALUES(?,?,1,'Synthetic task job','Synthetic','PUBLISHED','MANUAL')", job, job);
          jdbc.update("INSERT INTO demand_workflow_template_version(id,jd_version_id,version_no,recruitment_type,status,active_slot) "
              + "VALUES(?,?,1,'INTERNAL','ACTIVE',1)", job, job);
          jdbc.update("INSERT INTO recruitment_application(id,candidate_id,jd_id,jd_version_id,resume_version_id,"
              + "workflow_template_version_id,match_plan_version_id,recruitment_type,current_stage,owner_user_id,owner_department_id) "
              + "VALUES(?,?,?,?,?,?,?,'INTERNAL','IN_PROCESS',?,?)", job, fixture.candidateId(), job, job,
              fixture.resumeVersionId(), job, DefaultScoringConstants.PLAN_VERSION_ID, user, department);
          jdbc.update("INSERT INTO recruitment_application_workflow_step(id,application_id,template_version_id,"
              + "step_code,sequence_no,candidate_optional,status,owner_user_id) "
              + "VALUES(?,?,?,'DECISION',1,FALSE,'PENDING',?)", job, job, job, job.equals(selected) ? user : ids.newId());
        }
        var candidates = List.of(fixture.candidateId());
        assertThat(myTasks.candidateIds(candidates, selected)).containsExactly(fixture.candidateId());
        // 另一个岗位虽然属于同一候选人，但任务分配给其他 HR，不能借本人岗位的任务进入结果。
        assertThat(myTasks.candidateIds(candidates, other)).isEmpty();
        jdbc.update("INSERT INTO recruitment_application_workflow_step(id,application_id,template_version_id,"
            + "step_code,sequence_no,candidate_optional,status,owner_user_id) "
            + "VALUES(?,?,?,'HR_COMMUNICATION',2,TRUE,'IN_PROGRESS',?)", ids.newId(), selected, selected, user);
        assertThat(myTasks.candidateIds(candidates, null)).containsExactly(fixture.candidateId());
        assertThat(myTasks.candidateIds(candidates, selected)).containsExactly(fixture.candidateId());
        jdbc.update("UPDATE recruitment_application_workflow_step SET status='PASSED' WHERE application_id=?", selected);
        assertThat(myTasks.candidateIds(candidates, selected)).isEmpty();
        jdbc.update("UPDATE recruitment_application_workflow_step SET status='PENDING' WHERE application_id=?", selected);
        jdbc.update("UPDATE recruitment_application SET terminal_at=CURRENT_TIMESTAMP(6),current_stage='TERMINATED',"
            + "outcome='WITHDRAW',active_slot=NULL WHERE id=?", selected);
        assertThat(myTasks.candidateIds(candidates, selected)).isEmpty();
      });
    }
  }

  @Test
  void rejectedVersionLeavesTheCandidateAndDeliveryHistoryUntouched() {
    try (var fixture = fixture()) {
      var before = state();
      assertThatThrownBy(() -> manager.deleteCandidate(fixture.candidateId(), new TalentVersionReasonDTO(1, "Stale version")))
          .isInstanceOf(RuntimeException.class);
      assertThat(state()).isEqualTo(before);
    }
  }

  private SearchFactExternalFixture fixture() {
    String user = jdbc.queryForObject("SELECT id FROM identity_user_account WHERE normalized_email='search-lifecycle-it@example.com'", String.class);
    String department = jdbc.queryForObject("SELECT home_department_id FROM identity_user_account WHERE id=?", String.class, user);
    return new SearchFactExternalFixture(jdbc, ids, tasks, transactions, user, department);
  }

  private void seedIndex(SearchFactExternalFixture fixture) {
    index.upsertBatch(authority.currentTarget(), List.of(factory.create(states.currentState(fixture.candidateId()))));
    assertThat(hits(fixture.candidateId())).extracting(SearchDocument::getFactSnapshotId).containsExactly(fixture.factId());
  }

  private List<SearchDocument> hits(String candidate) {
    return index.search(authority.currentTarget(), new SearchCriteria(), null, 100).getItems().stream()
        .filter(document -> candidate.equals(document.getCandidateId())).toList();
  }

  private void drain(int expected) {
    for (int i = 0; i < expected; i++) assertThat(runner.run("RESUME_SEARCH_PROJECT", 1, searchTasks::process)).isOne();
    assertThat(runner.run("RESUME_SEARCH_PROJECT", 1, searchTasks::process)).isZero();
  }

  private void assertWorkBindings(String candidate, int count) {
    var rows = jdbc.queryForList("SELECT e.work_task_id,e.owner_user_id,e.owner_department_id "
        + "FROM reporting_outbox_event e WHERE aggregate_id=?", candidate);
    assertThat(rows).hasSize(count);
    for (var event : rows) {
      var task = tasks.get((String) event.get("work_task_id"));
      assertThat(task.resourceId()).isEqualTo(candidate);
      assertThat(task.taskType()).isEqualTo("RESUME_SEARCH_PROJECT");
      assertThat(task.maxAttempts()).isEqualTo(5);
      assertThat(task.ownerUserId()).isEqualTo(event.get("owner_user_id"));
      assertThat(task.ownerDepartmentId()).isEqualTo(event.get("owner_department_id"));
    }
  }

  private String intakeId(SearchFactExternalFixture fixture) {
    return jdbc.queryForObject("SELECT intake_id FROM talent_resume_version WHERE id=?", String.class, fixture.resumeVersionId());
  }

  private String pendingIntake(SearchFactExternalFixture fixture) {
    String intake = ids.newId();
    jdbc.update("INSERT INTO talent_resume_intake(id,batch_id,source_channel_id,source_recruiter_user_id,owner_user_id,"
        + "owner_department_id,original_file_name,media_type,size_bytes,content_sha256,object_key,scan_status,status) "
        + "SELECT ?,batch_id,source_channel_id,source_recruiter_user_id,owner_user_id,owner_department_id,"
        + "original_file_name,media_type,size_bytes,?,object_key,'CLEAN','READY_FOR_CONFIRMATION' "
        + "FROM talent_resume_intake WHERE id=?", intake, "b".repeat(64), intakeId(fixture));
    return intake;
  }

  private void cleanupIntake(String intake) {
    var versions = jdbc.queryForList("SELECT id FROM talent_resume_version WHERE intake_id=?", String.class, intake);
    jdbc.update("UPDATE talent_resume_intake SET candidate_id=NULL,resume_version_id=NULL WHERE id=?", intake);
    for (String version : versions) jdbc.update("DELETE FROM talent_process_event WHERE target_id=?", version);
    jdbc.update("DELETE FROM talent_resume_version WHERE intake_id=?", intake);
    jdbc.update("DELETE FROM talent_resume_intake WHERE id=?", intake);
  }

  private Map<String, Object> state() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    for (String table : List.of("talent_candidate", "talent_candidate_identity", "talent_resume", "talent_resume_version",
        "talent_resume_intake", "talent_identity_conflict", "talent_candidate_merge_history", "talent_process_event",
        "reporting_outbox_event", "reporting_async_task", "reporting_async_task_attempt")) {
      snapshot.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
    }
    return snapshot;
  }

  private void rejectOutboxFor(String candidate) {
    if (!candidate.matches("[0-9A-HJKMNP-TV-Z]{26}")) throw new IllegalArgumentException("isolated candidate id required");
    jdbc.execute("CREATE TRIGGER search_lifecycle_reject BEFORE INSERT ON reporting_outbox_event FOR EACH ROW "
        + "BEGIN IF NEW.aggregate_id='" + candidate + "' THEN SIGNAL SQLSTATE '45000' "
        + "SET MESSAGE_TEXT='Synthetic lifecycle outbox failure'; END IF; END");
  }
}
