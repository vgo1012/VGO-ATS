package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** 显式测试库内验证来源草稿及活动 JD 唯一性、历史数据和升级重复执行。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
class SourceJdSchemaExternalMySqlIT {
  @Test
  void upgradesHistoricalJdsAfterPartialExecutionAndPreservesThemOnRerun() throws Exception {
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.execute("CREATE TABLE demand_jd (id CHAR(26) PRIMARY KEY, demand_id CHAR(26)"
          + " CHARACTER SET ascii COLLATE ascii_bin NOT NULL, create_id CHAR(26)"
          + " CHARACTER SET ascii COLLATE ascii_bin NULL)");
      jdbc.update("INSERT INTO demand_jd VALUES('historical','demand','owner')");
      jdbc.execute("ALTER TABLE demand_jd ADD source_owner_department_id CHAR(26)"
          + " CHARACTER SET ascii COLLATE ascii_bin NULL");
      execute(connection, upgrade());
      execute(connection, upgrade());
      assertThat(jdbc.queryForObject("SELECT demand_id FROM demand_jd WHERE id='historical'", String.class))
          .isEqualTo("demand");
      jdbc.update("INSERT INTO demand_jd(id,create_id,source_fingerprint) VALUES('draft','owner','hash')");
      assertThatThrownBy(() -> jdbc.update("INSERT INTO demand_jd(id,create_id,source_fingerprint)"
          + " VALUES('duplicate','owner','hash')")).isInstanceOf(DataIntegrityViolationException.class);
      jdbc.update("INSERT INTO demand_jd(id,create_id,source_fingerprint) VALUES('other','other-owner','hash')");
      execute(connection, upgrade());
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demand_jd", Integer.class)).isEqualTo(3);
    });
  }

  @Test
  void emptyBaselineAllowsSourceDraftWithoutCreatingAnyFormalDemand() throws Exception {
    withDatabase(connection -> {
      execute(connection, sqlRoot().resolve("hr_ai_complete_schema.sql"));
      execute(connection, upgrade());
      execute(connection, upgrade());
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.update("INSERT INTO demand_jd(id,title,status,create_id,source_owner_department_id,source_fingerprint)"
          + " VALUES('draft','Example','DRAFT','owner','department','hash')");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demand_recruitment_demand", Integer.class)).isZero();
      assertThat(jdbc.queryForObject("SELECT demand_id FROM demand_jd WHERE id='draft'", String.class)).isNull();
    });
  }

  @Test
  void activeIdentityUsesTrimmedTitleAndDistinctionAndReleasesAfterLogicalDelete() throws Exception {
    withDatabase(connection -> {
      execute(connection, sqlRoot().resolve("hr_ai_complete_schema.sql"));
      execute(connection, identityUpgrade());
      execute(connection, identityUpgrade());
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));

      jdbc.update("INSERT INTO demand_jd(id,title,distinction,status) VALUES"
          + "('jd-1',' Java Engineer ',' Shanghai ','DRAFT')");
      String expected = jdbc.queryForObject("SELECT SHA2(CONCAT(CHAR_LENGTH(TRIM(?)), ':',"
          + " TRIM(?), TRIM(?)), 256)", String.class, " Java Engineer ", " Java Engineer ", " Shanghai ");
      assertThat(jdbc.queryForObject("SELECT active_identity_key FROM demand_jd WHERE id='jd-1'", String.class))
          .isEqualTo(expected);

      assertThatThrownBy(() -> jdbc.update("INSERT INTO demand_jd(id,title,distinction,status) VALUES"
          + "('jd-duplicate','Java Engineer','Shanghai','ACTIVE')"))
          .isInstanceOf(DataIntegrityViolationException.class);
      jdbc.update("INSERT INTO demand_jd(id,title,distinction,status) VALUES"
          + "('jd-2','Java Engineer','Beijing','ACTIVE')");
      assertThatThrownBy(() -> jdbc.update("UPDATE demand_jd SET distinction='Shanghai' WHERE id='jd-2'"))
          .isInstanceOf(DataIntegrityViolationException.class);

      jdbc.update("UPDATE demand_jd SET deleted=TRUE WHERE id='jd-1'");
      jdbc.update("UPDATE demand_jd SET distinction='Shanghai' WHERE id='jd-2'");
      jdbc.update("INSERT INTO demand_jd(id,title,distinction,status) VALUES"
          + "('legacy-empty-1','Java Engineer',' ','DRAFT'),"
          + "('legacy-empty-2','Java Engineer',' ','DRAFT')");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demand_jd", Integer.class)).isEqualTo(4);
    });
  }

  @Test
  void concurrentIndependentJdsMayShareTheSameTitleAndDistinction() throws Exception {
    withDatabase(connection -> {
      execute(connection, sqlRoot().resolve("hr_ai_complete_schema.sql"));
      String schema = connection.getCatalog();
      var ready = new CountDownLatch(2);
      var start = new CountDownLatch(1);
      var executor = Executors.newFixedThreadPool(2);
      try {
        var first = executor.submit(() -> insertConcurrentJd(schema, "concurrent-1", ready, start));
        var second = executor.submit(() -> insertConcurrentJd(schema, "concurrent-2", ready, start));
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat((first.get(10, TimeUnit.SECONDS) ? 1 : 0)
            + (second.get(10, TimeUnit.SECONDS) ? 1 : 0)).isEqualTo(2);
      } finally {
        executor.shutdownNow();
      }
    });
  }

  @Test
  void identityUpgradeRejectsHistoricalDuplicatesWithoutChangingBusinessData() throws Exception {
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.execute("CREATE TABLE demand_jd (id CHAR(26) PRIMARY KEY, title VARCHAR(180) NOT NULL,"
          + " distinction VARCHAR(180) NULL, deleted BOOLEAN NOT NULL DEFAULT FALSE) ENGINE=InnoDB");
      jdbc.update("INSERT INTO demand_jd(id,title,distinction) VALUES"
          + "('historical-1','Engineer','North'),('historical-2',' Engineer ',' North ')");

      assertThatThrownBy(() -> execute(connection, identityUpgrade()))
          .hasMessageContaining("duplicate active JD title and distinction");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demand_jd", Integer.class)).isEqualTo(2);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS"
          + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd'"
          + " AND COLUMN_NAME='active_identity_key'", Integer.class)).isZero();

      jdbc.update("UPDATE demand_jd SET distinction='South' WHERE id='historical-2'");
      execute(connection, identityUpgrade());
      execute(connection, identityUpgrade());
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demand_jd", Integer.class)).isEqualTo(2);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS"
          + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd'"
          + " AND INDEX_NAME='uk_jd_active_identity' AND NON_UNIQUE=0", Integer.class)).isEqualTo(1);

      jdbc.execute("ALTER TABLE demand_jd DROP INDEX uk_jd_active_identity");
      execute(connection, identityUpgrade());
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS"
          + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd'"
          + " AND INDEX_NAME='uk_jd_active_identity' AND NON_UNIQUE=0", Integer.class)).isEqualTo(1);
    });
  }

  @Test
  void identityUpgradeRejectsAnIncompatibleGeneratedColumn() throws Exception {
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.execute("CREATE TABLE demand_jd (id CHAR(26) PRIMARY KEY, title VARCHAR(180) NOT NULL,"
          + " distinction VARCHAR(180) NULL, deleted BOOLEAN NOT NULL DEFAULT FALSE,"
          + " active_identity_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin"
          + " GENERATED ALWAYS AS (SHA2(TRIM(title), 256)) STORED) ENGINE=InnoDB");

      assertThatThrownBy(() -> execute(connection, identityUpgrade()))
          .hasMessageContaining("existing demand_jd.active_identity_key has an incompatible definition");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS"
          + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd'"
          + " AND INDEX_NAME='uk_jd_active_identity'", Integer.class)).isZero();
    });
  }
  private static void execute(Connection connection, Path script) {
    ScriptUtils.executeSqlScript(connection,
        new EncodedResource(new FileSystemResource(script), StandardCharsets.UTF_8));
  }

  private static Path upgrade() {
    return sqlRoot()
        .resolve("../../bootstrap/src/test/resources/db/legacy-upgrades")
        .normalize()
        .resolve("hr_ai_upgrade_20260907_05__source_jd_drafts.sql");
  }

  private static Path identityUpgrade() {
    return sqlRoot().resolve("hr_ai_upgrade_20260917_03__jd_active_identity.sql");
  }

  private static boolean insertConcurrentJd(
      String schema, String id, CountDownLatch ready, CountDownLatch start) throws Exception {
    try (Connection connection = DriverManager.getConnection(System.getenv("HR_DB_URL"),
        System.getenv("HR_DB_USER"), System.getenv("HR_DB_PASSWORD"))) {
      connection.setCatalog(schema);
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      ready.countDown();
      if (!start.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting to start concurrent JD insert");
      }
      try {
        jdbc.update("INSERT INTO demand_jd(id,title,distinction,status)"
            + " VALUES(?,'Platform Engineer','East','DRAFT')", id);
        return true;
      } catch (DataIntegrityViolationException duplicate) {
        return false;
      }
    } catch (Exception failure) {
      ready.countDown();
      throw failure;
    }
  }

  private static Path sqlRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (current != null && !Files.isDirectory(current.resolve("docs/sql"))) current = current.getParent();
    if (current == null) throw new IllegalStateException("SQL source root not found");
    return current.resolve("docs/sql");
  }

  private static void withDatabase(DatabaseCheck check) throws Exception {
    String schema = "hr_jd_source_it_" + UUID.randomUUID().toString().replace("-", "");
    try (Connection connection = DriverManager.getConnection(System.getenv("HR_DB_URL"),
        System.getenv("HR_DB_USER"), System.getenv("HR_DB_PASSWORD"));
        var statement = connection.createStatement()) {
      String previous = connection.getCatalog();
      statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
      try {
        connection.setCatalog(schema);
        check.run(connection);
      } finally {
        connection.setCatalog(previous);
        statement.execute("DROP DATABASE `" + schema + "`");
      }
    }
  }

  @FunctionalInterface
  private interface DatabaseCheck { void run(Connection connection) throws Exception; }
}
