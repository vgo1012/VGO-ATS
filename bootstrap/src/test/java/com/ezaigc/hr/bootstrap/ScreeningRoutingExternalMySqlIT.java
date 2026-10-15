package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.ezaigc.hr.intelligence.matching.constant.DefaultScoringConstants;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchJobQuery;
import com.ezaigc.hr.intelligence.matching.domain.dto.ScreeningSelectionQuery;
import com.ezaigc.hr.intelligence.matching.domain.entity.ScreeningAdmission;
import com.ezaigc.hr.intelligence.matching.domain.entity.ScreeningCandidateRow;
import com.ezaigc.hr.intelligence.matching.mapper.MatchRunMapper;
import com.ezaigc.hr.intelligence.matching.mapper.ScreeningAdmissionMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** 显式外部测试环境下使用独立临时库和合成评分，执行真实分流 SQL。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
class ScreeningRoutingExternalMySqlIT {
  private static final String OWNER = "O".repeat(26);
  private static final String DEPARTMENT = "D".repeat(26);
  private static final String JD = "J".repeat(26);
  private static final String RUN = "R".repeat(26);
  private static final Instant NOW = Instant.parse("2026-09-10T08:00:00Z");

  @Test
  void batchInsertCountsOnlyNewIdentitiesAndPreservesReturnedAdmissions() throws Exception {
    withDatabase(connection -> {
      execute(connection, sqlRoot().resolve("hr_ai_complete_schema.sql"));
      var dataSource = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(dataSource);
      // 仅此临时连接省略外模块父夹具，准入唯一键和字段默认值保持真实部署定义。
      jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
      var configuration = new MybatisConfiguration();
      configuration.setMapUnderscoreToCamelCase(true);
      configuration.setEnvironment(new Environment("screening-batch-insert-it", new JdbcTransactionFactory(), dataSource));
      configuration.addMapper(ScreeningAdmissionMapper.class);
      try (var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true)) {
        var mapper = session.getMapper(ScreeningAdmissionMapper.class);
        List<ScreeningAdmission> fresh = List.of(batchAdmission(1, 1), batchAdmission(2, 2));
        assertThat(mapper.insertBatchIgnore(fresh)).isEqualTo(fresh.size());
        for (ScreeningAdmission expected : fresh) {
          ScreeningAdmission actual = mapper.findByIdentity(
              expected.getCandidateId(), expected.getResumeVersionId(), expected.getJdVersionId());
          assertThat(actual.getId()).isEqualTo(expected.getId());
          assertThat(actual.getDefaultMatchRunId()).isEqualTo(expected.getDefaultMatchRunId());
          assertThat(actual.getAdmittedByUserId()).isEqualTo(expected.getAdmittedByUserId());
          assertThat(actual.getOwnerDepartmentId()).isEqualTo(expected.getOwnerDepartmentId());
          assertThat(actual.getAdmittedAt()).isEqualTo(NOW);
          assertThat(actual.getVersion()).isZero();
          assertThat(actual.getReturnedAt()).isNull();
        }

        assertThat(mapper.insertBatchIgnore(List.of(batchAdmission(1, 11), batchAdmission(2, 12)))).isZero();
        Instant returnedAt = NOW.plusSeconds(1);
        assertThat(mapper.returnToResumes(admission(1), 0, OWNER, returnedAt)).isEqualTo(1);
        ScreeningAdmission returnedDuplicate = batchAdmission(1, 21);
        returnedDuplicate.setDefaultMatchRunId("S".repeat(26));
        returnedDuplicate.setAdmittedAt(NOW.plusSeconds(2));
        assertThat(mapper.insertBatchIgnore(List.of(returnedDuplicate, batchAdmission(3, 3)))).isEqualTo(1);

        ScreeningAdmission returned = mapper.findByIdentity(candidate(1), resume(1), JD);
        assertThat(returned.getId()).isEqualTo(admission(1));
        assertThat(returned.getVersion()).isEqualTo(1);
        assertThat(returned.getReturnedAt()).isEqualTo(returnedAt);
        assertThat(returned.getDefaultMatchRunId()).isEqualTo(RUN);
        assertThat(returned.getAdmittedAt()).isEqualTo(NOW);
        assertThat(mapper.findByIdentity(candidate(2), resume(2), JD).getId()).isEqualTo(admission(2));
        ScreeningAdmission added = mapper.findByIdentity(candidate(3), resume(3), JD);
        assertThat(added.getId()).isEqualTo(admission(3));
        assertThat(added.getVersion()).isZero();
        assertThat(added.getReturnedAt()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM matching_screening_admission", Integer.class)).isEqualTo(3);
      }
    });
  }

  private static ScreeningAdmission batchAdmission(int identityNumber, int idNumber) {
    var row = new ScreeningAdmission();
    row.setId(admission(idNumber));
    row.setCandidateId(candidate(identityNumber));
    row.setResumeVersionId(resume(identityNumber));
    row.setJdVersionId(JD);
    row.setDefaultMatchRunId(RUN);
    row.setAdmittedByUserId(OWNER);
    row.setOwnerDepartmentId(DEPARTMENT);
    row.setAdmittedAt(NOW);
    row.setCreateId(OWNER);
    row.setCreateTime(NOW);
    row.setUpdateId(OWNER);
    row.setUpdateTime(NOW);
    return row;
  }

  @Test
  void selectionFiltersExactAdmissionsAndNamesBeforePagingAndPreservesResumeVersions() throws Exception {
    withDatabase(connection -> {
      execute(connection, sqlRoot().resolve("hr_ai_complete_schema.sql"));
      var dataSource = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(dataSource);
      jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
      seed(jdbc);
      for (int index = 1; index <= 2; index++) {
        jdbc.update("INSERT INTO talent_candidate(id,full_name,owner_user_id,owner_department_id,status)"
            + " VALUES(?,?,?,?,'ACTIVE')", candidate(index), "合成" + index, OWNER, DEPARTMENT);
        jdbc.update("INSERT INTO talent_resume_version(id,resume_id,intake_id,version_no,original_file_name,"
            + "media_type,size_bytes,content_sha256,object_key,status)"
            + " VALUES(?,?,?,?,?,'application/pdf',10,?,'synthetic','ACTIVE')",
            resume(index), candidate(index), resume(index), index, "简历" + index + ".pdf", String.valueOf(index).repeat(64));
      }
      var configuration = new MybatisConfiguration();
      configuration.setMapUnderscoreToCamelCase(true);
      configuration.setEnvironment(new Environment("screening-selection-it", new JdbcTransactionFactory(), dataSource));
      configuration.addMapper(ScreeningAdmissionMapper.class);
      try (var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true)) {
        var mapper = session.getMapper(ScreeningAdmissionMapper.class);
        var query = new ScreeningSelectionQuery();
        query.setJdVersionId(JD);
        query.setActorId(OWNER);
        query.setManagedUserIds(Set.of());
        query.setVisibleDepartmentIds(Set.of());
        query.setLimit(1);
        assertThat(mapper.findSelectionCandidates(query)).isEmpty();
        mapper.insertIgnore(admission(1), candidate(1), resume(1), JD, RUN, OWNER, DEPARTMENT, NOW);
        mapper.insertIgnore(admission(2), candidate(2), resume(2), JD, RUN, OWNER, DEPARTMENT, NOW);
        assertThat(mapper.findSelectionCandidates(query)).singleElement().satisfies(row -> {
          assertThat(row.getResumeVersionId()).isEqualTo(resume(2));
          assertThat(row.getCandidateName()).isEqualTo("合成2");
          assertThat(row.getResumeVersionNo()).isEqualTo(2);
          assertThat(row.getOriginalFileName()).isEqualTo("简历2.pdf");
          assertThat(row.getProfessionalScore()).isEqualByComparingTo("80");
        });
        query.setBeforeTime(NOW);
        query.setBeforeId(admission(2));
        assertThat(mapper.findSelectionCandidates(query)).extracting(ScreeningCandidateRow::getResumeVersionId)
            .containsExactly(resume(1));
        query.setBeforeTime(null);
        query.setBeforeId(null);
        query.setQuery("合成1");
        assertThat(mapper.findSelectionCandidates(query)).extracting(ScreeningCandidateRow::getCandidateId)
            .containsExactly(candidate(1));
        query.setQuery(null);
        query.setJdVersionId("K".repeat(26));
        assertThat(mapper.findSelectionCandidates(query)).isEmpty();
        query.setJdVersionId(JD);
        query.setActorId("X".repeat(26));
        assertThat(mapper.findSelectionCandidates(query)).isEmpty();
        query.setManagedUserIds(Set.of(OWNER));
        assertThat(mapper.findSelectionCandidates(query)).hasSize(1);
        query.setManagedUserIds(Set.of());
        query.setVisibleDepartmentIds(Set.of(DEPARTMENT));
        assertThat(mapper.findSelectionCandidates(query)).hasSize(1);
        query.setVisibleDepartmentIds(Set.of());
        query.setScopeAll(true);
        query.setLimit(20);
        // 同一候选人的两个已准入版本必须能分别重评，不按自然人合并。
        // 每次评分运行内候选人唯一；两个版本的结果应来自两次运行。
        String secondRun = "S".repeat(26);
        seedRun(jdbc, secondRun, 1);
        jdbc.update("UPDATE matching_match_run SET total_count=1,succeeded_count=1 WHERE id=?", RUN);
        jdbc.update("UPDATE matching_screening_admission SET candidate_id=?,default_match_run_id=? WHERE id=?",
            candidate(1), secondRun, admission(2));
        jdbc.update("UPDATE matching_weighted_result SET candidate_id=?,match_run_id=? WHERE resume_version_id=?",
            candidate(1), secondRun, resume(2));
        jdbc.update("UPDATE talent_resume_intake SET candidate_id=?,score_run_id=? WHERE resume_version_id=?",
            candidate(1), secondRun, resume(2));
        session.clearCache();
        assertThat(mapper.findSelectionCandidates(query)).extracting(ScreeningCandidateRow::getResumeVersionId)
            .containsExactly(resume(2), resume(1));
        mapper.returnToResumes(admission(2), 0, OWNER, NOW.plusSeconds(1));
        assertThat(mapper.findSelectionCandidates(query)).extracting(ScreeningCandidateRow::getResumeVersionId)
            .containsExactly(resume(1));
        recruitment(jdbc, candidate(1), resume(1));
        session.clearCache();
        assertThat(mapper.findSelectionCandidates(query)).isEmpty();
      }
    });
  }

  @Test
  void admissionsReturnAndReadmissionPreserveHistoryAndRejectStaleVersions() throws Exception {
    withDatabase(connection -> {
      execute(connection, sqlRoot().resolve("hr_ai_complete_schema.sql"));
      execute(connection, upgrade());
      execute(connection, upgrade());
      var dataSource = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(dataSource);
      // 省略与查询无关的外模块父夹具；仅当前临时连接关闭外键检查，不改变表约束。
      jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
      seed(jdbc);
      var configuration = new MybatisConfiguration();
      configuration.setMapUnderscoreToCamelCase(true);
      configuration.setEnvironment(new Environment("screening-routing-it", new JdbcTransactionFactory(), dataSource));
      configuration.addMapper(ScreeningAdmissionMapper.class);
      configuration.addMapper(MatchRunMapper.class);
      try (var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true)) {
        var admissions = session.getMapper(ScreeningAdmissionMapper.class);
        var runs = session.getMapper(MatchRunMapper.class);
        assertThat(admitted(admissions)).isEmpty();
        assertThat(admissions.findScreeningCandidates(List.of(candidate(1), candidate(2)), OWNER,
            false, Set.of(), Set.of(), false)).hasSize(2);
        assertThat(admissions.insertIgnore(admission(1), candidate(1), resume(1), JD, RUN, OWNER, DEPARTMENT, NOW)).isEqualTo(1);
        assertThat(admissions.insertIgnore(admission(2), candidate(2), resume(2), JD, RUN, OWNER, DEPARTMENT, NOW)).isEqualTo(1);
        assertThat(admissions.insertIgnore("Z".repeat(26), candidate(1), resume(1), JD, RUN, OWNER, DEPARTMENT, NOW)).isZero();
        assertThat(admitted(admissions)).hasSize(2);
        assertThat(admissions.activeResumeVersionIds(List.of(candidate(1), candidate(2))))
            .containsExactlyInAnyOrder(resume(1), resume(2));
        assertThat(admissions.lockAdmission(admission(1)).getVersion()).isZero();

        MatchJobQuery pending = query();
        assertThat(runs.findJobs(pending)).singleElement().satisfies(job -> assertThat(job.getCandidateCount()).isEqualTo(2));
        assertThat(runs.findJobBases(pending)).singleElement().satisfies(basis -> assertThat(basis.getCandidateCount()).isEqualTo(2));
        assertThat(runs.findJobResults(pending)).hasSize(2);
        String beforeReturn = runs.findJobRevision(pending);
        assertThat(admissions.returnToResumes(admission(1), 0, OWNER, NOW.plusSeconds(1))).isEqualTo(1);
        assertThat(admissions.returnToResumes(admission(1), 0, OWNER, NOW.plusSeconds(2))).isZero();
        assertThat(admissions.lockAdmission(admission(1)).getVersion()).isEqualTo(1);
        assertThat(admitted(admissions)).extracting(ScreeningCandidateRow::getCandidateId).containsExactly(candidate(2));
        assertThat(admissions.activeResumeVersionIds(List.of(candidate(1), candidate(2)))).containsExactly(resume(2));
        assertThat(runs.findJobs(pending)).singleElement().satisfies(job -> assertThat(job.getCandidateCount()).isEqualTo(1));
        assertThat(runs.findJobBases(pending)).singleElement().satisfies(basis -> assertThat(basis.getCandidateCount()).isEqualTo(1));
        assertThat(runs.findJobResults(pending)).extracting(result -> result.getCandidateId()).containsExactly(candidate(2));
        assertThat(runs.findJobRevision(pending)).isNotEqualTo(beforeReturn);
        assertThat(admissions.findScreeningCandidates(List.of(candidate(1)), OWNER, false, Set.of(), Set.of(), false))
            .singleElement().satisfies(row -> assertThat(row.getAdmissionId()).isNull());

        assertThat(admissions.readmit(admission(1), 0, RUN, OWNER, DEPARTMENT, NOW.plusSeconds(3))).isZero();
        assertThat(admissions.readmit(admission(1), 1, RUN, OWNER, DEPARTMENT, NOW.plusSeconds(3))).isEqualTo(1);
        assertThat(admissions.lockAdmission(admission(1)).getVersion()).isEqualTo(2);
        assertThat(admissions.returnToResumes(admission(1), 0, OWNER, NOW.plusSeconds(4))).isZero();
        assertThat(admitted(admissions)).hasSize(2);
        assertThat(admissions.returnToResumes(admission(2), 0, OWNER, NOW.plusSeconds(4))).isEqualTo(1);
        recruitment(jdbc, candidate(1), resume(1));
        session.clearCache();
        assertThat(admitted(admissions)).isEmpty();
        assertThat(admissions.findScreeningCandidates(List.of(candidate(1)), OWNER, false, Set.of(), Set.of(), false)).isEmpty();
        assertThat(runs.findJobs(pending)).isEmpty();
        assertThat(runs.findJobBases(pending)).isEmpty();
        assertThat(runs.findJobResults(pending)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM matching_weighted_result WHERE match_run_id=?", Integer.class, RUN)).isEqualTo(2);
        execute(connection, upgrade());
        session.clearCache();
        assertThat(admissions.findByIdentity(candidate(1), resume(1), JD).getVersion()).isEqualTo(2);
        assertThat(admissions.findByIdentity(candidate(2), resume(2), JD).getReturnedAt()).isNotNull();
      }
    });
  }

  @Test
  void upgradeAddsReturnStateToHistoricalAdmissionAndCanRerunWithoutDataLoss() throws Exception {
    withDatabase(connection -> {
      execute(connection, sqlRoot().resolve("hr_ai_complete_schema.sql"));
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
      seed(jdbc);
      jdbc.update("INSERT INTO matching_screening_admission(id,candidate_id,resume_version_id,jd_version_id,"
          + "default_match_run_id,admitted_by_user_id,owner_department_id,admitted_at,version) VALUES(?,?,?,?,?,?,?,?,7)",
          admission(1), candidate(1), resume(1), JD, RUN, OWNER, DEPARTMENT, "2026-09-10 08:00:00");
      // 在独立临时库重现发布前结构；升级必须保留已有准入和评分。
      jdbc.execute("ALTER TABLE matching_screening_admission DROP COLUMN returned_at");
      execute(connection, upgrade());
      execute(connection, upgrade());
      assertThat(jdbc.queryForObject("SELECT version FROM matching_screening_admission WHERE id=?", Integer.class, admission(1))).isEqualTo(7);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM matching_screening_admission WHERE id=? AND returned_at IS NULL", Integer.class, admission(1))).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM matching_weighted_result", Integer.class)).isEqualTo(2);
    });
  }

  private static List<ScreeningCandidateRow> admitted(ScreeningAdmissionMapper mapper) {
    return mapper.findScreeningCandidates(List.of(), OWNER, false, Set.of(), Set.of(), true);
  }

  private static MatchJobQuery query() {
    var query = new MatchJobQuery();
    query.setActorId(OWNER);
    query.setManagedUserIds(Set.of());
    query.setDepartmentIds(Set.of());
    query.setJdVersionId(JD);
    query.setBasisRunId(RUN);
    query.setLimit(50);
    return query;
  }

  private static void seed(JdbcTemplate jdbc) {
    jdbc.update("INSERT INTO talent_intake_batch(id,source_channel_id,source_recruiter_user_id,default_owner_user_id,"
        + "owner_department_id,jd_version_id,status) VALUES(?,?,?,?,?,?,'COMPLETED')", RUN, RUN, OWNER, OWNER, DEPARTMENT, JD);
    seedRun(jdbc, RUN, 2);
    for (int index = 1; index <= 2; index++) {
      jdbc.update("INSERT INTO matching_candidate_selection_item(selection_snapshot_id,sequence_no,candidate_id,"
          + "resume_version_id,resume_fact_snapshot_id) VALUES(?,?,?,?,?)", RUN, index, candidate(index), resume(index), JD);
      jdbc.update("INSERT INTO matching_weighted_result(id,match_run_id,base_evaluation_id,candidate_id,resume_version_id,"
          + "professional_score,bonus_score,reference_score,must_tier,stable_rank,result_json)"
          + " VALUES(?,?,?,?,?,80,5,85,'PASS',?,JSON_OBJECT())", resume(index), RUN, JD, candidate(index), resume(index), index);
      jdbc.update("INSERT INTO talent_resume_intake(id,batch_id,source_channel_id,source_recruiter_user_id,owner_user_id,"
          + "owner_department_id,original_file_name,media_type,size_bytes,content_sha256,object_key,scan_status,status,"
          + "candidate_id,resume_version_id,score_run_id,score_status)"
          + " VALUES(?,?,?,?,?,?,'synthetic.pdf','application/pdf',10,?,'synthetic','CLEAN','COMPLETED',?,?,?,'SUCCEEDED')",
          resume(index), RUN, RUN, OWNER, OWNER, DEPARTMENT, String.valueOf(index).repeat(64), candidate(index), resume(index), RUN);
    }
  }

  private static void seedRun(JdbcTemplate jdbc, String runId, int count) {
    jdbc.update("INSERT INTO matching_match_run(id,task_id,jd_version_id,jd_fact_snapshot_id,plan_version_id,"
        + "criterion_plan_version_id,weight_profile_version_id,selection_snapshot_id,selected_criterion_ids_json,"
        + "sort_mode,status,total_count,succeeded_count,owner_user_id,owner_department_id,run_purpose)"
        + " VALUES(?,?,?,?,?,?,?,?,JSON_ARRAY(),'REFERENCE_SCORE','SUCCEEDED',?,?,?,?,'COMPANY_DEFAULT')",
        runId, runId, JD, JD, DefaultScoringConstants.PLAN_VERSION_ID, JD,
        DefaultScoringConstants.WEIGHT_VERSION_ID, runId, count, count, OWNER, DEPARTMENT);
  }

  private static void recruitment(JdbcTemplate jdbc, String candidate, String resume) {
    jdbc.update("INSERT INTO recruitment_application(id,candidate_id,jd_id,jd_version_id,resume_version_id,"
        + "workflow_template_version_id,match_plan_version_id,recruitment_type,current_stage,outcome,"
        + "owner_user_id,owner_department_id,active_slot,terminal_at)"
        + " VALUES(?,?,?,?,?,?,?,'INTERNAL','TERMINATED','WITHDRAW',?,?,NULL,'2026-09-10 08:00:05')",
        candidate, candidate, JD, JD, resume, JD, DefaultScoringConstants.PLAN_VERSION_ID, OWNER, DEPARTMENT);
  }

  private static String candidate(int number) { return String.format("C%025d", number); }
  private static String resume(int number) { return String.format("V%025d", number); }
  private static String admission(int number) { return String.format("A%025d", number); }
  private static Path upgrade() {
    return sqlRoot()
        .resolve("../../bootstrap/src/test/resources/db/legacy-upgrades")
        .normalize()
        .resolve("hr_ai_upgrade_20260910_02__screening_return.sql");
  }

  private static Path sqlRoot() {
    Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (root != null && !Files.isRegularFile(root.resolve("docs/sql/hr_ai_complete_schema.sql"))) root = root.getParent();
    if (root == null) throw new IllegalStateException("SQL source root not found");
    return root.resolve("docs/sql");
  }

  private static void execute(Connection connection, Path script) {
    ScriptUtils.executeSqlScript(connection, new EncodedResource(new FileSystemResource(script), StandardCharsets.UTF_8));
  }

  private static void withDatabase(DatabaseWork work) throws Exception {
    String source = required("HR_DB_URL"), user = required("HR_DB_USER"), password = required("HR_DB_PASSWORD");
    URI endpoint = URI.create(source.substring("jdbc:".length()));
    if (!"mysql".equals(endpoint.getScheme()) || endpoint.getRawUserInfo() != null)
      throw new IllegalArgumentException("Expected explicit MySQL test endpoint");
    String schema = "hr_screening_route_it_" + UUID.randomUUID().toString().replace("-", "");
    String isolated = "jdbc:mysql://" + endpoint.getRawAuthority() + "/" + schema
        + (endpoint.getRawQuery() == null ? "" : "?" + endpoint.getRawQuery());
    try (Connection admin = DriverManager.getConnection(source, user, password); Statement ddl = admin.createStatement()) {
      String sourceCatalog = admin.getCatalog();
      ddl.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
      try (Connection connection = DriverManager.getConnection(isolated, user, password)) {
        assertThat(connection.getCatalog()).isEqualTo(schema);
        assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
        work.run(connection);
      } finally {
        if (!schema.matches("hr_screening_route_it_[0-9a-f]{32}")) throw new IllegalStateException("Unexpected fixture schema");
        ddl.execute("DROP DATABASE `" + schema + "`");
        assertThat(admin.getCatalog()).isEqualTo(sourceCatalog);
      }
    }
  }

  private static String required(String name) {
    String value = System.getenv(name);
    assertThat(value).as("必须显式配置 %s", name).isNotBlank();
    return value;
  }

  @FunctionalInterface private interface DatabaseWork { void run(Connection connection) throws Exception; }
}
