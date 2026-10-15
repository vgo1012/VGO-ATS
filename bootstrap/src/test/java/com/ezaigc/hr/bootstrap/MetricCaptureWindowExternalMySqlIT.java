package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.ezaigc.hr.reporting.mapper.MetricMapper;
import com.ezaigc.hr.reporting.service.impl.MetricServiceImpl;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;

/** 在独立临时库验证报表按窗口投影及索引升级，不接触业务库。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
class MetricCaptureWindowExternalMySqlIT {

  private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-09-02T00:00:00Z");
  private static final List<String> ALL_TYPES =
      List.of(
          "RESUME_INGESTED",
          "RESUME_PARSED",
          "APPLICATION_CREATED",
          "HUMAN_SCREENED",
          "SUBMISSION_SENT",
          "INTERVIEW_PASSED",
          "ONBOARDED");

  @Test
  void requestedCaptureUsesHalfOpenWindowAndIsIdempotentOnRealMySql() throws Exception {
    withDatabase(
        connection -> {
          createMetricFixture(connection);
          seedMetricSources(connection);
          JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
          var configuration = new MybatisConfiguration();
          configuration.setEnvironment(
              new Environment(
                  "metric-capture-test",
                  new JdbcTransactionFactory(),
                  new SingleConnectionDataSource(connection, true)));
          configuration.addMapper(MetricMapper.class);
          try (var session =
              new MybatisSqlSessionFactoryBuilder()
                  .build(configuration)
                  .openSession(connection)) {
            var service = new MetricServiceImpl();
            ReflectionTestUtils.setField(service, "baseMapper", session.getMapper(MetricMapper.class));

            assertCapture(jdbc, service, ALL_TYPES, 7);
            service.capture(FROM, TO, ALL_TYPES);
            assertThat(metricCounts(jdbc))
                .allSatisfy((type, count) -> assertThat(count).isEqualTo(1L));

            assertCapture(
                jdbc,
                service,
                List.of(
                    "RESUME_INGESTED",
                    "APPLICATION_CREATED",
                    "SUBMISSION_SENT",
                    "INTERVIEW_PASSED",
                    "ONBOARDED"),
                5);
            assertCapture(
                jdbc,
                service,
                List.of(
                    "HUMAN_SCREENED", "SUBMISSION_SENT", "INTERVIEW_PASSED", "ONBOARDED"),
                4);
            assertCapture(jdbc, service, List.of("RESUME_INGESTED", "RESUME_PARSED"), 2);
          }
        });
  }

  private static void assertCapture(
      JdbcTemplate jdbc, MetricServiceImpl service, List<String> eventTypes, int expectedCount) {
    jdbc.execute("TRUNCATE TABLE reporting_metric_event");
    service.capture(FROM, TO, eventTypes);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reporting_metric_event", Long.class))
        .isEqualTo(expectedCount);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM reporting_metric_event "
                    + "WHERE occurred_at < ? OR occurred_at >= ?",
                Long.class,
                Timestamp.from(FROM),
                Timestamp.from(TO)))
        .isZero();
  }

  private static Map<String, Long> metricCounts(JdbcTemplate jdbc) {
    return jdbc.query(
        "SELECT event_type,COUNT(*) event_count FROM reporting_metric_event GROUP BY event_type",
        result -> {
          var counts = new LinkedHashMap<String, Long>();
          while (result.next()) {
            counts.put(result.getString("event_type"), result.getLong("event_count"));
          }
          return counts;
        });
  }

  private static void createMetricFixture(Connection connection) throws Exception {
    try (Statement statement = connection.createStatement()) {
      for (String ddl :
          List.of(
              "CREATE TABLE talent_resume_intake(id CHAR(26) PRIMARY KEY,owner_user_id CHAR(26),owner_department_id CHAR(26),source_channel_id CHAR(26))",
              "CREATE TABLE talent_resume_version(id CHAR(26) PRIMARY KEY,intake_id CHAR(26),create_id CHAR(26),create_time DATETIME(6) NOT NULL)",
              "CREATE TABLE talent_resume_parse_snapshot(id CHAR(26) PRIMARY KEY,resume_version_id CHAR(26),create_time DATETIME(6) NOT NULL)",
              "CREATE TABLE recruitment_application(id CHAR(26) PRIMARY KEY,create_id CHAR(26),owner_user_id CHAR(26),owner_department_id CHAR(26),jd_id CHAR(26),jd_version_id CHAR(26),create_time DATETIME(6) NOT NULL)",
              "CREATE TABLE recruitment_decision_record(id CHAR(26) PRIMARY KEY,application_id CHAR(26),record_type VARCHAR(16),decided_by_user_id CHAR(26),decided_at DATETIME(6) NOT NULL)",
              "CREATE TABLE submission_client_submission(id CHAR(26) PRIMARY KEY,owner_user_id CHAR(26),owner_department_id CHAR(26),jd_id CHAR(26),jd_version_id CHAR(26),client_id CHAR(26))",
              "CREATE TABLE submission_send_snapshot(id CHAR(26) PRIMARY KEY,submission_id CHAR(26),sent_by_user_id CHAR(26),sent_at DATETIME(6) NOT NULL)",
              "CREATE TABLE interview_interview(id CHAR(26) PRIMARY KEY,interview_type VARCHAR(16),owner_user_id CHAR(26),owner_department_id CHAR(26),jd_id CHAR(26),jd_version_id CHAR(26))",
              "CREATE TABLE interview_feedback(id CHAR(26) PRIMARY KEY,interview_id CHAR(26),status VARCHAR(16),current_version_no INT)",
              "CREATE TABLE interview_feedback_version(id CHAR(26) PRIMARY KEY,feedback_id CHAR(26),version_no INT,create_id CHAR(26),feedback_occurred_at DATETIME(6) NOT NULL,verdict VARCHAR(16))",
              "CREATE TABLE recruitment_onboarding_event(id CHAR(26) PRIMARY KEY,application_id CHAR(26),operated_by_user_id CHAR(26),onboarded_at DATETIME(6) NOT NULL)",
              "CREATE TABLE reporting_metric_event(id CHAR(26) PRIMARY KEY,event_type VARCHAR(40),source_type VARCHAR(40),source_id CHAR(26),actor_user_id CHAR(26),owner_user_id CHAR(26),department_id CHAR(26),source_channel_id CHAR(26),jd_id CHAR(26),jd_version_id CHAR(26),client_id CHAR(26),value_decimal DECIMAL(18,4),occurred_at DATETIME(6),captured_at DATETIME(6),UNIQUE KEY uk_metric_source(event_type,source_type,source_id))")) {
        statement.execute(ddl);
      }
    }
  }

  private static void seedMetricSources(Connection connection) throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    jdbc.update("INSERT INTO talent_resume_intake VALUES('intake','owner','department','channel')");
    insertResumeVersion(jdbc, "resume-before", FROM.minusSeconds(1));
    insertResumeVersion(jdbc, "resume-from", FROM);
    insertResumeVersion(jdbc, "resume-to", TO);
    jdbc.update(
        "INSERT INTO talent_resume_parse_snapshot VALUES('parse','resume-from',?)",
        Timestamp.from(FROM.plusSeconds(1)));
    jdbc.update(
        "INSERT INTO recruitment_application VALUES('application','actor','owner','department','jd','jd-version',?)",
        Timestamp.from(FROM.plusSeconds(2)));
    jdbc.update(
        "INSERT INTO recruitment_decision_record VALUES('decision','application','DECISION','actor',?)",
        Timestamp.from(FROM.plusSeconds(3)));
    jdbc.update("INSERT INTO submission_client_submission VALUES('submission','owner','department','jd','jd-version','client')");
    jdbc.update(
        "INSERT INTO submission_send_snapshot VALUES('send','submission','actor',?)",
        Timestamp.from(FROM.plusSeconds(4)));
    jdbc.update("INSERT INTO interview_interview VALUES('interview','CLIENT','owner','department','jd','jd-version')");
    jdbc.update("INSERT INTO interview_feedback VALUES('feedback','interview','SUBMITTED',1)");
    jdbc.update(
        "INSERT INTO interview_feedback_version VALUES('feedback-version','feedback',1,'actor',?,'PASS')",
        Timestamp.from(FROM.plusSeconds(5)));
    jdbc.update(
        "INSERT INTO recruitment_onboarding_event VALUES('onboarding','application','actor',?)",
        Timestamp.from(FROM.plusSeconds(6)));
  }

  private static void insertResumeVersion(JdbcTemplate jdbc, String id, Instant createTime) {
    jdbc.update(
        "INSERT INTO talent_resume_version VALUES(?,'intake','actor',?)",
        id,
        Timestamp.from(createTime));
  }

  private static void withDatabase(DatabaseWork work) throws Exception {
    String sourceUrl = requiredEnvironment("HR_DB_URL");
    String schema = "hr_metric_it_" + UUID.randomUUID().toString().replace("-", "");
    URI endpoint = URI.create(sourceUrl.substring("jdbc:".length()));
    if (!"mysql".equals(endpoint.getScheme()) || endpoint.getRawUserInfo() != null) {
      throw new IllegalArgumentException("External tests require a plain MySQL JDBC endpoint");
    }
    String isolatedUrl =
        "jdbc:mysql://"
            + endpoint.getRawAuthority()
            + "/"
            + schema
            + (endpoint.getRawQuery() == null ? "" : "?" + endpoint.getRawQuery());
    String user = requiredEnvironment("HR_DB_USER");
    String password = requiredEnvironment("HR_DB_PASSWORD");
    try (Connection administration = DriverManager.getConnection(sourceUrl, user, password);
        Statement databaseStatement = administration.createStatement()) {
      databaseStatement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
      try (Connection isolated = DriverManager.getConnection(isolatedUrl, user, password)) {
        assertThat(isolated.getCatalog()).isEqualTo(schema);
        work.run(isolated);
      } finally {
        databaseStatement.execute("DROP DATABASE `" + schema + "`");
      }
    }
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    assertThat(value).as("必须显式配置测试环境变量 %s", name).isNotBlank();
    return value;
  }

  @FunctionalInterface
  private interface DatabaseWork {
    void run(Connection connection) throws Exception;
  }
}
