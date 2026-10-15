package com.ezaigc.hr.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ReportingExportIndexMigrationContractTest {

  @Test
  void finalSchemaKeepsExportHistoryAndExpiryCompositeIndexes() throws IOException {
    Path schema = repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql");
    assertTrue(Files.isRegularFile(schema), "必须提供最终态建库脚本");

    String sql =
        Files.readString(schema)
            .toLowerCase(Locale.ROOT)
            .replaceAll("--[^\\r\\n]*", " ")
            .replaceAll("\\s+", " ")
            .trim();

    assertEquals(1, occurrences(sql, "index ix_reporting_export_history"), "历史查询索引只能定义一次");
    assertTrue(
        sql.contains(
            "index ix_reporting_export_history (requested_by_user_id, create_time desc, id desc)"),
        "用户导出历史必须覆盖 requested_by_user_id + create_time/id 排序");
    assertEquals(1, occurrences(sql, "index ix_reporting_export_expiry"), "过期清理索引只能定义一次");
    assertTrue(
        sql.contains("index ix_reporting_export_expiry (status, expires_at, id)"),
        "过期清理必须覆盖 status + expires_at/id 范围与排序");
  }

  private static int occurrences(String text, String token) {
    int count = 0;
    int from = 0;
    while ((from = text.indexOf(token, from)) >= 0) {
      count++;
      from += token.length();
    }
    return count;
  }

  private static Path repositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("pom.xml"))
          && Files.isDirectory(current.resolve("docs/sql"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("无法定位仓库根目录");
  }
}
