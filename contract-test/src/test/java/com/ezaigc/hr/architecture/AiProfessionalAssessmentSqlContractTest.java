package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AiProfessionalAssessmentSqlContractTest {
  private static final Path ROOT = repositoryRoot();

  @Test
  void baselineAndUpgradeDefineSameImmutableAiAssessmentCache() throws Exception {
    String baseline = Files.readString(ROOT.resolve("docs/sql/hr_ai_complete_schema.sql"));
    String upgrade =
        Files.readString(
            ROOT.resolve("docs/sql/hr_ai_upgrade_20260920_01__ai_professional_assessment.sql"));

    assertTrue(baseline.contains("CREATE TABLE `matching_ai_professional_assessment`"));
    assertTrue(upgrade.contains("CREATE TABLE IF NOT EXISTS `matching_ai_professional_assessment`"));
    for (String contract :
        new String[] {
          "CONSTRAINT uk_matching_ai_professional_cache UNIQUE (cache_key)",
          "CONSTRAINT fk_matching_ai_professional_jd_fact",
          "CONSTRAINT fk_matching_ai_professional_resume_fact",
          "CONSTRAINT ck_matching_ai_professional_state CHECK",
          "INDEX ix_matching_ai_professional_claim (status,lease_until,update_time,id)"
        }) {
      assertTrue(baseline.contains(contract), contract);
      assertTrue(upgrade.contains(contract), contract);
    }
    assertTrue(upgrade.contains("information_schema.TABLES"));
    assertTrue(upgrade.contains("information_schema.COLUMNS"));
    assertTrue(upgrade.contains("information_schema.STATISTICS"));
    assertTrue(upgrade.contains("information_schema.REFERENTIAL_CONSTRAINTS"));
    assertTrue(upgrade.contains("CREATE TABLE IF NOT EXISTS"));
    assertTrue(
        baseline.contains(
            "scoring_version                VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'legacy-deterministic'"));
    assertTrue(
        upgrade.contains(
            "ALTER TABLE matching_match_run ADD COLUMN scoring_version VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT ''legacy-deterministic''"));
    assertTrue(upgrade.contains("COLUMN_NAME='scoring_version'"));
    assertTrue(upgrade.contains("COLUMN_DEFAULT='legacy-deterministic'"));
    assertFalse(upgrade.contains("DROP TABLE"));
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
}
