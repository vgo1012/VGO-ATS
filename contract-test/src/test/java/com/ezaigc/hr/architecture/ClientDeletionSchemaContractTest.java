package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** 客户物理删除依赖这些历史引用外键作为并发安全边界。 */
class ClientDeletionSchemaContractTest {
  @Test void historicalClientReferencesRestrictDeletionInBothBaselines() throws Exception {
    Path root = Path.of("").toAbsolutePath();
    while (!Files.isDirectory(root.resolve("docs/sql"))) root = root.getParent();
    for (String file : List.of("docs/sql/hr_ai_complete_schema.sql",
        "bootstrap/src/main/resources/db/migration/V0001__complete_schema.sql")) {
      String sql = Files.readString(root.resolve(file));
      for (String constraint : List.of("fk_demand_project_client", "fk_demand_position_client",
          "fk_demand_recruitment_client", "fk_demand_jd_client", "fk_demand_jd_version_client",
          "fk_submission_client", "fk_restriction_client")) {
        String expression = "CONSTRAINT\\s+" + constraint
            + "\\s+FOREIGN KEY\\s*\\(client_id\\)\\s+REFERENCES demand_client\\s*\\(id\\)"
            + "(?:\\s+ON DELETE RESTRICT)?\\s*,";
        assertTrue(Pattern.compile(expression, Pattern.CASE_INSENSITIVE).matcher(sql).find(),
            file + " must restrict " + constraint + " (no CASCADE or SET NULL)");
      }
    }
  }
}
