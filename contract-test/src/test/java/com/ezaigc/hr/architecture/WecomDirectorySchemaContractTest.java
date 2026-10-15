package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class WecomDirectorySchemaContractTest {
    @Test
    void accountLinkMigrationIsPackagedAndKeepsIdentityAndAuthorizationSeparate() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        if (!Files.exists(root.resolve("docs/sql"))) root = root.getParent();
        String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
        String upgrade = Files.readString(root.resolve("docs/sql/hr_ai_upgrade_20260917_02__wecom_account_link.sql"));
        String packaging = Files.readString(root.resolve("bootstrap/pom.xml"));
        for (String required : List.of("identity_wecom_account_link", "uk_wecom_account_link_member",
                "uk_wecom_account_link_account", "FOREIGN KEY (wecom_member_id)", "FOREIGN KEY (user_account_id)",
                "identity_wecom_department_link", "uk_wecom_department_link_source",
                "idx_wecom_department_link_local", "FOREIGN KEY (wecom_department_id)",
                "FOREIGN KEY (local_department_id)")) {
            assertTrue(baseline.contains(required), required);
            assertTrue(upgrade.contains(required), required);
        }
        assertTrue(packaging.contains("V2026091702__wecom_account_link.sql"));
        assertTrue(upgrade.contains("(INDEX_NAME='uk_wecom_department_link_source' AND NON_UNIQUE=0)"));
        assertTrue(upgrade.contains("(INDEX_NAME='idx_wecom_department_link_local' AND NON_UNIQUE=1)"));
        for (String forbidden : List.of("INSERT INTO identity_user_account", "UPDATE identity_user_account",
                "INSERT INTO identity_user_role", "UPDATE identity_user_role")) {
            assertFalse(upgrade.contains(forbidden), forbidden);
        }
    }

    @Test
    void precreatedDirectoryTablesReceiveOnlyMissingVersionColumns() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        if (!Files.exists(root.resolve("docs/sql"))) root = root.getParent();
        String upgrade = Files.readString(root.resolve("docs/sql/hr_ai_upgrade_20260917_01__wecom_directory_version.sql"));
        String packaging = Files.readString(root.resolve("bootstrap/pom.xml"));
        assertTrue(packaging.contains("V2026091701__wecom_directory_version.sql"));
        assertTrue(upgrade.contains("TABLE_SCHEMA=DATABASE()"));
        assertTrue(upgrade.contains("information_schema.COLUMNS"));
        assertTrue(upgrade.contains("COLUMN_DEFAULT IS NULL"));
        assertTrue(upgrade.indexOf("existing wecom version column is incompatible") < upgrade.indexOf("ALTER TABLE"));
        for (String table : List.of("identity_wecom_sync_state", "identity_wecom_sync_batch", "identity_wecom_department", "identity_wecom_member")) {
            assertTrue(upgrade.contains("TABLE_NAME='" + table + "' AND COLUMN_NAME='version')=0"));
            assertTrue(upgrade.contains("ALTER TABLE " + table + " ADD COLUMN version INT NOT NULL DEFAULT 0"));
        }
        for (String forbidden : List.of("DROP ", "DELETE ", "UPDATE ", "INSERT ", "MODIFY ", "CHANGE ")) {
            assertFalse(upgrade.contains(forbidden), forbidden);
        }
    }

    @Test
    void forwardMigrationMatchesFinalSchemaAndNeverMutatesAccounts() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        if (!Files.exists(root.resolve("docs/sql"))) root = root.getParent();
        String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
        String upgrade = Files.readString(root.resolve("docs/sql/hr_ai_upgrade_20260916_01__wecom_directory.sql"));
        String packaging = Files.readString(root.resolve("bootstrap/pom.xml"));
        for (String required : List.of("information_schema.TABLES", "TABLE_SCHEMA=DATABASE()", "IF NOT EXISTS", "identity_user_account")) {
            assertTrue(upgrade.contains(required), required);
        }
        assertTrue(packaging.contains("V2026091601__wecom_directory.sql"));
        assertTrue(packaging.contains("hr_ai_upgrade_20260916_01__wecom_directory.sql"));
        for (String table : List.of("identity_wecom_sync_state", "identity_wecom_sync_batch", "identity_wecom_department", "identity_wecom_member")) {
            Pattern definition = Pattern.compile("(?s)CREATE TABLE (?:IF NOT EXISTS )?" + table + " \\(.*?;");
            var current = definition.matcher(baseline);
            var migration = definition.matcher(upgrade);
            assertTrue(current.find(), table);
            assertTrue(migration.find(), table);
            assertEquals(current.group().replace("\r\n", "\n"), migration.group().replace("IF NOT EXISTS ", "").replace("\r\n", "\n"));
        }
        for (String forbidden : List.of("DROP TABLE", "DELETE FROM", "UPDATE identity_user_account", "INSERT INTO identity_user_account")) {
            assertFalse(upgrade.contains(forbidden), forbidden);
        }
        for (String required : List.of("uk_wecom_department_identity (corp_id, department_id)", "uk_wecom_member_identity (corp_id, user_id)",
                "uk_wecom_batch_request (corp_id, request_key)")) {
            assertTrue(baseline.contains(required), required);
        }
    }
}
