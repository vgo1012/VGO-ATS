package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 合并历史查询索引交付契约；真实 MySQL 升级/重跑由外部数据库门禁执行。 */
class CandidateMergeHistorySqlContractTest {
    @Test
    void protectedHistoryIndexesHaveBaselineUpgradeAndPackagedMigration() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        if (!Files.exists(root.resolve("docs/sql"))) root = root.getParent();
        String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
        String upgrade = Files.readString(root.resolve("docs/sql/hr_ai_upgrade_20261008_02__candidate_merge_history_indexes.sql"));
        String packaging = Files.readString(root.resolve("bootstrap/pom.xml"));
        String[][] indexes = {
            {"matching_candidate_selection_item", "ix_matching_selection_candidate"},
            {"matching_match_run_item_outcome", "ix_matching_item_outcome_candidate"},
            {"matching_weighted_result", "ix_matching_result_candidate"},
            {"submission_client_restriction_current", "ix_client_restriction_current_candidate"}
        };
        int firstDdl = upgrade.indexOf("ALTER TABLE ");
        assertTrue(firstDdl > 0);
        for (String[] index : indexes) {
            int tableStart = baseline.indexOf("CREATE TABLE `" + index[0] + "`");
            int tableEnd = baseline.indexOf(") ENGINE=InnoDB", tableStart);
            assertTrue(tableStart >= 0 && tableEnd > tableStart);
            String tableDefinition = baseline.substring(tableStart, tableEnd);
            assertTrue(tableDefinition.matches("(?s).*INDEX\\s+" + index[1] + "\\s*\\(candidate_id\\).*"));
            assertTrue(upgrade.contains("TABLE_NAME='" + index[0] + "' AND COLUMN_NAME='candidate_id'"));
            int incompatibility = upgrade.indexOf(index[1] + " has incompatible definition");
            assertTrue(incompatibility >= 0 && incompatibility < firstDdl);
            assertTrue(upgrade.contains("ALTER TABLE " + index[0] + " ADD INDEX " + index[1] + " (candidate_id)"));
        }
        assertTrue(upgrade.contains("TABLE_SCHEMA=DATABASE()"));
        assertTrue(upgrade.contains("CHARACTER_MAXIMUM_LENGTH=26"));
        assertTrue(upgrade.contains("IS_VISIBLE='YES' AND SUB_PART IS NULL"));
        assertTrue(upgrade.contains("DATABASE() IS NOT NULL"));
        assertTrue(packaging.contains("docs/sql/hr_ai_upgrade_20261008_02__candidate_merge_history_indexes.sql"));
        assertTrue(packaging.contains("db/migration/V2026100802__candidate_merge_history_indexes.sql"));
        assertTrue(packaging.contains("db/migration/V2026100801__wecom_create_recovery.sql"));
        assertFalse(upgrade.contains("DELETE FROM"));
        assertFalse(upgrade.contains("UPDATE "));
        assertFalse(upgrade.contains("DROP INDEX"));
        assertFalse(upgrade.contains("DROP TABLE"));
    }
}

