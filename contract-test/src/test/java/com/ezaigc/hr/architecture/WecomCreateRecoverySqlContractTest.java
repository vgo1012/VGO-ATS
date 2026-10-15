package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 部署布局与恢复数据保护契约；不替代真实 MySQL 升级和重跑验证。 */
class WecomCreateRecoverySqlContractTest {
    @Test
    void creationIntentMigrationIsPackagedAndChecksMetadataBeforeChangingTheSchema() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        if (!Files.exists(root.resolve("docs/sql"))) root = root.getParent();
        String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
        String upgrade = Files.readString(root.resolve("docs/sql/hr_ai_upgrade_20261008_01__wecom_create_recovery.sql"));
        String packaging = Files.readString(root.resolve("bootstrap/pom.xml"));
        assertTrue(baseline.contains("create_intent_id                    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL"));
        assertTrue(upgrade.contains("COLUMN_NAME='create_intent_id'"));
        assertTrue(upgrade.indexOf("create_intent_id has an incompatible definition") < upgrade.indexOf("ADD COLUMN create_intent_id"));
        assertTrue(upgrade.contains("CHARACTER_MAXIMUM_LENGTH=26"));
        assertTrue(upgrade.contains("TABLE_SCHEMA=DATABASE()"));
        assertTrue(upgrade.contains("information_schema.CHECK_CONSTRAINTS"));
        assertTrue(upgrade.contains("NOT EXISTS (SELECT 1 FROM interview_external_calendar_attempt a WHERE a.id=p.create_intent_id)"));
        assertTrue(upgrade.contains("create_intent_id IS NULL"));
        assertTrue(upgrade.contains("WECOM_LEGACY_CREATE_UNKNOWN"));
        assertTrue(baseline.contains("'FAILED','UNKNOWN'"));
        assertTrue(baseline.contains("'RUNNING','SUCCEEDED','FAILED','UNKNOWN'"));
        assertTrue(packaging.contains("docs/sql/hr_ai_upgrade_20261008_01__wecom_create_recovery.sql"));
        assertTrue(packaging.contains("db/migration/V2026100801__wecom_create_recovery.sql"));
        assertFalse(upgrade.contains("DELETE FROM"));
        assertFalse(upgrade.contains("DROP TABLE"));
    }
}
