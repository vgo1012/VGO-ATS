package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.ezaigc.hr.reporting.application.manager.MetricCaptureApplicationManager;
import com.ezaigc.hr.reporting.enums.MetricCaptureLane;
import com.ezaigc.hr.reporting.enums.MetricEventType;
import com.ezaigc.hr.reporting.mapper.MetricCaptureCheckpointMapper;
import com.ezaigc.hr.reporting.mapper.MetricMapper;
import com.ezaigc.hr.reporting.service.impl.MetricCaptureServiceImpl;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 隔离库验证指标增量、补偿、事务失败、多实例与升级重跑。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
class MetricIncrementalExternalMySqlIT {
  private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-09-02T00:00:00Z");
  private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");

  @Test
  void baselineCreatesCheckpointAndUpgradePreservesIt() throws Exception {
    withDatabase(connection -> {
      Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
      while (root != null && !Files.exists(root.resolve("docs/sql/hr_ai_complete_schema.sql"))) root = root.getParent();
      if (root == null) throw new IllegalStateException("repository root missing");
      ScriptUtils.executeSqlScript(connection, new EncodedResource(new FileSystemResource(
          root.resolve("docs/sql/hr_ai_complete_schema.sql")), StandardCharsets.UTF_8));
      connection.setAutoCommit(false);
      try (var harness = harness(connection)) {
        assertThat(harness.service().status().getDataUpdatedAt()).isNull();
        for (var source : MetricEventType.values()) {
          for (var lane : MetricCaptureLane.values()) {
            String name = source.getCode() + ':' + lane.getCode();
            assertThat(harness.mapper().insertIfAbsent(name, source.getCode(), lane.getCode(),
                "00000000000000000000000000", "test", NOW)).isEqualTo(1);
            var checkpoint = harness.mapper().lock(name);
            assertThat(checkpoint.getSourceCode()).isEqualTo(source.getCode());
            assertThat(checkpoint.getLane()).isEqualTo(lane.getCode());
            assertThat(checkpoint.getVersion()).isZero();
            assertThat(checkpoint.getLastCompletedAt()).isNull();
            checkpoint.setLastSourceId("saved-" + source.ordinal());
            checkpoint.setLastCompletedAt(NOW.minusSeconds(source.ordinal()));
            assertThat(harness.mapper().advance(checkpoint, "test", NOW)).isEqualTo(1);
          }
        }
        harness.session().commit(true);
        Instant expectedWatermark = NOW.minusSeconds(MetricEventType.values().length - 1);
        assertThat(harness.service().status().getDataUpdatedAt()).isEqualTo(expectedWatermark);
        assertThat(harness.service().status().getReconciliationUpdatedAt()).isEqualTo(expectedWatermark);
        var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        var before = jdbc.queryForList("SELECT checkpoint_name,source_code,lane,last_source_id,cycle_upper_id,"
            + "cycle_started_at,last_completed_at,version,create_id,create_time,update_id,update_time"
            + " FROM reporting_metric_capture_checkpoint ORDER BY checkpoint_name");
        assertThat(before).hasSize(14);
        migrate(connection);
        migrate(connection);
        assertThat(jdbc.queryForList("SELECT checkpoint_name,source_code,lane,last_source_id,cycle_upper_id,"
            + "cycle_started_at,last_completed_at,version,create_id,create_time,update_id,update_time"
            + " FROM reporting_metric_capture_checkpoint ORDER BY checkpoint_name")).isEqualTo(before);
        harness.session().clearCache();
        assertThat(harness.mapper().lock("RESUME_INGESTED:INCREMENTAL").getLastSourceId()).isEqualTo("saved-0");
        assertThat(harness.service().status().getDataUpdatedAt()).isEqualTo(expectedWatermark);
        harness.session().commit(true);
      }
    });
  }

  @Test
  void boundedCaptureResumesAndReconciliationRecoversLateIdsAndChangedSourceState() throws Exception {
    withDatabase(connection -> {
      createMetricFixture(connection);
      seedMetricSources(connection);
      migrate(connection);
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.update("UPDATE interview_feedback SET status='DRAFT'");
      connection.setAutoCommit(false);
      try (var harness = harness(connection)) {
        assertThat(harness.service().status().getDataUpdatedAt()).isNull();
        for (var source : MetricEventType.values()) complete(harness, source, MetricCaptureLane.INCREMENTAL, 1);
        assertThat(harness.service().status().getDataUpdatedAt()).isEqualTo(NOW);
        assertThat(metricCounts(jdbc)).containsEntry("RESUME_INGESTED", 3L).doesNotContainKey("INTERVIEW_PASSED");
        jdbc.update("INSERT INTO talent_resume_version VALUES('a-late','intake','actor',?)", Timestamp.from(FROM));
        jdbc.update("UPDATE interview_feedback SET status='SUBMITTED'");
        connection.commit();
        for (var source : MetricEventType.values()) complete(harness, source, MetricCaptureLane.INCREMENTAL, 1);
        assertThat(metricCounts(jdbc)).containsEntry("RESUME_INGESTED", 3L).doesNotContainKey("INTERVIEW_PASSED");
        for (var source : MetricEventType.values()) complete(harness, source, MetricCaptureLane.RECONCILIATION, 1);
        assertThat(metricCounts(jdbc)).containsEntry("RESUME_INGESTED", 4L).containsEntry("INTERVIEW_PASSED", 1L);
        assertThat(harness.service().status().getReconciliationUpdatedAt()).isEqualTo(NOW);
        var before = metricCounts(jdbc);
        for (var source : MetricEventType.values()) complete(harness, source, MetricCaptureLane.RECONCILIATION, 2);
        assertThat(metricCounts(jdbc)).isEqualTo(before);
        // 背景采集完整历史，但读取仍保留半开日期窗口和权限范围。
        var metrics = harness.session().getMapper(MetricMapper.class);
        assertThat(metrics.aggregate(FROM, TO, null, null, null, false, "owner", List.of(), List.of()))
            .hasSize(7).allSatisfy(row -> assertThat(row.getEventCount()).isEqualTo(
                row.getEventType().equals("RESUME_INGESTED") ? 2 : 1));
        assertThat(metrics.aggregate(FROM, TO, null, null, null, false, "outsider", List.of(), List.of())).isEmpty();
        assertThat(metrics.parseCoverage(FROM, TO, null, null, null, true, "owner", List.of(), List.of()))
            .satisfies(row -> {
              assertThat(row.getResumeVersionCount()).isEqualTo(2);
              assertThat(row.getParsedResumeVersionCount()).isEqualTo(1);
            });
      }
    });
  }

  @Test
  void captureDoesNotLockInterviewSourceWhileBusinessHoldsItsRow() throws Exception {
    withDatabase(business -> {
      createMetricFixture(business);
      seedMetricSources(business);
      migrate(business);
      business.setAutoCommit(false);
      try (var lock = business.prepareStatement("SELECT id FROM interview_interview WHERE id='interview' FOR UPDATE");
           var locked = lock.executeQuery()) {
        assertThat(locked.next()).isTrue();
        try (var capture = DriverManager.getConnection(business.getMetaData().getURL(),
            requiredEnvironment("HR_DB_USER"), requiredEnvironment("HR_DB_PASSWORD"))) {
          try (var timeout = capture.createStatement()) {
            timeout.execute("SET SESSION innodb_lock_wait_timeout=1");
          }
          capture.setAutoCommit(false);
          try (var harness = harness(capture)) {
            assertThat(harness.service().captureNext(MetricEventType.INTERVIEW_PASSED,
                MetricCaptureLane.INCREMENTAL, 10)).isOne();
            harness.session().commit(true);
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(capture, true));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reporting_metric_event"
                + " WHERE event_type='INTERVIEW_PASSED' AND source_id='feedback-version'", Integer.class)).isOne();
            var checkpoint = harness.mapper().lock("INTERVIEW_PASSED:INCREMENTAL");
            assertThat(checkpoint.getLastSourceId()).isEqualTo("feedback-version");
            assertThat(checkpoint.getLastCompletedAt()).isEqualTo(NOW);
          }
        }
      } finally {
        business.rollback();
      }
    });
  }

  @Test
  void projectionAndCheckpointRollbackTogetherAndCanResumeOnAnotherInstance() throws Exception {
    withDatabase(connection -> {
      createMetricFixture(connection);
      seedMetricSources(connection);
      migrate(connection);
      connection.setAutoCommit(false);
      try (var harness = harness(connection)) {
        var failing = mock(MetricCaptureCheckpointMapper.class, delegatesTo(harness.mapper()));
        doThrow(new IllegalStateException("simulated checkpoint failure")).when(failing).advance(any(), anyString(), any());
        ReflectionTestUtils.setField(harness.service(), "baseMapper", failing);
        assertThatThrownBy(() -> harness.service().captureNext(MetricEventType.RESUME_INGESTED, MetricCaptureLane.INCREMENTAL, 1))
            .hasMessage("simulated checkpoint failure");
        harness.session().rollback(true);
        var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reporting_metric_event", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reporting_metric_capture_checkpoint", Long.class)).isZero();
        var resumed = new MetricCaptureServiceImpl();
        ReflectionTestUtils.setField(resumed, "baseMapper", harness.mapper());
        ReflectionTestUtils.setField(resumed, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(resumed.captureNext(MetricEventType.RESUME_INGESTED, MetricCaptureLane.INCREMENTAL, 1)).isEqualTo(1);
        harness.session().commit(true);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reporting_metric_event", Long.class)).isEqualTo(1);
      }
    });
  }

  @Test
  void twoInstancesSerializeOneCheckpointWithoutDroppingEitherBatch() throws Exception {
    withDatabase(connection -> {
      createMetricFixture(connection);
      seedMetricSources(connection);
      migrate(connection);
      String url = connection.getMetaData().getURL();
      var start = new CyclicBarrier(2);
      try (var workers = Executors.newFixedThreadPool(2)) {
        var first = workers.submit(() -> concurrentBatch(url, start));
        var second = workers.submit(() -> concurrentBatch(url, start));
        assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo(1);
      }
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reporting_metric_event", Long.class)).isEqualTo(2);
      assertThat(jdbc.queryForObject("SELECT last_source_id FROM reporting_metric_capture_checkpoint", String.class)).isEqualTo("resume-from");
      assertThat(jdbc.queryForObject("SELECT version FROM reporting_metric_capture_checkpoint", Integer.class)).isEqualTo(2);
    });
  }

  @Test
  void upgradeIsRerunnablePreservesCheckpointAndRejectsWrongExistingDefinition() throws Exception {
    withDatabase(connection -> {
      createMetricFixture(connection);
      migrate(connection);
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.update("INSERT INTO reporting_metric_capture_checkpoint(checkpoint_name,source_code,lane,last_source_id)"
          + " VALUES('RESUME_INGESTED:INCREMENTAL','RESUME_INGESTED','INCREMENTAL','saved')");
      migrate(connection);
      assertThat(jdbc.queryForObject("SELECT last_source_id FROM reporting_metric_capture_checkpoint", String.class)).isEqualTo("saved");
      jdbc.execute("ALTER TABLE reporting_metric_capture_checkpoint MODIFY lane VARCHAR(60) NOT NULL");
      assertThatThrownBy(() -> migrate(connection)).hasRootCauseMessage("Unknown column 'existing reporting_metric_capture_checkpoint has an incompatible definition' in 'field list'");
      assertThat(jdbc.queryForObject("SELECT last_source_id FROM reporting_metric_capture_checkpoint", String.class)).isEqualTo("saved");
    });
  }

  private static int concurrentBatch(String url, CyclicBarrier start) throws Exception {
    try (var connection = DriverManager.getConnection(url, requiredEnvironment("HR_DB_USER"), requiredEnvironment("HR_DB_PASSWORD"))) {
      connection.setAutoCommit(false);
      try (var harness = harness(connection)) {
        start.await(10, TimeUnit.SECONDS);
        int count = harness.service().captureNext(MetricEventType.RESUME_INGESTED, MetricCaptureLane.INCREMENTAL, 1);
        harness.session().commit(true);
        return count;
      }
    }
  }

  private static void complete(Harness harness, MetricEventType source, MetricCaptureLane lane, int limit) {
    for (int batch = 0; batch < 20; batch++) {
      assertThat(harness.service().captureNext(source, lane, limit)).isBetween(0, limit);
      harness.session().commit(true);
      var checkpoint = harness.mapper().lock(source.getCode() + ':' + lane.getCode());
      boolean completed = checkpoint.getCycleUpperId() == null;
      harness.session().commit(true);
      if (completed) return;
    }
    throw new AssertionError("synthetic metric cycle did not finish");
  }

  private static Harness harness(Connection connection) throws Exception {
    // 所有指标实库场景使用生产事务声明；DEFAULT时保留数据库默认隔离级别。
    Isolation isolation = MetricCaptureApplicationManager.class
        .getMethod("captureNext", MetricEventType.class, MetricCaptureLane.class, int.class)
        .getAnnotation(Transactional.class).isolation();
    if (isolation != Isolation.DEFAULT) connection.setTransactionIsolation(isolation.value());
    var configuration = new MybatisConfiguration();
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.setEnvironment(new Environment("metric-incremental", new JdbcTransactionFactory(), new SingleConnectionDataSource(connection, true)));
    configuration.addMapper(MetricCaptureCheckpointMapper.class);
    configuration.addMapper(MetricMapper.class);
    var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(connection);
    var mapper = session.getMapper(MetricCaptureCheckpointMapper.class);
    var service = new MetricCaptureServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    ReflectionTestUtils.setField(service, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    return new Harness(session, mapper, service);
  }

  private static void migrate(Connection connection) {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (current != null && !Files.exists(current.resolve("docs/sql/hr_ai_complete_schema.sql"))) current = current.getParent();
    if (current == null) throw new IllegalStateException("repository root missing");
    ScriptUtils.executeSqlScript(connection, new EncodedResource(new FileSystemResource(
        current.resolve("bootstrap/src/test/resources/db/legacy-upgrades/hr_ai_upgrade_20260911_01__metric_capture_checkpoint.sql")), StandardCharsets.UTF_8));
  }

  private record Harness(SqlSession session, MetricCaptureCheckpointMapper mapper, MetricCaptureServiceImpl service) implements AutoCloseable {
    @Override public void close() { session.close(); }
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
    String schema = "hr_metric_incremental_it_" + UUID.randomUUID().toString().replace("-", "");
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
