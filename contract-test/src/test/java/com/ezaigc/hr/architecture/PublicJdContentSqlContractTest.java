package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 公共 JD 内容共享的部署契约；真实 MySQL 升级和重跑须另行验证。 */
class PublicJdContentSqlContractTest {
  @Test
  void cityCatalogIsIdenticalAcrossJdInputBoundaries() throws Exception {
    Path root = Path.of(System.getProperty("user.dir"));
    if (!Files.exists(root.resolve("ui"))) root = root.getParent();
    assertEquals(Files.readString(root.resolve("ui/src/demand/cities-2023.json")),
        Files.readString(root.resolve("bootstrap/src/main/resources/jd-cities-2023.json")),
        "前后端必须使用同一城市目录，不允许一侧接受另一侧拒绝的城市");
  }

  @Test
  void publicContentIsOptInAndPackagedWithoutChangingHistoricalVisibility() throws Exception {
    Path root = Path.of(System.getProperty("user.dir"));
    if (!Files.exists(root.resolve("docs/sql"))) root = root.getParent();
    String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
    String upgrade = Files.readString(root.resolve("docs/sql/hr_ai_upgrade_20261010_01__public_jd_content.sql"));
    String packaging = Files.readString(root.resolve("bootstrap/pom.xml"));
    int jdStart = baseline.indexOf("CREATE TABLE `demand_jd`");
    int jdEnd = baseline.indexOf("CREATE TABLE `demand_jd_version`", jdStart);
    assertTrue(jdStart >= 0 && jdEnd > jdStart);
    String jd = baseline.substring(jdStart, jdEnd);

    assertTrue(jd.contains("public_visible BOOLEAN NOT NULL DEFAULT FALSE"));
    assertTrue(jd.contains("content_shared BOOLEAN NOT NULL DEFAULT FALSE"));
    assertTrue(jd.contains("主动共享 JD 内容，不授予招聘记录权限"));
    assertTrue(jd.contains("ix_demand_jd_department_sources"));
    assertTrue(jd.contains("ix_demand_jd_public_sources (content_shared, deleted, status, create_time DESC, id DESC)"));

    int firstDdl = upgrade.indexOf("ALTER TABLE ");
    assertTrue(firstDdl > 0);
    assertTrue(upgrade.indexOf("demand_jd.content_shared has an incompatible definition") < firstDdl);
    assertTrue(upgrade.indexOf("ix_demand_jd_public_sources has an incompatible definition") < firstDdl);
    assertTrue(upgrade.contains("TABLE_SCHEMA=DATABASE()"));
    assertTrue(upgrade.contains("COLUMN_DEFAULT='0'"));
    assertTrue(upgrade.contains("1:content_shared:A,2:deleted:A,3:status:A,4:create_time:D,5:id:D"));
    assertTrue(upgrade.contains("ADD COLUMN content_shared BOOLEAN NOT NULL DEFAULT FALSE"));
    assertTrue(upgrade.contains("ADD INDEX ix_demand_jd_public_sources (content_shared, deleted, status, create_time DESC, id DESC)"));
    assertFalse(upgrade.matches("(?is).*\\b(?:UPDATE|DELETE|INSERT|DROP)\\s+.*"));
    assertTrue(packaging.contains("docs/sql/hr_ai_upgrade_20261010_01__public_jd_content.sql"));
    assertTrue(packaging.contains("db/migration/V2026101001__public_jd_content.sql"));
  }
}
