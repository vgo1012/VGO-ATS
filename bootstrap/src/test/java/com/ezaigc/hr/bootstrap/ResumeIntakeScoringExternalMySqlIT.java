package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.mapper.TalentIntakeMapper;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
class ResumeIntakeScoringExternalMySqlIT {
  @Test
  void detectsHistoricalContentDuplicatesWithoutClaimingCandidateMerge() throws Exception {
    withDatabase(database -> {
      JdbcTemplate jdbc = database.jdbc();
      createPrerequisites(jdbc);
      jdbc.update("INSERT INTO talent_intake_batch(id) VALUES ('batch')");
      jdbc.update("INSERT INTO talent_resume_intake_processing(id,status,attempt_count)"
          + " VALUES ('processing','SUCCEEDED',1),('other-processing','SUCCEEDED',1)");
      jdbc.update("INSERT INTO talent_candidate(id,full_name) VALUES ('candidate','合成候选人')");
      jdbc.update("""
          INSERT INTO talent_resume_intake
            (id,batch_id,original_file_name,content_sha256,processing_id,status,
             candidate_id,resume_version_id,create_time)
          VALUES ('legacy','batch','resume.pdf',?,NULL,'COMPLETED',
                  'candidate','legacy-version','2026-09-07 00:00:00'),
                 ('recent','batch','resume.pdf',?,'processing','READY_FOR_CONFIRMATION',
                  NULL,NULL,'2026-09-09 00:00:00'),
                 ('same-name','batch','resume.pdf',?,'other-processing','READY_FOR_CONFIRMATION',
                  NULL,NULL,'2026-09-09 00:00:00')
          """, "a".repeat(64), "a".repeat(64), "b".repeat(64));

      try (var session = mapperSessions(database.url()).openSession(true)) {
        TalentIntakeMapper mapper = session.getMapper(TalentIntakeMapper.class);
        assertThat(mapper.findIntake("legacy").isDuplicateContent()).isTrue();
        ResumeIntake recent = mapper.findIntake("recent");
        assertThat(recent.isDuplicateContent()).isTrue();
        assertThat(recent.getCandidateId()).isNull();
        assertThat(recent.getResumeVersionId()).isNull();
        assertThat(recent.getStatus()).isEqualTo("READY_FOR_CONFIRMATION");
        assertThat(mapper.findIntake("same-name").isDuplicateContent()).isFalse();

        List<ResumeIntake> rows = mapper.findIntakes(null, null, null, null, null, null, null, null,
            "actor", true, Set.of(), Set.of(), null, null, 100);
        assertThat(rows).hasSize(3);
        assertThat(rows).filteredOn(ResumeIntake::isDuplicateContent)
            .extracting(ResumeIntake::getId).containsExactlyInAnyOrder("legacy", "recent");
        assertThat(rows).filteredOn(row -> row.getId().equals("recent")).singleElement()
            .satisfies(row -> {
              assertThat(row.getCandidateId()).isNull();
              assertThat(row.getResumeVersionId()).isNull();
              assertThat(row.getStatus()).isEqualTo("READY_FOR_CONFIRMATION");
            });
        assertThat(mapper.findIntakes(null, null, null, true, null, null, null, null,
            "actor", true, Set.of(), Set.of(), null, null, 100))
            .extracting(ResumeIntake::getId).containsExactlyInAnyOrder("legacy", "recent");
      }
    });
  }

  @Test
  void selectsCompletedLinkedIntakesWithCurrentFactsForInitialOrExistingScores() throws Exception {
    withDatabase(database -> {
      JdbcTemplate jdbc = database.jdbc();
      createPrerequisites(jdbc);
      jdbc.update("INSERT INTO talent_intake_batch(id,jd_version_id) VALUES (?,?),(?,?)",
          "with-jd", "jd-version", "without-jd", null);
      jdbc.update("INSERT INTO talent_resume_intake_processing(id,status,attempt_count)"
          + " VALUES ('processing','SUCCEEDED',1)");
      List<String> pending = List.of(
          "NOT_APPLICABLE", "WAITING_JD", "WAITING_CONFIG", "QUEUED", "RUNNING");
      for (int index = 0; index < pending.size(); index++) {
        insertIntake(jdbc, "eligible-" + index, "with-jd", "COMPLETED", pending.get(index), "Y");
      }
      insertIntake(jdbc, "historical-only", "with-jd", "COMPLETED", "QUEUED", null);
      insertIntake(jdbc, "without-score-run", "with-jd", "COMPLETED", "NOT_APPLICABLE", "Y");
      jdbc.update("UPDATE talent_resume_intake SET score_run_id=NULL WHERE id='without-score-run'");
      insertIntake(jdbc, "unfinished", "with-jd", "CONFIRMED", "QUEUED", "Y");
      insertIntake(jdbc, "missing-jd", "without-jd", "COMPLETED", "QUEUED", "Y");
      insertIntake(jdbc, "missing-version", "with-jd", "COMPLETED", "QUEUED", "Y");
      jdbc.update("UPDATE talent_resume_intake SET resume_version_id=NULL WHERE id='missing-version'");
      for (String status : List.of("SUCCEEDED", "FAILED_FINAL", "FAILED_RETRYABLE")) {
        insertIntake(jdbc, status, "with-jd", "COMPLETED", status, "Y");
      }
      // 同一版本保留历史快照时，当前快照仍只返回一条已有评分的待同步收件。
      jdbc.update("INSERT INTO talent_resume_fact_snapshot(id,resume_version_id,current_marker)"
          + " VALUES ('older-fact','eligible-0',NULL)");

      try (var session = mapperSessions(database.url()).openSession(true)) {
        TalentIntakeMapper mapper = session.getMapper(TalentIntakeMapper.class);
        assertThat(mapper.findScorableIntakes(100)).extracting(ResumeIntake::getId)
            .containsExactly("eligible-0", "eligible-1", "eligible-2", "eligible-3", "eligible-4",
                "without-score-run");
        assertThat(mapper.findScorableIntakes(2)).extracting(ResumeIntake::getId)
            .containsExactly("eligible-0", "eligible-1");
      }
    });
  }

  private static void insertIntake(JdbcTemplate jdbc, String id, String batchId,
      String status, String scoreStatus, String currentMarker) {
    jdbc.update("INSERT INTO talent_resume_intake"
        + " (id,batch_id,processing_id,resume_version_id,score_run_id,status,score_status,update_time)"
        + " VALUES (?,?,'processing',?,?,?,?,'2026-09-09 00:00:00')",
        id, batchId, id, "score-" + id, status, scoreStatus);
    jdbc.update("INSERT INTO talent_resume_fact_snapshot(id,resume_version_id,current_marker)"
        + " VALUES (?,?,?)", id, id, currentMarker);
  }

  private static void createPrerequisites(JdbcTemplate jdbc) {
    jdbc.execute("CREATE TABLE talent_intake_batch (id CHAR(26) PRIMARY KEY,"
        + " default_owner_user_id CHAR(26),jd_version_id CHAR(26)) ENGINE=InnoDB");
    jdbc.execute("CREATE TABLE talent_resume_intake_processing (id CHAR(26) PRIMARY KEY,"
        + " status VARCHAR(32),attempt_count INT,parsed_facts_json JSON) ENGINE=InnoDB");
    jdbc.execute("CREATE TABLE talent_candidate (id CHAR(26) PRIMARY KEY,"
        + " full_name VARCHAR(128),primary_email VARCHAR(255),primary_phone VARCHAR(32)) ENGINE=InnoDB");
    jdbc.execute("""
        CREATE TABLE talent_resume_intake (
          id CHAR(26) PRIMARY KEY,batch_id CHAR(26),source_channel_id CHAR(26),
          source_recruiter_user_id CHAR(26),owner_user_id CHAR(26),owner_department_id CHAR(26),
          handled_by_user_id CHAR(26),original_file_name VARCHAR(255),media_type VARCHAR(128),
          size_bytes BIGINT NOT NULL DEFAULT 0,content_sha256 CHAR(64),object_key VARCHAR(500),
          processing_id CHAR(26),scan_status VARCHAR(32),status VARCHAR(32),failure_code VARCHAR(128),
          candidate_id CHAR(26),resume_version_id CHAR(26),score_run_id CHAR(26),score_status VARCHAR(32),
          version BIGINT NOT NULL DEFAULT 0,ext JSON,remark VARCHAR(500),create_id CHAR(26),
          create_name VARCHAR(128),create_time DATETIME(3),update_id CHAR(26),
          update_name VARCHAR(128),update_time DATETIME(3)
        ) ENGINE=InnoDB
        """);
    // 与部署基线一致：当前值仅为 Y，历史快照为 NULL。
    jdbc.execute("CREATE TABLE talent_resume_fact_snapshot (id CHAR(26) PRIMARY KEY,"
        + " resume_version_id CHAR(26) NOT NULL,current_marker CHAR(1) NULL,"
        + " CHECK (current_marker IS NULL OR current_marker='Y'),"
        + " UNIQUE KEY uk_current_fact (resume_version_id,current_marker)) ENGINE=InnoDB");
  }

  private static SqlSessionFactory mapperSessions(String url) {
    var dataSource = new DriverManagerDataSource(
        url, requiredEnvironment("HR_DB_USER"), requiredEnvironment("HR_DB_PASSWORD"));
    var configuration = new MybatisConfiguration();
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.setEnvironment(
        new Environment("resume-intake-scoring-it", new JdbcTransactionFactory(), dataSource));
    configuration.addMapper(TalentIntakeMapper.class);
    return new MybatisSqlSessionFactoryBuilder().build(configuration);
  }

  private static void withDatabase(DatabaseWork work) throws Exception {
    String sourceUrl = requiredEnvironment("HR_DB_URL");
    String schema = "hr_intake_score_it_" + UUID.randomUUID().toString().replace("-", "");
    URI endpoint = URI.create(sourceUrl.substring("jdbc:".length()));
    if (!"mysql".equals(endpoint.getScheme()) || endpoint.getRawUserInfo() != null) {
      throw new IllegalArgumentException("External tests require a plain MySQL JDBC endpoint");
    }
    String url = "jdbc:mysql://" + endpoint.getRawAuthority() + "/" + schema
        + (endpoint.getRawQuery() == null ? "" : "?" + endpoint.getRawQuery());
    String user = requiredEnvironment("HR_DB_USER");
    String password = requiredEnvironment("HR_DB_PASSWORD");
    try (Connection administration = DriverManager.getConnection(sourceUrl, user, password);
        Statement statement = administration.createStatement()) {
      statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
      try (Connection isolated = DriverManager.getConnection(url, user, password)) {
        assertThat(isolated.getCatalog()).isEqualTo(schema);
        work.run(new TestDatabase(isolated, url));
      } finally {
        statement.execute("DROP DATABASE `" + schema + "`");
      }
    }
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " must be configured");
    }
    return value;
  }

  private record TestDatabase(Connection connection, String url) {
    JdbcTemplate jdbc() {
      return new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }
  }

  @FunctionalInterface
  private interface DatabaseWork {
    void run(TestDatabase database) throws Exception;
  }
}
