package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.ezaigc.hr.talent.mapper.ResumeIntakeProcessingMapper;
import com.ezaigc.hr.talent.service.impl.ResumeIntakeProcessingPersistenceServiceImpl;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
class ResumeIntakeProcessingExternalMySqlIT {
  private static final Path UPGRADE = repositoryRoot()
      .resolve("bootstrap/src/test/resources/db/legacy-upgrades/hr_ai_upgrade_20260909_03__resume_intake_processing.sql");
  private static final Path PENDING_IMPORT_UPGRADE = repositoryRoot()
      .resolve("bootstrap/src/test/resources/db/legacy-upgrades/hr_ai_upgrade_20260909_05__resume_intake_pending_import.sql");
  private static final Path SCOPED_REPARSE_UPGRADE = repositoryRoot()
      .resolve("docs/sql/hr_ai_upgrade_20260930_01__scoped_intake_reparse.sql");

  @Test
  void historicalDatabaseRejectsIncompleteBundleAndUpgradeRestoresWorkerQuery(@TempDir Path bundle)
      throws Exception {
    withDatabase(database -> {
      var source = new SingleConnectionDataSource(database.connection(), true);
      Flyway.configure().dataSource(source).locations("classpath:db/migration")
          .target("2026092401").load().migrate();
      String id = "01J00000000000000000000010";
      Instant now = Instant.parse("2026-10-08T00:00:00Z");
      try (var session = mapperSessions(database.url()).openSession(true)) {
        var mapper = session.getMapper(ResumeIntakeProcessingMapper.class);
        mapper.insertProcessing(id, "b".repeat(64), "synthetic.txt", "text/plain",
            "synthetic/object", "01JACTOR000000000000000001", now);
        var persistence = new ResumeIntakeProcessingPersistenceServiceImpl();
        ReflectionTestUtils.setField(persistence, "baseMapper", mapper);
        assertThatThrownBy(() -> persistence.findClaimable(now, 1))
            .isInstanceOf(PersistenceException.class)
            .rootCause().isInstanceOf(SQLException.class)
            .satisfies(failure -> {
              var sql = (SQLException) failure;
              assertThat(sql.getErrorCode()).isEqualTo(1054);
              assertThat(sql.getSQLState()).isEqualTo("42S22");
            });

        try (var baseline = getClass().getResourceAsStream("/db/migration/V0001__complete_schema.sql")) {
          Files.copy(baseline, bundle.resolve("V0001__complete_schema.sql"));
        }
        var incomplete = Flyway.configure().dataSource(source)
            .locations("filesystem:" + bundle).ignoreMigrationPatterns(new String[0]).load();
        assertThatThrownBy(incomplete::migrate).isInstanceOf(FlywayException.class);
        assertThat(database.jdbc().queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS"
            + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_resume_intake_processing'"
            + " AND COLUMN_NAME='processing_scope_key'", Integer.class)).isZero();

        var upgrade = Flyway.configure().dataSource(source).locations("classpath:db/migration")
            .ignoreMigrationPatterns(new String[0]).load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(upgrade.migrate().migrationsExecuted).isZero();
        session.clearCache();
        assertThat(persistence.findClaimable(now, 1)).singleElement().satisfies(processing -> {
          assertThat(processing.getId()).isEqualTo(id);
          assertThat(processing.getProcessingScopeKey()).isEqualTo("00000000000000000000000000");
          assertThat(processing.getAttemptCount()).isZero();
        });
        assertThat(persistence.claim(id, 0, "worker-new", now.plusSeconds(60), now)).isOne();
      }
    });
  }

  @Test
  void scopedReparseUpgradePreservesSharedResultAndSurvivesPartialRerun() throws Exception {
    withDatabase(database -> {
      JdbcTemplate jdbc = database.jdbc();
      createPrerequisites(jdbc);
      executeScript(database.connection(), UPGRADE);
      String hash = "b".repeat(64);
      String sharedId = "01J00000000000000000000010";
      String scopedId = "01J00000000000000000000011";
      String intakeId = "01J00000000000000000000012";
      jdbc.update("INSERT INTO talent_resume_intake_processing"
              + " (id,content_sha256,original_file_name,media_type,object_key,status,parsed_facts_json)"
              + " VALUES (?,?,?,?,?,'SUCCEEDED','{}')",
          sharedId, hash, "source.pdf", "application/pdf", "resume/source");
      jdbc.update("INSERT INTO talent_resume_intake (id,batch_id,object_key,status,processing_id)"
              + " VALUES (?,?,?,?,?)", intakeId,
          "01J00000000000000000000004", "resume/source", "IDENTITY_REVIEW_REQUIRED", sharedId);
      jdbc.update("INSERT INTO talent_resume_version (id,intake_id,content_sha256) VALUES (?,?,?)",
          "01J00000000000000000000013", intakeId, hash);
      jdbc.execute("ALTER TABLE talent_resume_intake_processing ADD COLUMN processing_scope_key"
          + " CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL"
          + " DEFAULT '00000000000000000000000000'");

      executeScript(database.connection(), SCOPED_REPARSE_UPGRADE);
      executeScript(database.connection(), SCOPED_REPARSE_UPGRADE);
      assertThat(jdbc.queryForObject("SELECT parsed_facts_json FROM talent_resume_intake_processing"
          + " WHERE id=?", String.class, sharedId)).isEqualTo("{}");
      try (var session = mapperSessions(database.url()).openSession(true)) {
        ResumeIntakeProcessingMapper mapper = session.getMapper(ResumeIntakeProcessingMapper.class);
        assertThat(mapper.insertProcessing("01J00000000000000000000014", hash, "duplicate.pdf",
            "application/pdf", "resume/duplicate", "01JACTOR000000000000000001",
            Instant.parse("2026-09-30T00:00:00Z"))).isZero();
        assertThat(mapper.insertReparseProcessing(scopedId, hash, "source.pdf",
            "application/pdf", "resume/source", "01JACTOR000000000000000001",
            Instant.parse("2026-09-30T00:00:00Z"))).isOne();
        jdbc.update("UPDATE talent_resume_intake SET processing_id=? WHERE id=?", scopedId, intakeId);
        assertThat(mapper.findProcessingIdByResumeVersion("01J00000000000000000000013"))
            .isEqualTo(scopedId);
      }
      executeScript(database.connection(), SCOPED_REPARSE_UPGRADE);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM talent_resume_intake_processing"
          + " WHERE content_sha256=?", Integer.class, hash)).isEqualTo(2);
      assertThat(jdbc.queryForObject("SELECT parsed_facts_json FROM talent_resume_intake_processing"
          + " WHERE id=?", String.class, sharedId)).isEqualTo("{}");
    });
  }

  @Test
  void emptySchemaAlreadyIncludesScopedProcessingUniqueness() throws Exception {
    withDatabase(database -> {
      executeScript(database.connection(), repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));
      JdbcTemplate jdbc = database.jdbc();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS"
          + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_resume_intake_processing'"
          + " AND COLUMN_NAME='processing_scope_key'", Integer.class)).isOne();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS"
          + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_resume_intake_processing'"
          + " AND INDEX_NAME='uk_talent_intake_processing_hash_scope'", Integer.class)).isEqualTo(2);
    });
  }

  @Test
  void upgradeSupportsPartialCompletionAndRerunWithoutChangingHistory() throws Exception {
    withDatabase(database -> {
      JdbcTemplate jdbc = database.jdbc();
      createPrerequisites(jdbc);
      insertHistoricalIntake(jdbc);
      jdbc.execute("ALTER TABLE talent_resume_intake ADD COLUMN processing_id CHAR(26)"
          + " CHARACTER SET ascii COLLATE ascii_bin NULL AFTER object_key");
      var before = jdbc.queryForList("SELECT id,batch_id,object_key,resume_version_id,status,"
          + " processing_id FROM talent_resume_intake");

      executeScript(database.connection(), UPGRADE);
      executeScript(database.connection(), UPGRADE);
      executeScript(database.connection(), PENDING_IMPORT_UPGRADE);
      executeScript(database.connection(), PENDING_IMPORT_UPGRADE);

      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES"
          + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_resume_intake_processing'",
          Integer.class)).isOne();
      assertThat(jdbc.queryForList("SELECT id,batch_id,object_key,resume_version_id,status,"
          + " processing_id FROM talent_resume_intake")).isEqualTo(before);
      assertThat(jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.COLUMNS"
          + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_resume_intake'"
          + " AND COLUMN_NAME IN ('processing_id','score_run_id','score_status')"
          + " ORDER BY ORDINAL_POSITION", String.class))
          .containsExactly("processing_id", "score_run_id", "score_status");
      jdbc.update("INSERT INTO talent_resume_intake (id,batch_id,object_key,resume_version_id,status)"
              + " VALUES (?,?,?,?,?)",
          "01J00000000000000000000006", "01J00000000000000000000004",
          "pending/object", null, "PENDING_IMPORT");
      executeScript(database.connection(), PENDING_IMPORT_UPGRADE);
      assertThat(jdbc.queryForObject("SELECT status FROM talent_resume_intake WHERE id=?", String.class,
          "01J00000000000000000000006")).isEqualTo("PENDING_IMPORT");
    });
  }

  @Test
  void mapperEnforcesHashUniquenessLeaseOwnershipAndExhaustedRetry() throws Exception {
    withDatabase(database -> {
      JdbcTemplate jdbc = database.jdbc();
      createPrerequisites(jdbc);
      executeScript(database.connection(), UPGRADE);
      executeScript(database.connection(), SCOPED_REPARSE_UPGRADE);
      SqlSessionFactory sessions = mapperSessions(database.url());
      String hash = "a".repeat(64);
      CountDownLatch ready = new CountDownLatch(2);
      CountDownLatch start = new CountDownLatch(1);
      try (var pool = Executors.newFixedThreadPool(2)) {
        var first = pool.submit(
            () -> insertConcurrent(sessions, ready, start, "01J00000000000000000000001", hash));
        var second = pool.submit(
            () -> insertConcurrent(sessions, ready, start, "01J00000000000000000000002", hash));
        assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(first.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS)).isEqualTo(1);
      }
      String processingId = jdbc.queryForObject(
          "SELECT id FROM talent_resume_intake_processing WHERE content_sha256=?",
          String.class, hash);
      jdbc.update("INSERT INTO talent_resume_intake (id,batch_id,object_key,status,processing_id)"
              + " VALUES (?,?,?,?,?)", "01J00000000000000000000009",
          "01J00000000000000000000004", "resume/object", "PARSING", processingId);
      jdbc.update("INSERT INTO talent_resume_version"
          + " (id,intake_id,content_sha256) VALUES (?,?,?)",
          "01J00000000000000000000003", "01J00000000000000000000009", hash);
      jdbc.update("INSERT INTO talent_resume_version"
          + " (id,intake_id,content_sha256) VALUES (?,?,?)",
          "01J00000000000000000000015", null, hash);
      try (var session = sessions.openSession(true)) {
        ResumeIntakeProcessingMapper mapper = session.getMapper(ResumeIntakeProcessingMapper.class);
        assertThat(mapper.findProcessingIdByResumeVersion("01J00000000000000000000003"))
            .isEqualTo(processingId);
        assertThat(mapper.findProcessingIdByResumeVersion("01J00000000000000000000015"))
            .isEqualTo(processingId);
        Instant claimedAt = Instant.parse("2026-09-09T00:00:00Z");
        assertThat(mapper.claim(processingId, 0, "worker-old",
            claimedAt.plusSeconds(30), claimedAt)).isOne();
        assertThat(mapper.succeed(processingId, "worker-old", "[]", "{}",
            "provider", "model", null, null, 0, null, claimedAt.plusSeconds(31))).isZero();
        jdbc.update("UPDATE talent_resume_intake_processing SET attempt_count=3,max_attempts=3,"
            + " lease_expires_at=? WHERE id=?", Timestamp.from(claimedAt), processingId);
        assertThat(mapper.expireExhausted(claimedAt.plusSeconds(31), 10)).isOne();
        assertThat(mapper.retry(processingId, "01JACTOR000000000000000001", claimedAt.plusSeconds(32)))
            .isOne();
        long version = jdbc.queryForObject(
            "SELECT version FROM talent_resume_intake_processing WHERE id=?",
            Long.class, processingId);
        assertThat(mapper.claim(processingId, version, "worker-new",
            claimedAt.plusSeconds(90), claimedAt.plusSeconds(33))).isOne();
      }
    });
  }

  @Test
  void currentReadFindsConcurrentHashWinnerAfterRepeatableReadSnapshot() throws Exception {
    withDatabase(database -> {
      JdbcTemplate jdbc = database.jdbc();
      createPrerequisites(jdbc);
      executeScript(database.connection(), UPGRADE);
      executeScript(database.connection(), SCOPED_REPARSE_UPGRADE);
      SqlSessionFactory sessions = mapperSessions(database.url());
      String hash = "c".repeat(64);
      Instant now = Instant.parse("2026-09-09T00:00:00Z");
      try (SqlSession snapshot = sessions.openSession(false);
          SqlSession winner = sessions.openSession(true)) {
        Connection connection = snapshot.getConnection();
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        connection.setAutoCommit(false);
        assertThat(connection.getAutoCommit()).isFalse();
        try {
          try (Statement statement = connection.createStatement();
              var result = statement.executeQuery("SELECT COUNT(*) FROM talent_resume_intake_processing")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
          }
          assertThat(winner.getConnection().getAutoCommit()).isTrue();
          assertThat(winner.getMapper(ResumeIntakeProcessingMapper.class).insertProcessing(
              "01J00000000000000000000007", hash, "winner.pdf", "application/pdf", "winner/object",
              "01JACTOR000000000000000001", now)).isOne();

          ResumeIntakeProcessingMapper mapper = snapshot.getMapper(ResumeIntakeProcessingMapper.class);
          assertThat(mapper.insertProcessing("01J00000000000000000000008", hash, "loser.pdf",
              "application/pdf", "loser/object", "01JACTOR000000000000000001", now)).isZero();
          ResumeIntakeProcessingPersistenceServiceImpl service =
              new ResumeIntakeProcessingPersistenceServiceImpl();
          ReflectionTestUtils.setField(service, "baseMapper", mapper);
          var dataSource = sessions.getConfiguration().getEnvironment().getDataSource();
          // 模拟生产 Spring 事务绑定，使 BaseMapper 内部新会话复用当前 snapshot 连接。
          TransactionSynchronizationManager.bindResource(
              dataSource, new ConnectionHolder(connection));
          try {
            assertThat(service.findByHash(hash)).extracting("id")
                .isEqualTo("01J00000000000000000000007");
          } finally {
            TransactionSynchronizationManager.unbindResource(dataSource);
          }
        } finally {
          connection.rollback();
        }
      }
    });
  }

  private static int insertConcurrent(SqlSessionFactory sessions, CountDownLatch ready,
      CountDownLatch start, String id, String hash) throws Exception {
    ready.countDown();
    if (!start.await(30, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Concurrent insert start barrier timed out");
    }
    try (var session = sessions.openSession(true)) {
      return session.getMapper(ResumeIntakeProcessingMapper.class).insertProcessing(
          id, hash, "resume.pdf", "application/pdf", "object/key",
          "01JACTOR000000000000000001", Instant.parse("2026-09-09T00:00:00Z"));
    }
  }

  private static SqlSessionFactory mapperSessions(String url) {
    var dataSource = new DriverManagerDataSource(
        url, requiredEnvironment("HR_DB_USER"), requiredEnvironment("HR_DB_PASSWORD"));
    var configuration = new MybatisConfiguration();
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.setEnvironment(
        new Environment(
            "resume-intake-processing-it", new SpringManagedTransactionFactory(), dataSource));
    configuration.addMapper(ResumeIntakeProcessingMapper.class);
    return new MybatisSqlSessionFactoryBuilder().build(configuration);
  }

  private static void createPrerequisites(JdbcTemplate jdbc) {
    jdbc.execute("CREATE TABLE talent_intake_batch (id CHAR(26) PRIMARY KEY)"
        + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    jdbc.execute("CREATE TABLE talent_resume_intake (id CHAR(26) PRIMARY KEY,"
        + " batch_id CHAR(26) NOT NULL,object_key VARCHAR(500) NOT NULL,"
        + " resume_version_id CHAR(26) NULL,status VARCHAR(32) NOT NULL,"
        + " CONSTRAINT ck_talent_intake_status CHECK"
        + " (status IN ('READY_FOR_CONFIRMATION','CONFIRMED',"
        + "'IGNORED','REJECTED','QUARANTINED','FAILED_RETRYABLE')))"
        + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    jdbc.execute("CREATE TABLE talent_resume_version (id CHAR(26) PRIMARY KEY,"
        + " intake_id CHAR(26) NULL,"
        + " content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL)"
        + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  }

  private static void insertHistoricalIntake(JdbcTemplate jdbc) {
    jdbc.update("INSERT INTO talent_intake_batch(id) VALUES (?)",
        "01J00000000000000000000004");
    jdbc.update("INSERT INTO talent_resume_intake"
        + " (id,batch_id,object_key,resume_version_id,status) VALUES (?,?,?,?,?)",
        "01J00000000000000000000005", "01J00000000000000000000004",
        "legacy/object", null, "CONFIRMED");
  }

  private static void withDatabase(DatabaseWork work) throws Exception {
    String sourceUrl = requiredEnvironment("HR_DB_URL");
    String schema = "hr_intake_it_" + UUID.randomUUID().toString().replace("-", "");
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

  private static void executeScript(Connection connection, Path script) {
    ScriptUtils.executeSqlScript(connection,
        new EncodedResource(new FileSystemResource(script), StandardCharsets.UTF_8));
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " must be configured");
    }
    return value;
  }

  private static Path repositoryRoot() {
    Path current = Path.of("").toAbsolutePath();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("docs/sql/hr_ai_complete_schema.sql"))) return current;
      current = current.getParent();
    }
    throw new IllegalStateException("Cannot locate repository root");
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
