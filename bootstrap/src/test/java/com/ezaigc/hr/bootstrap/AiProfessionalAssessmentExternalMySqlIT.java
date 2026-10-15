package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.ezaigc.hr.intelligence.matching.domain.entity.AiProfessionalAssessment;
import com.ezaigc.hr.intelligence.matching.enums.AiProfessionalAssessmentStatus;
import com.ezaigc.hr.intelligence.matching.mapper.AiProfessionalAssessmentMapper;
import com.ezaigc.hr.intelligence.matching.service.impl.AiProfessionalAssessmentPersistenceServiceImpl;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** 在显式外部 MySQL 开关下验证 AI 评分缓存升级和真实领取 fencing。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
class AiProfessionalAssessmentExternalMySqlIT {

  @Test
  void upgradeRerunAndRealMapperKeepOneImmutableAssessmentAcrossCompetingClaims()
      throws Exception {
    withDatabase(
        (connection, isolatedUrl) -> {
          var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
          createPrerequisites(jdbc);
          script(connection);
          script(connection);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()"
                          + " AND TABLE_NAME='matching_match_run' AND COLUMN_NAME='scoring_version'",
                      String.class))
              .isEqualTo("legacy-deterministic");
          jdbc.update("INSERT INTO matching_match_run(id) VALUES ('legacy-run')");
          assertThat(
                  jdbc.queryForObject(
                      "SELECT scoring_version FROM matching_match_run WHERE id='legacy-run'",
                      String.class))
              .isEqualTo("legacy-deterministic");
          seedFacts(jdbc);

          var source =
              new DriverManagerDataSource(
                  isolatedUrl, required("HR_DB_USER"), required("HR_DB_PASSWORD"));
          var configuration = new MybatisConfiguration();
          configuration.setMapUnderscoreToCamelCase(true);
          configuration.setEnvironment(
              new Environment("ai-assessment-it", new JdbcTransactionFactory(), source));
          configuration.addMapper(AiProfessionalAssessmentMapper.class);
          var factory = new MybatisSqlSessionFactoryBuilder().build(configuration);

          Instant claimedAt = Instant.parse("2026-09-20T01:00:00.123456Z");
          var concurrentFirst = assessment("concurrent-first", "concurrent-token-first", claimedAt);
          var concurrentSecond =
              assessment("concurrent-second", "concurrent-token-second", claimedAt);
          concurrentFirst.setCacheKey("b".repeat(64));
          concurrentSecond.setCacheKey("b".repeat(64));
          var ready = new CountDownLatch(2);
          var start = new CountDownLatch(1);
          try (var executor = Executors.newFixedThreadPool(2)) {
            var firstClaim =
                executor.submit(
                    () -> {
                      claimAfterBarrier(factory, concurrentFirst, claimedAt, ready, start);
                      return null;
                    });
            var secondClaim =
                executor.submit(
                    () -> {
                      claimAfterBarrier(factory, concurrentSecond, claimedAt, ready, start);
                      return null;
                    });
            // 连接预热单独有界等待；下面的5秒仅衡量真正并发领取，不包含远程握手。
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            firstClaim.get(5, TimeUnit.SECONDS);
            secondClaim.get(5, TimeUnit.SECONDS);
          }
          try (var verification = factory.openSession(true)) {
            var concurrent =
                verification
                    .getMapper(AiProfessionalAssessmentMapper.class)
                    .findByCacheKey(concurrentFirst.getCacheKey());
            assertThat(concurrent.getClaimToken())
                .isIn(concurrentFirst.getClaimToken(), concurrentSecond.getClaimToken());
            assertThat(
                    jdbc.queryForObject(
                        "SELECT COUNT(*) FROM matching_ai_professional_assessment WHERE cache_key=REPEAT('b',64)",
                        Integer.class))
                .isEqualTo(1);
          }

          AiProfessionalAssessment first = assessment("assessment-first", "token-first", claimedAt);
          AiProfessionalAssessment second =
              assessment("assessment-second", "token-second", claimedAt.plusSeconds(60));
          second.setEvaluatedOn(LocalDate.of(2026, 9, 21));
          try (var firstSession = factory.openSession(true);
              var secondSession = factory.openSession(true)) {
            var firstMapper = firstSession.getMapper(AiProfessionalAssessmentMapper.class);
            var secondMapper = secondSession.getMapper(AiProfessionalAssessmentMapper.class);

            assertThat(firstMapper.findByCacheKey(first.getCacheKey())).isNull();
            var persistence = new AiProfessionalAssessmentPersistenceServiceImpl();
            ReflectionTestUtils.setField(persistence, "baseMapper", secondMapper);
            assertThat(persistence.claim(first, claimedAt)).isTrue();
            assertThat(firstMapper.findByCacheKey(first.getCacheKey()).getLeaseUntil())
                .isEqualTo(first.getLeaseUntil().truncatedTo(ChronoUnit.MICROS));
            claim(secondMapper, second, claimedAt.plusSeconds(60));
            var busy = secondMapper.findByCacheKey(first.getCacheKey());
            assertThat(busy.getId()).isEqualTo(first.getId());
            assertThat(busy.getClaimToken()).isEqualTo(first.getClaimToken());
            assertThat(busy.getEvaluatedOn()).isEqualTo(LocalDate.of(2026, 9, 20));

            claim(secondMapper, second, claimedAt.plusSeconds(301));
            var takenOver = firstMapper.findByCacheKey(first.getCacheKey());
            assertThat(takenOver.getId()).isEqualTo(first.getId());
            assertThat(takenOver.getClaimToken()).isEqualTo(second.getClaimToken());
            assertThat(takenOver.getEvaluatedOn()).isEqualTo(LocalDate.of(2026, 9, 21));
            assertThat(
                    secondMapper.complete(
                        first.getId(),
                        first.getClaimToken(),
                        "{\"score\":10}",
                        "provider-old",
                        "model-old",
                        1,
                        1,
                        1,
                        claimedAt.plusSeconds(302),
                        "system",
                        AiProfessionalAssessmentStatus.SUCCEEDED.getCode(),
                        AiProfessionalAssessmentStatus.PROCESSING.getCode()))
                .isZero();
            assertThat(firstMapper.findByCacheKey(first.getCacheKey()).getStatus())
                .isEqualTo(AiProfessionalAssessmentStatus.PROCESSING.getCode());
            assertThat(
                    persistence.complete(
                        first.getId(),
                        second.getClaimToken(),
                        "{\"score\":88}",
                        "provider",
                        "model",
                        120,
                        40,
                        900,
                        claimedAt.plusSeconds(302)))
                .isTrue();
            var completedFresh = firstMapper.findByCacheKey(first.getCacheKey());
            assertThat(completedFresh.getStatus())
                .isEqualTo(AiProfessionalAssessmentStatus.SUCCEEDED.getCode());
            assertThat(completedFresh.getResultJson()).contains("88");

            var third = assessment("assessment-third", "token-third", claimedAt.plusSeconds(900));
            claim(firstMapper, third, claimedAt.plusSeconds(900));
            var succeeded = secondMapper.findByCacheKey(first.getCacheKey());
            assertThat(succeeded.getId()).isEqualTo(first.getId());
            assertThat(succeeded.getStatus()).isEqualTo("SUCCEEDED");
            assertThat(succeeded.getClaimToken()).isNull();
            assertThat(succeeded.getResultJson()).contains("88");
          }

          script(connection);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT COUNT(*) FROM matching_ai_professional_assessment",
                      Integer.class))
              .isEqualTo(2);
        });
  }

  @Test
  void databaseRejectsInvalidStatusUsageAndTerminalShape() throws Exception {
    withDatabase(
        (connection, ignored) -> {
          var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
          createPrerequisites(jdbc);
          script(connection);
          seedFacts(jdbc);
          String columns =
              "(id,cache_key,jd_fact_snapshot_id,resume_fact_snapshot_id,prompt_version,"
                  + "schema_version,evaluated_on,status,claim_token,lease_until,result_json,"
                  + "input_tokens,output_tokens,duration_millis,error_code)";
          assertThatThrownBy(
                  () ->
                      jdbc.update(
                          "INSERT INTO matching_ai_professional_assessment "
                              + columns
                              + " VALUES ('bad-status',REPEAT('a',64),'jd-fact','resume-fact','p','s',"
                              + "'2026-09-20','UNKNOWN',NULL,NULL,NULL,0,0,0,NULL)"))
              .satisfies(failure -> checkViolation(failure,
                  "ck_matching_ai_professional_status", "ck_matching_ai_professional_state"));
          assertThatThrownBy(
                  () ->
                      jdbc.update(
                          "INSERT INTO matching_ai_professional_assessment "
                              + columns
                              + " VALUES ('bad-usage',REPEAT('b',64),'jd-fact','resume-fact','p','s',"
                              + "'2026-09-20','PROCESSING','token','2026-09-21',NULL,-1,0,0,NULL)"))
              .satisfies(failure -> checkViolation(failure, "ck_matching_ai_professional_usage"));
          assertThatThrownBy(
                  () ->
                      jdbc.update(
                          "INSERT INTO matching_ai_professional_assessment "
                              + columns
                              + " VALUES ('bad-success',REPEAT('c',64),'jd-fact','resume-fact','p','s',"
                              + "'2026-09-20','SUCCEEDED',NULL,NULL,NULL,0,0,0,NULL)"))
              .satisfies(failure -> checkViolation(failure, "ck_matching_ai_professional_state"));
          assertThatThrownBy(
                  () ->
                      jdbc.update(
                          "INSERT INTO matching_ai_professional_assessment "
                              + columns
                              + " VALUES ('bad-provider',REPEAT('d',64),'jd-fact','resume-fact','p','s',"
                              + "'2026-09-20','SUCCEEDED',NULL,NULL,JSON_OBJECT('score',88),0,0,0,NULL)"))
              .satisfies(failure -> checkViolation(failure, "ck_matching_ai_professional_state"));
        });
  }

  private static void checkViolation(Throwable failure, String... constraintNames) {
    assertThat(failure).isInstanceOfSatisfying(DataAccessException.class, dataFailure ->
        assertThat(dataFailure.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, sql -> {
          assertThat(sql.getErrorCode()).isEqualTo(3819);
          assertThat(sql.getMessage()).containsAnyOf(constraintNames);
        }));
  }

  private static AiProfessionalAssessment assessment(String id, String token, Instant now) {
    var assessment = new AiProfessionalAssessment();
    assessment.setId(id);
    assessment.setCacheKey("a".repeat(64));
    assessment.setJdFactSnapshotId("jd-fact");
    assessment.setResumeFactSnapshotId("resume-fact");
    assessment.setPromptVersion("prompt-v1");
    assessment.setSchemaVersion("schema-v1");
    assessment.setProvider("provider");
    assessment.setModel("model");
    assessment.setEvaluatedOn(LocalDate.of(2026, 9, 20));
    assessment.setStatus("PROCESSING");
    assessment.setClaimToken(token);
    assessment.setLeaseUntil(now.plusSeconds(300));
    return assessment;
  }

  private static void claimAfterBarrier(
      SqlSessionFactory factory,
      AiProfessionalAssessment assessment,
      Instant now,
      CountDownLatch ready,
      CountDownLatch start)
      throws Exception {
    try (var session = factory.openSession(true)) {
      assertThat(session.getConnection().isValid(10)).isTrue();
      ready.countDown();
      assertThat(start.await(30, TimeUnit.SECONDS)).isTrue();
      session
          .getMapper(AiProfessionalAssessmentMapper.class)
          .claim(
              assessment,
              now,
              "system",
              AiProfessionalAssessmentStatus.PROCESSING.getCode(),
              AiProfessionalAssessmentStatus.FAILED.getCode());
    }
  }

  private static void claim(
      AiProfessionalAssessmentMapper mapper,
      AiProfessionalAssessment assessment,
      Instant now) {
    mapper.claim(
        assessment,
        now,
        "system",
        AiProfessionalAssessmentStatus.PROCESSING.getCode(),
        AiProfessionalAssessmentStatus.FAILED.getCode());
  }

  private static void createPrerequisites(JdbcTemplate jdbc) {
    jdbc.execute(
        "CREATE TABLE demand_jd_fact_snapshot(id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY) ENGINE=InnoDB");
    jdbc.execute(
        "CREATE TABLE talent_resume_fact_snapshot(id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY) ENGINE=InnoDB");
    jdbc.execute(
        "CREATE TABLE matching_match_run(id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,"
            + " run_purpose VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CUSTOM') ENGINE=InnoDB");
  }

  private static void seedFacts(JdbcTemplate jdbc) {
    jdbc.update("INSERT INTO demand_jd_fact_snapshot(id) VALUES ('jd-fact')");
    jdbc.update("INSERT INTO talent_resume_fact_snapshot(id) VALUES ('resume-fact')");
  }

  private static void withDatabase(DatabaseWork work) throws Exception {
    String sourceUrl = required("HR_DB_URL");
    URI endpoint = URI.create(sourceUrl.substring("jdbc:".length()));
    if (!"mysql".equals(endpoint.getScheme()) || endpoint.getRawUserInfo() != null) {
      throw new IllegalArgumentException("External tests require a plain MySQL JDBC endpoint");
    }
    String schema = "hr_ai_assessment_it_" + UUID.randomUUID().toString().replace("-", "");
    String isolatedUrl =
        "jdbc:mysql://"
            + endpoint.getRawAuthority()
            + "/"
            + schema
            + (endpoint.getRawQuery() == null ? "" : "?" + endpoint.getRawQuery());
    try (Connection administration =
            DriverManager.getConnection(
                sourceUrl, required("HR_DB_USER"), required("HR_DB_PASSWORD"));
        Statement command = administration.createStatement()) {
      command.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
      try (Connection isolated =
          DriverManager.getConnection(
              isolatedUrl, required("HR_DB_USER"), required("HR_DB_PASSWORD"))) {
        assertThat(isolated.getCatalog()).isEqualTo(schema);
        assertThat(isolated.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
        assertThat(isolated.getMetaData().getDatabaseMajorVersion()).isGreaterThanOrEqualTo(8);
        work.run(isolated, isolatedUrl);
      } finally {
        command.execute("DROP DATABASE `" + schema + "`");
      }
    }
  }

  private static void script(Connection connection) {
    ScriptUtils.executeSqlScript(
        connection,
        new EncodedResource(
            new FileSystemResource(
                repositoryRoot()
                    .resolve("docs/sql/hr_ai_upgrade_20260920_01__ai_professional_assessment.sql")),
            StandardCharsets.UTF_8));
  }

  private static Path repositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null
        && !Files.isRegularFile(current.resolve("docs/sql/hr_ai_complete_schema.sql"))) {
      current = current.getParent();
    }
    if (current == null) {
      throw new IllegalStateException("无法定位仓库根目录");
    }
    return current;
  }

  private static String required(String name) {
    String value = System.getenv(name);
    assertThat(value).as("必须显式配置测试环境变量 %s", name).isNotBlank();
    return value;
  }

  @FunctionalInterface
  private interface DatabaseWork {
    void run(Connection connection, String isolatedUrl) throws Exception;
  }
}
