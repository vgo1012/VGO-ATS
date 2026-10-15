package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** 仅在显式测试服务创建随机数据库；验证旧 AI 运行表升级、重跑及空库基线。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
class AiRunSchemaExternalMySqlIT {
  private static final List<String> TABLES = List.of(
      "talent_resume_extraction_run", "talent_resume_ai_model_run", "demand_jd_ai_model_run");

  @Test
  void preservesHistoryAndSupportsPendingMetadataAfterPartialUpgradeAndRerun() throws Exception {
    withDatabase(connection -> {
      JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      for (String table : TABLES) {
        String metadata = table.endsWith("extraction_run")
            ? "extractor_version VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL"
            : "model_provider VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,"
                + "model_version VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL";
        jdbc.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY," + metadata + ")");
        jdbc.update("INSERT INTO " + table + " VALUES (1,'known'"
            + (table.endsWith("extraction_run") ? ")" : ",'v1')"));
      }
      var history = TABLES.stream().map(table -> jdbc.queryForList("SELECT * FROM " + table)).toList();
      try (var statement = connection.createStatement()) {
        assertThatThrownBy(() -> statement.executeUpdate(
            "INSERT INTO talent_resume_extraction_run(id) VALUES(2)"))
            .isInstanceOf(SQLException.class);
      }
      // 模拟部署中断时只完成第一列，其余四列仍为旧定义。
      jdbc.execute("ALTER TABLE talent_resume_extraction_run MODIFY extractor_version"
          + " VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL");
      execute(connection, upgrade());
      execute(connection, upgrade());
      assertMetadataNullable(jdbc);
      assertThat(TABLES.stream().map(table -> jdbc.queryForList("SELECT * FROM " + table)).toList())
          .isEqualTo(history);
      for (String table : TABLES) {
        assertThat(jdbc.update("INSERT INTO " + table + "(id) VALUES(2)")).isEqualTo(1);
      }
      execute(connection, upgrade());
      for (String table : TABLES) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isEqualTo(2);
      }
    });
  }

  @Test
  void completeBaselineAlreadyAllowsPendingMetadataAndAcceptsUpgradeRerun() throws Exception {
    withDatabase(connection -> {
      execute(connection, sqlRoot().resolve("hr_ai_complete_schema.sql"));
      JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      assertMetadataNullable(jdbc);
      execute(connection, upgrade());
      execute(connection, upgrade());
      assertMetadataNullable(jdbc);
    });
  }

  private static void assertMetadataNullable(JdbcTemplate jdbc) {
    var values = jdbc.queryForList("SELECT IS_NULLABLE FROM information_schema.COLUMNS"
        + " WHERE TABLE_SCHEMA=DATABASE() AND ((TABLE_NAME='talent_resume_extraction_run'"
        + " AND COLUMN_NAME='extractor_version') OR (TABLE_NAME IN"
        + " ('talent_resume_ai_model_run','demand_jd_ai_model_run')"
        + " AND COLUMN_NAME IN ('model_provider','model_version')))", String.class);
    assertThat(values).hasSize(5).containsOnly("YES");
  }

  private static void execute(Connection connection, Path script) {
    ScriptUtils.executeSqlScript(connection,
        new EncodedResource(new FileSystemResource(script), StandardCharsets.UTF_8));
  }

  private static Path upgrade() {
    return sqlRoot()
        .resolve("../../bootstrap/src/test/resources/db/legacy-upgrades")
        .normalize()
        .resolve("hr_ai_upgrade_20260907_04__ai_run_pending_metadata.sql");
  }

  private static Path sqlRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (current != null && !Files.isDirectory(current.resolve("docs/sql"))) current = current.getParent();
    if (current == null) throw new IllegalStateException("SQL source root not found");
    return current.resolve("docs/sql");
  }

  private static void withDatabase(DatabaseCheck check) throws Exception {
    String schema = "hr_ai_run_it_" + UUID.randomUUID().toString().replace("-", "");
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
