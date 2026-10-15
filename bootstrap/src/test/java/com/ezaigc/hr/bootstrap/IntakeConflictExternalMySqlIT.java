package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.BackgroundActorExecutor;
import com.ezaigc.hr.identity.application.manager.IdentityAdministrationApplicationManager;
import com.ezaigc.hr.talent.application.manager.TalentIntakeApplicationManager;
import com.ezaigc.hr.talent.application.support.IdentityConflictRecorder;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.CandidateIdentity;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.domain.dto.ConfirmIntakeDTO;
import jakarta.annotation.Resource;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 用真实鉴权、行锁和外键验证冲突落证；所有合成夹具仅留在本次可丢弃空库。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
@ActiveProfiles("external-it")
@ContextConfiguration(initializers = ExternalMySqlIsolationInitializer.class)
@SpringBootTest(properties = {
    "spring.datasource.url=${HR_DB_URL}", "spring.datasource.username=${HR_DB_USER}",
    "spring.datasource.password=${HR_DB_PASSWORD}", "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.datasource.hikari.maximum-pool-size=4", "spring.datasource.hikari.connection-timeout=5000",
    // 确认与独立 recorder 使用不同连接，必须在池内每条连接设置锁等待预算。
    "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=2",
    "hr.object-storage.mode=local", "hr.object-storage.local-root=${HR_EXTERNAL_OBJECT_ROOT}",
    "hr.search.mode=memory", "hr.malware-scan.mode=development",
    "hr.pagination.cursor-signing-key=external-it-only-cursor-signing-key-32-bytes",
    "hr.bootstrap-admin.enabled=true", "hr.bootstrap-admin.department-code=ROOT",
    "hr.bootstrap-admin.department-name=冲突测试总部", "hr.bootstrap-admin.display-name=冲突测试管理员",
    "hr.bootstrap-admin.work-email=intake-conflict-it@example.com",
    "hr.bootstrap-admin.password=Test-Only-Conflict-123!",
    "hr.resume-intake.worker-enabled=false", "hr.resume-parsing.worker-enabled=false",
    "hr.resume-lifecycle.enabled=false", "hr.jd-parsing.worker-enabled=false",
    "hr.matching.worker-enabled=false", "hr.search.worker-enabled=false",
    "hr.messaging.worker-enabled=false", "hr.messaging.consumer-enabled=false", "hr.mail-resume.enabled=false",
    "hr.wecom.sync.enabled=false", "hr.wecom.collaboration.worker-enabled=false",
    "hr.reporting.audit-capture.enabled=false", "hr.reporting.metric-capture.enabled=false",
    "hr.reporting.monthly-report.enabled=false"
})
class IntakeConflictExternalMySqlIT {
  private static final String ADMIN_EMAIL = "intake-conflict-it@example.com";
  private static final String CANDIDATE_STATE = "SELECT full_name,primary_email,primary_phone,owner_user_id,"
      + "owner_department_id,status,version,update_time FROM talent_candidate WHERE id=?";

  @Resource private JdbcTemplate jdbc;
  @Resource private DataSource dataSource;
  @Resource private PlatformIdGenerator ids;
  @Resource private TransactionTemplate transactions;
  @Resource private TalentIntakeApplicationManager manager;
  @Resource private IdentityAdministrationApplicationManager administration;
  @Resource private AuthorizationService authorization;
  @Resource private UserDetailsService users;
  @Resource private BackgroundActorExecutor backgroundActors;
  @Resource private IdentityConflictRecorder recorder;
  private SecurityContext previousContext;
  private String adminId;
  private String recruiterId;
  private String departmentId;

  @BeforeEach
  void authenticateARealRecruiter() {
    previousContext = SecurityContextHolder.getContext();
    authenticate(ADMIN_EMAIL);
    var admin = authorization.currentActor();
    adminId = admin.getUserId();
    departmentId = admin.getHomeDepartmentId();
    String email = "conflict-hr-" + ids.newId().toLowerCase(Locale.ROOT) + "@example.com";
    recruiterId = administration.createUser("Synthetic conflict recruiter", email, null, departmentId,
        null, Set.of(), Set.of("RECRUITER"), "合成身份冲突事务回归").getUserId();
    authenticate(email);
    assertThat(authorization.currentActor().isSuperAdmin()).isFalse();
  }

  @AfterEach
  void restoreAuthentication() {
    SecurityContextHolder.setContext(previousContext);
  }

  @ParameterizedTest(name = "automatic={0}, accessRequired={1}")
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void conflictsReleaseCandidateLocksBeforeCommittingEvidence(boolean automatic, boolean accessRequired)
      throws SQLException {
    Fixture fixture = fixture(automatic, accessRequired);
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    authorization.requireAccess("talent.manage", target("RESUME_INTAKE", fixture.intakeId(), recruiterId));
    assertThat(authorization.canAccess("talent.manage", target("CANDIDATE", fixture.candidateId(),
        accessRequired ? adminId : recruiterId))).isEqualTo(!accessRequired);
    var candidateBefore = jdbc.queryForMap(CANDIDATE_STATE, fixture.candidateId());
    long candidatesBefore = count(jdbc, "talent_candidate");
    long identitiesBefore = count(jdbc, "talent_candidate_identity");
    long resumesBefore = count(jdbc, "talent_resume");
    long versionsBefore = count(jdbc, "talent_resume_version");

    PlatformException failure = assertTimeout(Duration.ofSeconds(5), () -> assertThrows(PlatformException.class,
        () -> {
          if (automatic) manager.autoConfirm(fixture.intakeId(), facts(fixture));
          else manager.confirm(fixture.intakeId(), command(fixture, 0));
        }));
    assertConflict(failure);
    assertThat(failure.getMessage()).isEqualTo(accessRequired
        ? "联系方式命中无权处理的已有候选人，请联系有权限的负责人核验"
        : "联系方式命中已有候选人但姓名不同，已进入人工冲突队列");

    // 不复用 Spring 绑定连接：独立自动提交读才能证明错误返回后证据已经持久化。
    try (var connection = dataSource.getConnection()) {
      assertThat(connection.getAutoCommit()).isTrue();
      assertThat(connection.getCatalog()).matches("hr_platform_it_[0-9a-f]{32}");
      var evidence = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      assertThat(evidence.queryForObject("SELECT @@SESSION.innodb_lock_wait_timeout", Integer.class)).isEqualTo(2);
      var conflict = evidence.queryForMap("SELECT id,intake_id,candidate_id_a,candidate_id_b,conflict_type,status "
          + "FROM talent_identity_conflict WHERE intake_id=?", fixture.intakeId());
      assertThat(conflict).containsEntry("intake_id", fixture.intakeId())
          .containsEntry("candidate_id_a", fixture.candidateId()).containsEntry("candidate_id_b", null)
          .containsEntry("conflict_type", accessRequired ? "CANDIDATE_ACCESS_REQUIRED" : "CONTACT_NAME_MISMATCH")
          .containsEntry("status", "OPEN");
      var intake = evidence.queryForMap("SELECT status,version,candidate_id,resume_version_id "
          + "FROM talent_resume_intake WHERE id=?", fixture.intakeId());
      assertThat(intake).containsEntry("status", "READY_FOR_CONFIRMATION")
          .containsEntry("candidate_id", null).containsEntry("resume_version_id", null);
      long version = ((Number) intake.get("version")).longValue();
      assertThat(version).isEqualTo(automatic ? 1 : 0);
      assertThat(evidence.queryForMap(CANDIDATE_STATE, fixture.candidateId())).isEqualTo(candidateBefore);
      assertThat(count(evidence, "talent_candidate")).isEqualTo(candidatesBefore);
      assertThat(count(evidence, "talent_candidate_identity")).isEqualTo(identitiesBefore);
      assertThat(count(evidence, "talent_resume")).isEqualTo(resumesBefore);
      assertThat(count(evidence, "talent_resume_version")).isEqualTo(versionsBefore);

      // 自动准备已提交为 READY；重复自动推进不能再推进状态，人工重试仍复用原 OPEN。
      if (automatic) {
        var repeated = assertThrows(PlatformException.class,
            () -> manager.autoConfirm(fixture.intakeId(), facts(fixture)));
        assertThat(repeated.code()).isEqualTo("INTAKE_NOT_PARSING");
      }
      assertConflict(assertThrows(PlatformException.class,
          () -> manager.confirm(fixture.intakeId(), command(fixture, version))));
      assertThat(evidence.queryForObject("SELECT COUNT(*) FROM talent_identity_conflict "
          + "WHERE intake_id=? AND status='OPEN'", Long.class, fixture.intakeId())).isOne();
      assertThat(evidence.queryForObject("SELECT id FROM talent_identity_conflict WHERE intake_id=?",
          String.class, fixture.intakeId())).isEqualTo(conflict.get("id"));
    }
  }

  private Fixture fixture(boolean automatic, boolean accessRequired) {
    String candidate = ids.newId();
    String intake = ids.newId();
    String channel = ids.newId();
    String batch = ids.newId();
    String name = "Synthetic existing " + candidate;
    String email = "candidate-" + candidate.toLowerCase(Locale.ROOT) + "@example.com";
    transactions.executeWithoutResult(ignored -> {
      jdbc.update("INSERT INTO talent_candidate(id,full_name,primary_email,owner_user_id,owner_department_id,status) "
          + "VALUES(?,?,?,?,?,'ACTIVE')", candidate, name, email, accessRequired ? adminId : recruiterId, departmentId);
      jdbc.update("INSERT INTO talent_candidate_identity(id,candidate_id,identity_type,normalized_value_hash) "
          + "VALUES(?,?,'EMAIL',?)", ids.newId(), candidate, Sha256Utils.hashUtf8(email));
      jdbc.update("INSERT INTO talent_source_channel(id,code,name,channel_type,status) "
          + "VALUES(?,?,'Synthetic conflict channel','MANUAL_UPLOAD','ACTIVE')", channel, channel);
      jdbc.update("INSERT INTO talent_intake_batch(id,source_channel_id,source_recruiter_user_id,"
          + "default_owner_user_id,owner_department_id,status) VALUES(?,?,?,?,?,'OPEN')",
          batch, channel, recruiterId, recruiterId, departmentId);
      jdbc.update("INSERT INTO talent_resume_intake(id,batch_id,source_channel_id,source_recruiter_user_id,"
          + "owner_user_id,owner_department_id,original_file_name,media_type,size_bytes,content_sha256,"
          + "object_key,scan_status,status) VALUES(?,?,?,?,?,?,'synthetic.pdf','application/pdf',1,?,?,'CLEAN',?)",
          intake, batch, channel, recruiterId, recruiterId, departmentId, Sha256Utils.hashUtf8(intake),
          "synthetic/" + intake, automatic ? "PARSING" : "READY_FOR_CONFIRMATION");
    });
    return new Fixture(candidate, intake, accessRequired ? name : "Synthetic different " + candidate, email);
  }

  @Test
  void simultaneousRecordersReuseOneCommittedOpenConflict() throws Exception {
    Fixture fixture = fixture(false, false);
    var start = new CyclicBarrier(2);
    Callable<String> record = () -> {
      start.await(5, TimeUnit.SECONDS);
      return backgroundActors.execute(recruiterId, () -> recorder.record(fixture.intakeId(),
          fixture.candidateId(), null, "CONTACT_NAME_MISMATCH", 0));
    };
    String firstId;
    String secondId;
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(record);
      var second = executor.submit(record);
      firstId = first.get(5, TimeUnit.SECONDS);
      secondId = second.get(5, TimeUnit.SECONDS);
    }
    assertThat(secondId).isEqualTo(firstId);
    try (var connection = dataSource.getConnection()) {
      assertThat(connection.getAutoCommit()).isTrue();
      var evidence = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      assertThat(evidence.queryForObject("SELECT COUNT(*) FROM talent_identity_conflict "
          + "WHERE intake_id=? AND status='OPEN'", Long.class, fixture.intakeId())).isOne();
      assertThat(evidence.queryForObject("SELECT id FROM talent_identity_conflict WHERE intake_id=?",
          String.class, fixture.intakeId())).isEqualTo(firstId);
    }
  }

  @Test
  void simultaneousConfirmationsCommitOnlyOneConflictWithoutDatabaseLockFailures() throws Exception {
    Fixture fixture = fixture(false, false);
    var candidateBefore = jdbc.queryForMap(CANDIDATE_STATE, fixture.candidateId());
    long identitiesBefore = count(jdbc, "talent_candidate_identity");
    var start = new CyclicBarrier(2);
    Callable<PlatformException> confirm = () -> backgroundActors.execute(recruiterId, () -> {
      awaitBarrier(start);
      return assertThrows(PlatformException.class,
          () -> manager.confirm(fixture.intakeId(), command(fixture, 0)));
    });
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(confirm);
      var second = executor.submit(confirm);
      assertConflict(first.get(5, TimeUnit.SECONDS));
      assertConflict(second.get(5, TimeUnit.SECONDS));
    }
    try (var connection = dataSource.getConnection()) {
      assertThat(connection.getAutoCommit()).isTrue();
      var evidence = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      assertThat(evidence.queryForObject("SELECT COUNT(*) FROM talent_identity_conflict "
          + "WHERE intake_id=? AND status='OPEN'", Long.class, fixture.intakeId())).isOne();
      assertThat(evidence.queryForMap("SELECT candidate_id_a,conflict_type FROM talent_identity_conflict "
          + "WHERE intake_id=?", fixture.intakeId())).containsEntry("candidate_id_a", fixture.candidateId())
          .containsEntry("conflict_type", "CONTACT_NAME_MISMATCH");
      assertThat(evidence.queryForObject("SELECT status FROM talent_resume_intake WHERE id=?", String.class,
          fixture.intakeId())).isEqualTo("READY_FOR_CONFIRMATION");
      assertThat(evidence.queryForObject("SELECT version FROM talent_resume_intake WHERE id=?", Long.class,
          fixture.intakeId())).isZero();
      assertThat(evidence.queryForMap(CANDIDATE_STATE, fixture.candidateId())).isEqualTo(candidateBefore);
      assertThat(count(evidence, "talent_candidate_identity")).isEqualTo(identitiesBefore);
    }
  }

  @ParameterizedTest
  @CsvSource({"READY_FOR_CONFIRMATION,1,0", "IGNORED,0,0"})
  void staleOrTerminalIntakesRejectLateEvidence(String status, long currentVersion, long detectedVersion)
      throws SQLException {
    Fixture fixture = fixture(false, false);
    assertThat(jdbc.update("UPDATE talent_resume_intake SET status=?,version=? WHERE id=?",
        status, currentVersion, fixture.intakeId())).isOne();
    var failure = assertThrows(PlatformException.class, () -> recorder.record(fixture.intakeId(),
        fixture.candidateId(), null, "CONTACT_NAME_MISMATCH", detectedVersion));
    assertThat(failure.code()).isEqualTo("VERSION_CONFLICT");
    try (var connection = dataSource.getConnection()) {
      assertThat(connection.getAutoCommit()).isTrue();
      var evidence = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      assertThat(evidence.queryForObject("SELECT COUNT(*) FROM talent_identity_conflict WHERE intake_id=?",
          Long.class, fixture.intakeId())).isZero();
      assertThat(evidence.queryForMap("SELECT status,version FROM talent_resume_intake WHERE id=?",
          fixture.intakeId())).containsEntry("status", status);
      assertThat(evidence.queryForObject("SELECT version FROM talent_resume_intake WHERE id=?",
          Long.class, fixture.intakeId())).isEqualTo(currentVersion);
    }
  }

  @ParameterizedTest(name = "withResolvedDecision={0}")
  @ValueSource(booleans = {false, true})
  void unchangedIdentityAndResolvedDecisionSurviveLockCacheRefresh(boolean withResolvedDecision)
      throws SQLException {
    Fixture fixture = fixture(false, false);
    String existingName = jdbc.queryForObject("SELECT full_name FROM talent_candidate WHERE id=?",
        String.class, fixture.candidateId());
    if (withResolvedDecision) {
      transactions.executeWithoutResult(ignored -> {
        jdbc.update("INSERT INTO talent_identity_conflict(id,intake_id,candidate_id_a,conflict_type,status,"
            + "resolution,chosen_candidate_id,reason,resolved_at,resolved_by_user_id) "
            + "VALUES(?,?,?,'CONTACT_NAME_MISMATCH','RESOLVED','USE_EXISTING',?,"
            + "'Synthetic verified identity decision',CURRENT_TIMESTAMP(6),?)",
            ids.newId(), fixture.intakeId(), fixture.candidateId(), fixture.candidateId(), recruiterId);
        assertThat(jdbc.update("UPDATE talent_resume_intake SET failure_code='IDENTITY_CONFLICT_REQUIRES_REVIEW' "
            + "WHERE id=?", fixture.intakeId())).isOne();
      });
      assertThat(fixture.name()).isNotEqualTo(existingName);
    }
    long candidatesBefore = count(jdbc, "talent_candidate");
    long identitiesBefore = count(jdbc, "talent_candidate_identity");
    var candidateBefore = jdbc.queryForMap(CANDIDATE_STATE, fixture.candidateId());
    var result = assertTimeout(Duration.ofSeconds(5), () -> manager.confirm(fixture.intakeId(),
        new ConfirmIntakeDTO(null, withResolvedDecision ? fixture.name() : existingName,
            fixture.email(), null, 0)));
    assertThat(result.getStatus()).isEqualTo("CONFIRMED");
    assertThat(result.getCandidateId()).isEqualTo(fixture.candidateId());
    assertThat(result.getResumeVersionId()).isNotBlank();
    try (var connection = dataSource.getConnection()) {
      assertThat(connection.getAutoCommit()).isTrue();
      var evidence = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      assertThat(evidence.queryForMap("SELECT status,candidate_id,resume_version_id "
          + "FROM talent_resume_intake WHERE id=?", fixture.intakeId())).containsEntry("status", "CONFIRMED")
          .containsEntry("candidate_id", fixture.candidateId())
          .containsEntry("resume_version_id", result.getResumeVersionId());
      assertThat(evidence.queryForMap("SELECT r.candidate_id,v.intake_id,v.version_no "
          + "FROM talent_resume_version v JOIN talent_resume r ON r.id=v.resume_id WHERE v.id=?",
          result.getResumeVersionId())).containsEntry("candidate_id", fixture.candidateId())
          .containsEntry("intake_id", fixture.intakeId()).containsEntry("version_no", 1);
      assertThat(count(evidence, "talent_candidate")).isEqualTo(candidatesBefore);
      assertThat(count(evidence, "talent_candidate_identity")).isEqualTo(identitiesBefore);
      assertThat(evidence.queryForMap(CANDIDATE_STATE, fixture.candidateId())).isEqualTo(candidateBefore);
      assertThat(evidence.queryForObject("SELECT COUNT(*) FROM talent_identity_conflict "
          + "WHERE intake_id=? AND status='OPEN'", Long.class, fixture.intakeId())).isZero();
    }
  }

  private void authenticate(String email) {
    var principal = users.loadUserByUsername(email);
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    SecurityContextHolder.setContext(context);
  }

  private AuthorizationTarget target(String type, String id, String owner) {
    return new AuthorizationTarget(type, id, owner, departmentId, Set.of());
  }

  private static ConfirmIntakeDTO command(Fixture fixture, long version) {
    return new ConfirmIntakeDTO(null, fixture.name(), fixture.email(), null, version);
  }

  private static ResumeFacts facts(Fixture fixture) {
    return new ResumeFacts(new CandidateIdentity(fixture.name(), fixture.email(), null,
        null, null, null, null, null), List.of(), List.of(), List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of());
  }

  private static void assertConflict(PlatformException failure) {
    assertThat(failure.status().value()).isEqualTo(409);
    assertThat(failure.code()).isEqualTo("IDENTITY_CONFLICT_REQUIRES_REVIEW");
  }

  private static void awaitBarrier(CyclicBarrier barrier) {
    try {
      barrier.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Synthetic confirmation interrupted", exception);
    } catch (BrokenBarrierException | TimeoutException exception) {
      throw new AssertionError("Synthetic confirmation barrier failed", exception);
    }
  }

  private static long count(JdbcTemplate template, String table) {
    return template.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
  }

  private record Fixture(String candidateId, String intakeId, String name, String email) {}
}
