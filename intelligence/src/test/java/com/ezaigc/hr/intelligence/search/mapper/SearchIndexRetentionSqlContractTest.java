package com.ezaigc.hr.intelligence.search.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SearchIndexRetentionSqlContractTest {
  @Test
  void emptyBaselineAndUpgradeBothDeclareTheStatusLeadingCapacityAccessPath() throws IOException {
    Path root = repositoryRoot();
    String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
    String upgrade = Files.readString(root.resolve("docs/sql/hr_ai_upgrade_20261008_03__search_index_retention.sql"));
    assertThat(baseline).contains("INDEX ix_search_resource_status (status,id)");
    assertThat(upgrade).contains("INDEX ix_search_resource_status (status,id)",
        "INDEX_NAME='ix_search_resource_status'", "SEQ_IN_INDEX=1 AND COLUMN_NAME='status'",
        "SEQ_IN_INDEX=2 AND COLUMN_NAME='id'", "search resource capacity index incompatible");
  }

  @Test
  void lifecycleUpgradeRejectsIncompatibleObjectsInsteadOfRewritingOrDeletingHistory() throws IOException {
    String upgrade = Files.readString(repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20261008_03__search_index_retention.sql"));
    assertThat(upgrade).contains("DATABASE() IS NOT NULL", "information_schema.COLUMNS",
        "information_schema.CHECK_CONSTRAINTS", "CHECK_CLAUSE", "_ascii", "_utf8mb4",
        "TABLE_COLLATION='utf8mb4_0900_ai_ci'", "COLUMN_DEFAULT='0'", "ENGINE='InnoDB'");
    assertThat(upgrade).doesNotContain("DROP TABLE", "DELETE FROM", "UPDATE search_index_resource");
  }

  private static Path repositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("docs/sql/hr_ai_complete_schema.sql"))) return current;
      current = current.getParent();
    }
    throw new IllegalStateException("repository SQL root unavailable");
  }
}
