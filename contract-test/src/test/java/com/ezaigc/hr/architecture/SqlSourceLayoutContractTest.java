package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SqlSourceLayoutContractTest {

  private static final String CONSOLIDATED_SCHEMA = "hr_ai_complete_schema.sql";
  private static final Path LEGACY_UPGRADE_FIXTURES =
      Path.of("bootstrap/src/test/resources/db/legacy-upgrades");
  private static final Path RELEASED_BASELINE =
      Path.of("bootstrap/src/main/resources/db/migration/V0001__complete_schema.sql");

  private static final Map<String, String> STANDARD_COLUMN_PATTERNS = new LinkedHashMap<>();

  static {
    STANDARD_COLUMN_PATTERNS.put("ext", "`?ext`?\\s+text\\s+null\\s+comment\\s+'json 扩展信息'");
    STANDARD_COLUMN_PATTERNS.put(
        "remark", "`?remark`?\\s+varchar\\(255\\)\\s+null\\s+comment\\s+'备注'");
    STANDARD_COLUMN_PATTERNS.put(
        "create_id",
        "`?create_id`?\\s+char\\(26\\)\\s+character\\s+set\\s+ascii\\s+collate\\s+ascii_bin"
            + "\\s+null\\s+comment\\s+'创建人 id'");
    STANDARD_COLUMN_PATTERNS.put(
        "create_name", "`?create_name`?\\s+varchar\\(64\\)\\s+null\\s+comment\\s+'创建人名称'");
    STANDARD_COLUMN_PATTERNS.put(
        "create_time",
        "`?create_time`?\\s+timestamp\\(6\\)\\s+not\\s+null\\s+default\\s+current_timestamp\\(6\\)"
            + "\\s+comment\\s+'创建时间'");
    STANDARD_COLUMN_PATTERNS.put(
        "update_id",
        "`?update_id`?\\s+char\\(26\\)\\s+character\\s+set\\s+ascii\\s+collate\\s+ascii_bin"
            + "\\s+null\\s+comment\\s+'更新人 id'");
    STANDARD_COLUMN_PATTERNS.put(
        "update_name", "`?update_name`?\\s+varchar\\(64\\)\\s+null\\s+comment\\s+'更新人名称'");
    STANDARD_COLUMN_PATTERNS.put(
        "update_time",
        "`?update_time`?\\s+timestamp\\(6\\)\\s+not\\s+null\\s+default\\s+current_timestamp\\(6\\)"
            + "\\s+on\\s+update\\s+current_timestamp\\(6\\)\\s+comment\\s+'更新时间'");
    STANDARD_COLUMN_PATTERNS.put(
        "version", "`?version`?\\s+int\\s+not\\s+null\\s+default\\s+0\\s+comment\\s+'技术乐观锁版本号'");
  }

  private static final Pattern REDUNDANT_LOMBOK_ACCESSOR =
      Pattern.compile(
          "(?s)public[^\\r\\n;{}]*\\b(\\w+)\\s*\\(\\s*\\)\\s*\\{"
              + "\\s*return\\s+(?:(?:this\\.)?\\1"
              + "|(?:this\\.)?[A-Za-z_$][\\w$]*\\s*==\\s*null\\s*\\?[^;:{}]+:[^;:{}]+"
              + "|Boolean\\.TRUE\\.equals\\(\\s*(?:this\\.)?[A-Za-z_$][\\w$]*\\s*\\))"
              + "\\s*;\\s*\\}");
  private static final Pattern LOMBOK_ACCESSOR_ANNOTATION =
      Pattern.compile("@(?:Data|Getter|Value)\\b");
  private static final Pattern JAVADOC_BLOCK = Pattern.compile("(?s)/\\*\\*.*?\\*/");
  private static final Pattern GENERATED_OR_MANGLED_JAVADOC =
      Pattern.compile(
          "(?:所表达的业务操作"
              + "|判断是否是否|解析解析|客户客户|记录记录"
              + "|\\p{IsHan}[A-Za-z]{2,}|[A-Za-z]{2,}\\p{IsHan})");
  private static final Pattern EXCESSIVE_ENTITY_BLANK_LINES = Pattern.compile("(?:\\n[ \\t]*){3,}");
  private static final Pattern VERSION_CAS_PREDICATE =
      Pattern.compile("\\.eq\\(\\s*([A-Za-z_$][\\w$]*)::getVersion\\s*,");
  private static final Pattern CONCATENATED_SQL_WRAPPER_FRAGMENT =
      Pattern.compile("\\.(?:last|setSql)\\(\\s*\"[^\"]*\"\\s*\\+");
  private static final Pattern CHINESE_TEXT = Pattern.compile("\\p{IsHan}");
  private static final Pattern SQL_COLUMN_DEFINITION =
      Pattern.compile(
          "^\\s*`?([a-z][a-z0-9_]*)`?\\s+"
              + "(?:char|varchar|text|mediumtext|longtext|json|boolean|tinyint|smallint|int|bigint|decimal|date|datetime|timestamp|blob|longblob|binary|varbinary|double|float)\\b",
          Pattern.CASE_INSENSITIVE);

  private static final Set<String> MUTABLE_AUDITED_TABLES =
      Set.of(
          "identity_department",
          "identity_department_assignment_queue",
          "identity_user_account",
          "identity_handover_batch",
          "demand_client",
          "demand_client_project",
          "demand_client_position",
          "demand_recruitment_demand",
          "demand_jd",
          "demand_workflow_template_version",
          "demand_jd_fact_snapshot",
          "demand_jd_ai_model_run",
          "matching_weight_profile",
          "matching_jd_match_plan",
          "matching_match_run",
          "matching_match_run_attempt",
          "matching_match_run_item_outcome",
          "matching_ai_professional_assessment",
          "matching_weighted_result",
          "talent_source_channel",
          "talent_work_calendar_revision",
          "talent_work_calendar_year",
          "talent_work_calendar_day",
          "talent_resume_lifecycle",
          "talent_resume_lifecycle_runtime",
          "talent_resume_lifecycle_run",
          "talent_intake_batch",
          "talent_resume_intake",
          "talent_resume_intake_processing",
          "talent_mailbox_cursor",
          "talent_mail_ingest_run",
          "talent_mail_route_rule",
          "talent_mail_resume",
          "talent_candidate",
          "talent_candidate_identity",
          "talent_resume",
          "talent_resume_version",
          "talent_identity_conflict",
          "talent_download_authorization",
          "talent_resume_extraction_run",
          "talent_resume_ai_model_run",
          "talent_resume_fact_snapshot",
          "risk_company_blacklist_current",
          "recruitment_application",
          "recruitment_application_workflow_step",
          "recruitment_onboarding_process",
          "recruitment_candidate_engagement_lock",
          "recruitment_engagement_lock_policy",
          "recruitment_engagement_collaboration_request",
          "interview_interview",
          "interview_template",
          "interview_feedback",
          "interview_notification_intent",
          "submission_client_submission",
          "submission_client_restriction_current",
          "reporting_audit_capture_checkpoint",
          "reporting_async_task",
          "reporting_async_task_attempt",
          "reporting_task_item",
          "reporting_export_job",
          "search_filter_template",
          "search_projection_checkpoint",
          "search_state_replay_checkpoint",
          "search_index_rebuild_run");

  private static final Set<String> NON_BOOTSTRAP_MODULES =
      Set.of(
          "common",
          "identity",
          "talent",
          "recruiting",
          "intelligence",
          "integration",
          "reporting",
          "contract-test");

  @Test
  void deploymentSqlIsValidAndOnlyBootstrapOwnsRuntimeResources() throws IOException {
    Path root = repositoryRoot();
    Path sqlRoot = root.resolve("docs/sql").normalize();

    Path fixtureRoot = root.resolve(LEGACY_UPGRADE_FIXTURES).normalize();

    Path consolidatedSchema = sqlRoot.resolve(CONSOLIDATED_SCHEMA);
    assertTrue(Files.isRegularFile(consolidatedSchema), "docs/sql 必须包含最终态单文件建库脚本");

    Set<String> upgradeVersions = new HashSet<>();
    Pattern upgradeName =
        Pattern.compile("hr_ai_upgrade_(\\d{8}_\\d{2})__[a-z][a-z0-9_]*\\.sql");
    try (Stream<Path> entries = Files.list(sqlRoot)) {
      for (Path path : entries.toList()) {
        assertTrue(Files.isRegularFile(path), "docs/sql 不得包含子目录: " + path);
        if (path.equals(consolidatedSchema)) {
          continue;
        }
        Matcher name = upgradeName.matcher(path.getFileName().toString());
        assertTrue(name.matches(), "docs/sql 只允许全量建库和带版本的部署 SQL: " + path);
        assertTrue(upgradeVersions.add(name.group(1)), "升级版本不得重复: " + path);
        assertFalse(Files.readString(path).isBlank(), "升级 SQL 不得为空: " + path);
      }
    }

    Set<String> fixtureVersions = new HashSet<>();
    try (Stream<Path> entries = Files.list(fixtureRoot)) {
      for (Path path : entries.filter(Files::isRegularFile).toList()) {
        if (!path.getFileName().toString().endsWith(".sql")) {
          continue;
        }
        Matcher name = upgradeName.matcher(path.getFileName().toString());
        assertTrue(name.matches(), "历史回归夹具必须使用升级 SQL 命名: " + path);
        assertTrue(fixtureVersions.add(name.group(1)), "历史回归夹具版本不得重复: " + path);
        assertFalse(Files.readString(path).isBlank(), "历史回归夹具 SQL 不得为空: " + path);
      }
    }

    List<Path> forbiddenResources =
        NON_BOOTSTRAP_MODULES.stream()
            .map(module -> root.resolve(module).resolve("src/main/resources"))
            .filter(Files::exists)
            .toList();
    assertTrue(
        forbiddenResources.isEmpty(),
        () -> "非启动模块不得保留 src/main/resources:\n" + join(forbiddenResources));
  }

  @Test
  void deployedWecomCalendarMigrationKeepsItsVersionAndContent() throws Exception {
    Path root = repositoryRoot();
    String released = Files.readString(root.resolve("docs/sql/hr_ai_upgrade_20260917_04__wecom_interview_calendar.sql"))
        .replace("\r\n", "\n");
    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(released.getBytes(StandardCharsets.UTF_8)));
    assertEquals("5978dfba3b0bd290532e4df707096fac2e7e46fa67a987f44fd680594875931b", digest,
        "已执行的企业微信日历 V2026091704 必须保留；邮件采集使用独立新版本");
    String pom = Files.readString(root.resolve("bootstrap/pom.xml"));
    assertTrue(pom.contains("db/migration/V2026091704__wecom_interview_calendar.sql"));
    assertTrue(pom.contains("db/migration/V2026091802__mail_resume_intake.sql"));
    assertFalse(pom.contains("db/migration/V2026091704__mail_resume_intake.sql"));
  }

  @Test
  void publicBaselinesSeedOnlyEqualExampleWeights() throws IOException {
    Path root = repositoryRoot();
    Pattern seed = Pattern.compile("SELECT\\s*'00000000000000000000000022'\\s*,\\s*"
        + "'00000000000000000000000021'\\s*,\\s*1\\s*,((?:\\s*\\d+\\s*,){5})");
    for (Path source : List.of(RELEASED_BASELINE, Path.of("docs/sql").resolve(CONSOLIDATED_SCHEMA))) {
      String sql = Files.readString(root.resolve(source));
      Matcher weights = seed.matcher(sql);
      assertTrue(weights.find(), source + "必须初始化公开示例权重");
      assertEquals("20,20,20,20,20,", weights.group(1).replaceAll("\\s+", ""),
          source + "必须初始化五个20%的公开示例权重");
      assertFalse(weights.find(), source + "公开默认权重只能初始化一次");
      assertTrue(sql.contains("'示例默认权重'"), source + "必须明确标识示例默认数据");
    }
  }

  @Test
  void publicFlywayBaselineIsImmutableAndForwardUpgradeIsExplicit() throws Exception {
    Path root = repositoryRoot();
    String released = Files.readString(root.resolve(RELEASED_BASELINE)).replace("\r\n", "\n");
    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(released.getBytes(StandardCharsets.UTF_8)));
    assertEquals("90c0d458ca5aca576e3b867690002d063ead7599748f307c1477414a810cb722", digest,
        "VGO 公开发行 V0001 不可随最终基线修改；后续结构调整必须新增前向迁移");
    String pom = Files.readString(root.resolve("bootstrap/pom.xml"));
    assertFalse(pom.contains("tofile=\"${project.build.outputDirectory}/db/migration/V0001"),
        "构建不得用可变最终基线覆盖已发布 V0001");
    assertTrue(pom.contains("docs/sql/hr_ai_upgrade_20260912_01__owner_scope.sql"));
    assertTrue(pom.contains("db/migration/V2026091201__owner_scope.sql"));
    assertTrue(pom.contains("docs/sql/hr_ai_upgrade_20260914_01__jd_headcount.sql"));
    assertTrue(pom.contains("db/migration/V2026091401__jd_headcount.sql"));
    assertTrue(pom.contains("docs/sql/hr_ai_upgrade_20260914_02__jd_recruitment_cycle.sql"));
    assertTrue(pom.contains("db/migration/V2026091402__jd_recruitment_cycle.sql"));
  }

  @Test
  void loginProtectionColumnsHaveAnExplicitNonDestructiveUpgrade() throws IOException {
    Path root = repositoryRoot();
    String file = "hr_ai_upgrade_20260928_01__login_protection.sql";
    String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
    String upgrade = Files.readString(root.resolve("docs/sql").resolve(file));
    String pom = Files.readString(root.resolve("bootstrap/pom.xml"));

    for (String column : List.of("failed_login_count", "failed_login_window_started_at",
        "login_locked_until")) {
      assertTrue(baseline.contains(column));
      assertTrue(upgrade.contains("COLUMN_NAME='" + column + "'"));
      assertTrue(upgrade.contains("ADD COLUMN " + column));
    }
    assertTrue(upgrade.contains("information_schema.COLUMNS"));
    assertTrue(upgrade.contains("TABLE_SCHEMA=DATABASE()"));
    assertFalse(upgrade.contains("DELETE FROM"));
    assertFalse(upgrade.contains("DROP TABLE"));
    assertTrue(pom.contains("docs/sql/" + file));
    assertTrue(pom.contains("db/migration/V2026092801__login_protection.sql"));
  }

  @Test
  void onboardingProcessHasAnIdempotentForwardUpgradeAndHonestHistoricalDuration() throws IOException {
    Path root = repositoryRoot();
    String file = "hr_ai_upgrade_20260922_02__onboarding_process.sql";
    String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
    String upgrade = Files.readString(root.resolve("docs/sql").resolve(file));
    String pom = Files.readString(root.resolve("bootstrap/pom.xml"));

    assertTrue(createdTables(baseline).contains("recruitment_onboarding_process"));
    assertTrue(upgrade.contains("CREATE TABLE IF NOT EXISTS `recruitment_onboarding_process`"));
    assertTrue(upgrade.contains("information_schema.TABLES"));
    assertTrue(upgrade.contains("information_schema.COLUMNS"));
    assertTrue(upgrade.contains("TABLE_SCHEMA=DATABASE()"));
    assertTrue(upgrade.contains("status IN ('IN_PROGRESS', 'ONBOARDED', 'RETURNED')"));
    assertTrue(Pattern.compile("onboarding_event\\.onboarded_at,\\s*onboarding_event\\.onboarded_at")
        .matcher(upgrade).find(),
        "历史事实只能回填为零等待历时，不得猜测进入入职流程时间");
    assertTrue(upgrade.contains(
        "ON DUPLICATE KEY UPDATE application_id=recruitment_onboarding_process.application_id"));
    assertFalse(upgrade.contains("DELETE FROM"));
    assertFalse(upgrade.contains("DROP TABLE"));
    assertTrue(pom.contains("docs/sql/" + file));
    assertTrue(pom.contains("db/migration/V2026092202__onboarding_process.sql"));
  }

  @Test
  void jdDimensionCodesHaveDictionariesPairSequenceAndNonDestructiveUpgrade() throws IOException {
    Path root = repositoryRoot();
    String file = "hr_ai_upgrade_20260917_06__jd_dimension_code.sql";
    String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
    String upgrade = Files.readString(root.resolve("docs/sql").resolve(file));
    String pom = Files.readString(root.resolve("bootstrap/pom.xml"));
    String cleanup = Files.readString(root.resolve("scripts/database/reset-recruitment-data.sql"));
    for (String table : List.of("demand_jd_code_option", "demand_jd_code_pair_sequence")) {
      assertTrue(createdTables(baseline).contains(table));
      assertTrue(createdTables(upgrade).contains(table));
      assertTrue(cleanup.contains("('" + table + "', 0, '0=1')"), "清理岗位必须保留编号字典与组合上限");
    }
    for (String column : List.of("job_type_code", "work_region_code")) {
      assertTrue(baseline.contains(column));
      assertTrue(upgrade.contains(column));
    }
    assertTrue(upgrade.contains("information_schema.COLUMNS"));
    assertTrue(upgrade.contains("TABLE_SCHEMA=DATABASE()"));
    assertFalse(upgrade.matches("(?s).*\\bUPDATE\\s+demand_jd\\b.*"), "升级不猜测历史维度或重编号");
    assertFalse(upgrade.contains("DELETE FROM"));
    assertFalse(upgrade.contains("DROP TABLE"));
    assertTrue(pom.contains("docs/sql/" + file));
    assertTrue(pom.contains("db/migration/V2026091706__jd_dimension_code.sql"));
  }

  @Test
  void jdTitleSuggestionsHaveIndependentForwardUpgradeAndSeed() throws IOException {
    Path root = repositoryRoot();
    String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
    String upgrade = Files.readString(root.resolve("docs/sql/hr_ai_upgrade_20260923_02__jd_title_suggestions.sql"));
    String pom = Files.readString(root.resolve("bootstrap/pom.xml"));
    String reset = Files.readString(root.resolve("scripts/database/reset-recruitment-data.sql"));
    for (String sql : List.of(baseline, upgrade)) {
      assertTrue(sql.contains("demand_jd_title_suggestion"));
      assertTrue(sql.contains("CONSTRAINT uk_jd_title_suggestion_name UNIQUE (name)"));
      assertTrue(sql.contains("CONSTRAINT ck_jd_title_suggestion_status CHECK"));
      for (String title : List.of("示例开发工程师", "示例产品经理", "示例招聘专员")) {
        assertTrue(sql.contains("'" + title + "', 'APPROVED'"));
      }
      Matcher seed = Pattern.compile("INSERT INTO demand_jd_title_suggestion \\(id, name, status\\) VALUES(.*?);",
          Pattern.DOTALL).matcher(sql);
      assertTrue(seed.find(), "必须包含独立公开岗位种子");
      Matcher titles = Pattern.compile("\\('[^']+', '([^']+)', 'APPROVED'\\)").matcher(seed.group(1));
      assertEquals(List.of("示例开发工程师", "示例产品经理", "示例招聘专员"),
          titles.results().map(match -> match.group(1)).toList(), "只初始化三项公开岗位示例");
    }
    assertTrue(upgrade.contains("CREATE TABLE IF NOT EXISTS `demand_jd_title_suggestion`"));
    assertTrue(upgrade.contains("information_schema.COLUMNS"));
    assertTrue(upgrade.contains("ON DUPLICATE KEY UPDATE id = id"));
    assertFalse(upgrade.contains("DELETE FROM"));
    assertTrue(pom.contains("db/migration/V2026092302__jd_title_suggestions.sql"));
    assertTrue(reset.contains("('demand_jd_title_suggestion', 0, '0=1')"));
  }

  @Test
  void departmentJdSourcesHaveProvenanceAndRerunnableForwardUpgrade() throws IOException {
    Path root = repositoryRoot();
    String file = "hr_ai_upgrade_20260924_01__department_jd_sources.sql";
    String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
    String upgrade = Files.readString(root.resolve("docs/sql").resolve(file));
    String pom = Files.readString(root.resolve("bootstrap/pom.xml"));
    String reset = Files.readString(root.resolve("scripts/database/reset-recruitment-data.sql"));

    for (String sql : List.of(baseline, upgrade)) {
      assertTrue(sql.contains("applied_from_jd_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL"));
      assertTrue(sql.contains("ix_demand_jd_department_sources"));
    }
    assertTrue(upgrade.contains("information_schema.COLUMNS"));
    assertTrue(upgrade.contains("information_schema.STATISTICS"));
    assertTrue(upgrade.contains("TABLE_SCHEMA=DATABASE()"));
    assertFalse(upgrade.contains("DELETE FROM"));
    assertFalse(Pattern.compile("(?i)UPDATE\\s+demand_jd\\s+SET\\s+(?:code|title|owner_department_id)\\s*=")
        .matcher(upgrade).find(), "升级不得猜测历史 JD 或重编号");
    assertTrue(pom.contains("docs/sql/" + file));
    assertTrue(pom.contains("db/migration/V2026092401__department_jd_sources.sql"));
  }

  @Test
  void jdDepartmentCodesHavePermanentMappingAndWidenedForwardSequence() throws IOException {
    Path root = repositoryRoot();
    String file = "hr_ai_upgrade_20260923_01__jd_department_code.sql";
    String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
    String upgrade = Files.readString(root.resolve("docs/sql").resolve(file));
    String pom = Files.readString(root.resolve("bootstrap/pom.xml"));
    String cleanup = Files.readString(root.resolve("scripts/database/reset-recruitment-data.sql"));

    assertTrue(createdTables(baseline).contains("demand_jd_department_code"));
    assertTrue(upgrade.contains("CREATE TABLE IF NOT EXISTS `demand_jd_department_code`"));
    for (String sql : List.of(baseline, upgrade)) {
      assertTrue(sql.contains("CONSTRAINT uk_jd_department_code UNIQUE (code)"));
      assertTrue(sql.contains("CONSTRAINT ck_jd_department_code CHECK (CHAR_LENGTH(code) = 2"));
    }
    assertTrue(baseline.contains("id CHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY"));
    assertTrue(upgrade.contains("MODIFY COLUMN id CHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL"));
    assertTrue(upgrade.contains("information_schema.COLUMNS"));
    assertTrue(upgrade.contains("TABLE_SCHEMA=DATABASE()"));
    assertFalse(upgrade.contains("DELETE FROM"));
    assertFalse(upgrade.contains("DROP TABLE"));
    assertFalse(Pattern.compile("(?i)UPDATE\\s+demand_jd\\s+SET\\s+code\\s*=").matcher(upgrade).find());
    assertTrue(cleanup.contains("('demand_jd_department_code', 0, '0=1')"), "清理岗位不得回收部门码");
    assertTrue(pom.contains("docs/sql/" + file));
    assertTrue(pom.contains("db/migration/V2026092301__jd_department_code.sql"));
  }

  @Test
  void jdSequenceReservedColumnIsQuotedInExecutableSql() throws IOException {
    Path root = repositoryRoot();
    for (String file : List.of(
        "docs/sql/hr_ai_complete_schema.sql",
        "docs/sql/hr_ai_upgrade_20260917_05__jd_short_code_sequence.sql",
        "docs/sql/hr_ai_upgrade_20260917_06__jd_dimension_code.sql",
        "docs/sql/hr_ai_upgrade_20260918_01__jd_independent_grouping.sql",
        "docs/sql/hr_ai_upgrade_20260923_01__jd_department_code.sql",
        "recruiting/src/main/java/com/ezaigc/hr/recruiting/demand/mapper/JdEntityMapper.java",
        "bootstrap/src/test/java/com/ezaigc/hr/bootstrap/SqlDeploymentExternalMySqlIT.java")) {
      // 元数据中的列名和 CHECK 比较值是字符串，不是 SQL 标识符。
      String executable = Files.readString(root.resolve(file)).replaceAll("'([^']|'')*'", "''");
      assertFalse(Pattern.compile("(?i)(?<![`a-z0-9_])last_value(?![`a-z0-9_])")
          .matcher(executable).find(), file + " 中的 MySQL 保留列名必须使用反引号");
    }
  }

  @Test
  void jdShortCodesHaveAForwardOnlySingletonSequence() throws IOException {
    Path root = repositoryRoot();
    String file = "hr_ai_upgrade_20260917_05__jd_short_code_sequence.sql";
    String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
    String upgrade = Files.readString(root.resolve("docs/sql").resolve(file));
    String pom = Files.readString(root.resolve("bootstrap/pom.xml"));
    for (String sql : List.of(baseline, upgrade)) {
      assertTrue(createdTables(sql).contains("demand_jd_code_sequence"));
      assertTrue(sql.contains("CHECK (id = 1)"));
      assertTrue(sql.contains("CHECK (`last_value` >= 0)"));
    }
    assertTrue(upgrade.contains("information_schema.COLUMNS"));
    assertTrue(upgrade.contains("TABLE_SCHEMA=DATABASE()"));
    assertTrue(upgrade.contains("GREATEST(`last_value`"));
    assertFalse(upgrade.contains("UPDATE demand_jd SET"));
    assertFalse(upgrade.contains("DELETE FROM"));
    assertFalse(upgrade.contains("DROP TABLE"));
    assertTrue(pom.contains("docs/sql/" + file));
    assertTrue(pom.contains("db/migration/V2026091705__jd_short_code_sequence.sql"));
    String cleanup = Files.readString(root.resolve("scripts/database/reset-recruitment-data.sql"));
    assertTrue(cleanup.contains("('demand_jd_code_sequence', 0, '0=1')"), "清理岗位必须保留编号上限");
  }

  @Test
  void independentJdGroupingHasPackagedForwardUpgradeWithoutRewritingPermanentCodes() throws IOException {
    Path root = repositoryRoot();
    String file = "hr_ai_upgrade_20260918_01__jd_independent_grouping.sql";
    String upgrade = Files.readString(root.resolve("docs/sql").resolve(file));
    String pom = Files.readString(root.resolve("bootstrap/pom.xml"));
    assertTrue(upgrade.contains("information_schema.COLUMNS"));
    assertTrue(upgrade.contains("information_schema.STATISTICS"));
    assertTrue(upgrade.contains("DROP INDEX uk_jd_active_identity"));
    assertTrue(upgrade.contains("DROP COLUMN active_identity_key"));
    assertFalse(Pattern.compile("(?i)UPDATE\\s+demand_jd\\s+SET\\s+code\\s*=").matcher(upgrade).find());
    assertFalse(upgrade.contains("DELETE FROM"));
    assertFalse(upgrade.contains("DROP TABLE"));
    assertTrue(pom.contains("docs/sql/" + file));
    assertTrue(pom.contains("db/migration/V2026091801__jd_independent_grouping.sql"));
  }

  @Test
  void historicalJdIdentityUpgradeRemainsUnchangedWhileBaselineAllowsIndependentJds() throws IOException {
    Path root = repositoryRoot();
    String file = "hr_ai_upgrade_20260917_03__jd_active_identity.sql";
    String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
    String upgrade = Files.readString(root.resolve("docs/sql").resolve(file));
    String pom = Files.readString(root.resolve("bootstrap/pom.xml"));
    String expression = "SHA2(CONCAT(CHAR_LENGTH(TRIM(title)), ':', TRIM(title), TRIM(distinction)), 256)";

    assertFalse(baseline.contains("active_identity_key"));
    assertFalse(baseline.contains("uk_jd_active_identity"));
    assertTrue(upgrade.contains(expression));
    assertTrue(upgrade.contains("duplicate active JD title and distinction; resolve manually"));
    assertTrue(upgrade.contains("GROUP BY SHA2(CONCAT(CHAR_LENGTH(TRIM(title))"));
    assertTrue(upgrade.contains("information_schema.COLUMNS"));
    assertTrue(upgrade.contains("information_schema.STATISTICS"));
    assertTrue(upgrade.contains("TABLE_SCHEMA=DATABASE()"));
    assertTrue(upgrade.contains("CHAR(96)"));
    assertTrue(upgrade.contains("CHAR(92)"));
    assertTrue(pom.contains("docs/sql/" + file));
    assertTrue(pom.contains("db/migration/V2026091703__jd_active_identity.sql"));
    assertFalse(upgrade.contains("DELETE FROM"));
    assertFalse(upgrade.contains("UPDATE demand_jd"));
    assertFalse(upgrade.contains("DROP TABLE"));
    assertFalse(upgrade.contains("CREATE TRIGGER"));
  }

  @Test
  void mailResumeIntakeHasForwardMigrationAndUidIdempotency() throws IOException {
    Path root = repositoryRoot();
    String file = "hr_ai_upgrade_20260918_02__mail_resume_intake.sql";
    String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
    String upgrade = Files.readString(root.resolve("docs/sql").resolve(file));
    String pom = Files.readString(root.resolve("bootstrap/pom.xml"));
    for (String table : List.of("talent_mailbox_cursor", "talent_mail_ingest_run",
        "talent_mail_route_rule", "talent_mail_resume")) {
      assertTrue(createdTables(baseline).contains(table));
      assertTrue(upgrade.contains("CREATE TABLE IF NOT EXISTS `" + table + "`"));
    }
    assertTrue(baseline.contains("CONSTRAINT uk_mail_resume_uid UNIQUE (mailbox_key, folder, uid_validity, mail_uid)"));
    assertTrue(baseline.contains("match_type IN ('JD_CODE','CONFIRMED_RULE','EXACT_TITLE_REGION','REVIEW_REQUIRED')"));
    assertTrue(upgrade.contains("information_schema.TABLES"));
    assertTrue(upgrade.contains("TABLE_SCHEMA=DATABASE()"));
    assertTrue(pom.contains("docs/sql/" + file));
    assertTrue(pom.contains("db/migration/V2026091802__mail_resume_intake.sql"));
    assertFalse(upgrade.contains("DELETE FROM"));
    assertFalse(upgrade.contains("DROP TABLE"));
  }

  @Test
  void resumeIntakeOutboxHasAnExplicitNonDestructiveForwardMigration() throws IOException {
    Path root = repositoryRoot();
    String file = "hr_ai_upgrade_20260918_03__resume_intake_outbox.sql";
    String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
    String upgrade = Files.readString(root.resolve("docs/sql").resolve(file));
    String pom = Files.readString(root.resolve("bootstrap/pom.xml"));
    assertTrue(createdTables(baseline).contains("talent_resume_intake_outbox"));
    assertTrue(upgrade.contains("CREATE TABLE IF NOT EXISTS `talent_resume_intake_outbox`"));
    assertTrue(upgrade.contains("TABLE_SCHEMA=DATABASE()"));
    assertTrue(upgrade.contains("information_schema.TABLE_CONSTRAINTS"));
    assertTrue(upgrade.contains("uk_talent_intake_outbox_binding"));
    assertTrue(upgrade.contains("fk_talent_intake_outbox_processing"));
    assertTrue(pom.contains("docs/sql/" + file));
    assertTrue(pom.contains("db/migration/V2026091803__resume_intake_outbox.sql"));
    assertFalse(upgrade.contains("DELETE FROM"));
    assertFalse(upgrade.contains("DROP TABLE"));
    assertFalse(upgrade.contains("UPDATE talent_resume_intake_processing"));
  }

  @Test
  void jdVersionWorkRegionHasAnExplicitHistoricalSnapshotMigration() throws IOException {
    Path root = repositoryRoot();
    String file = "hr_ai_upgrade_20260919_01__jd_version_work_region.sql";
    String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
    String upgrade = Files.readString(root.resolve("docs/sql").resolve(file));
    String pom = Files.readString(root.resolve("bootstrap/pom.xml"));
    assertTrue(baseline.contains("work_region_code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '版本冻结工作区域"));
    assertTrue(upgrade.contains("information_schema.COLUMNS"));
    assertTrue(upgrade.contains("TABLE_SCHEMA=DATABASE()"));
    assertTrue(upgrade.contains("WHERE v.work_region_code IS NULL AND j.work_region_code IS NOT NULL"));
    assertTrue(upgrade.contains("o.kind='REGION'"));
    assertTrue(pom.contains("docs/sql/" + file));
    assertTrue(pom.contains("db/migration/V2026091901__jd_version_work_region.sql"));
    assertFalse(upgrade.contains("DELETE FROM"));
    assertFalse(upgrade.contains("DROP COLUMN"));
    assertFalse(upgrade.contains("SET v.work_region_code=NULL"));
  }

  @Test
  void interviewLabelsHaveAnExplicitNonDestructiveForwardMigration() throws IOException {
    Path root = repositoryRoot();
    String file = "hr_ai_upgrade_20260915_02__interview_labels.sql";
    String upgrade = Files.readString(root.resolve("docs/sql").resolve(file));
    String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
    String pom = Files.readString(root.resolve("bootstrap/pom.xml"));
    assertTrue(pom.contains("docs/sql/" + file));
    assertTrue(pom.contains("db/migration/V2026091502__interview_labels.sql"));
    assertTrue(upgrade.contains("information_schema.COLUMNS"));
    assertTrue(upgrade.contains("information_schema.TABLE_CONSTRAINTS"));
    assertTrue(upgrade.contains("CONSTRAINT_SCHEMA=DATABASE()"));
    assertTrue(baseline.contains("initial_comment                VARCHAR(1000) NULL"));
    assertTrue(baseline.contains("interview_type IN ('INTERNAL', 'EXTERNAL', 'EXTERNAL_CLIENT', 'EXTERNAL_CLIENT_AI')"));
    assertFalse(upgrade.contains("DELETE FROM"));
    assertFalse(upgrade.contains("DROP COLUMN"));
  }

  @Test
  void holidayTypeHasAnAtomicForwardMigrationAndFinalBaseline() throws IOException {
    Path root = repositoryRoot();
    String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
    String upgrade = Files.readString(root.resolve("docs/sql/hr_ai_upgrade_20260915_01__work_calendar_holiday.sql"));
    String pom = Files.readString(root.resolve("bootstrap/pom.xml"));
    assertTrue(baseline.contains("day_type IN ('WORKDAY','RESTDAY','HOLIDAY')"));
    assertTrue(upgrade.contains("CONSTRAINT_SCHEMA=DATABASE()"));
    assertTrue(upgrade.contains("TABLE_SCHEMA=DATABASE()"));
    assertTrue(upgrade.contains("DROP CHECK ck_work_calendar_day_type, ADD CONSTRAINT"));
    assertTrue(upgrade.contains("CHECK_CLAUSE LIKE '%HOLIDAY%'"));
    assertFalse(upgrade.contains("UPDATE talent_work_calendar_day"));
    assertTrue(pom.contains("db/migration/V2026091501__work_calendar_holiday.sql"));
  }

  @Test
  void workCalendarAndLifecycleMigrationsAreForwardOnlyAndRetainConcurrencyConstraints() throws IOException {
    Path root = repositoryRoot();
    String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
    String pom = Files.readString(root.resolve("bootstrap/pom.xml"));
    for (String migration : List.of("20260914_07__work_calendar", "20260914_08__resume_lifecycle")) {
      String file = "hr_ai_upgrade_" + migration + ".sql";
      String upgrade = Files.readString(root.resolve("docs/sql").resolve(file));
      assertTrue(pom.contains("docs/sql/" + file));
      assertTrue(pom.contains("db/migration/V" + migration.replace("20260914_", "20260914") + ".sql"));
      assertTrue(upgrade.contains("information_schema.TABLES"));
      assertTrue(upgrade.contains("TABLE_SCHEMA=DATABASE()"));
      assertFalse(upgrade.contains("DROP TABLE"));
      assertFalse(upgrade.contains("DELETE FROM"));
      assertFalse(upgrade.contains("UPDATE talent_candidate"));
    }
    for (String table : List.of("talent_work_calendar_revision", "talent_work_calendar_year",
        "talent_work_calendar_day", "talent_resume_lifecycle", "talent_resume_lifecycle_runtime",
        "talent_resume_lifecycle_run")) {
      assertTrue(createdTables(baseline).contains(table));
    }
    assertTrue(baseline.contains("UNIQUE KEY uk_work_calendar_revision (calendar_year, revision)"));
    assertTrue(baseline.contains("UNIQUE KEY uk_work_calendar_day (calendar_revision_id, calendar_date)"));
    assertTrue(baseline.contains("UNIQUE KEY uk_resume_lifecycle_cycle (resume_version_id, cycle_no)"));
    assertTrue(baseline.contains("UNIQUE KEY uk_resume_lifecycle_active (resume_version_id, active_marker)"));
    assertTrue(baseline.contains("UNIQUE KEY uk_resume_lifecycle_run (settlement_date, calendar_generation)"));
    assertTrue(baseline.contains("INDEX ix_resume_lifecycle_due (phase, next_check_at, id)"));
    assertTrue(baseline.contains("activated_at DATETIME(6) NULL"), "维护日历不能提前启用存量时钟");
  }

  @Test
  void recruitmentActionSchemaKeepsLegacySourcesAndPackagesForwardMigrations() throws IOException {
    Path root = repositoryRoot();
    String schema = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
    String pom = Files.readString(root.resolve("bootstrap/pom.xml"));
    for (String migration : List.of("20260914_05__recruitment_activity_origin", "20260914_06__application_client_restrictions")) {
      String file = "hr_ai_upgrade_" + migration + ".sql";
      String upgrade = Files.readString(root.resolve("docs/sql").resolve(file));
      assertTrue(pom.contains("docs/sql/" + file));
      assertTrue(pom.contains("db/migration/V" + migration.replace("20260914_", "20260914") + ".sql"));
      assertTrue(upgrade.contains("information_schema.TABLES"));
      assertFalse(upgrade.contains("DELETE FROM"));
    }
    assertTrue(schema.contains("source_submission_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL"));
    assertTrue(schema.contains("source_application_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL"));
    assertTrue(schema.contains("evidence_reference VARCHAR(1000) NULL"));
    assertTrue(schema.contains("fk_restriction_application"));
    assertTrue(schema.contains("uk_activity_origin_source(source_type,source_id)"));
    assertTrue(schema.contains("ix_activity_origin_application(application_id,operation_id)"));
  }

  @Test
  void jdHeadcountBaselineAndUpgradePreserveLegacyRows() throws IOException {
    Path sqlRoot = repositoryRoot().resolve("docs/sql");
    String schema = Files.readString(sqlRoot.resolve(CONSOLIDATED_SCHEMA));
    String upgrade =
        Files.readString(sqlRoot.resolve("hr_ai_upgrade_20260914_01__jd_headcount.sql"));

    assertTrue(schema.contains("headcount INT NULL COMMENT '招聘编制，历史自动来源待 HR 补充'"));
    assertTrue(schema.contains("ck_demand_jd_headcount CHECK (headcount IS NULL OR headcount BETWEEN 1 AND 10000)"));
    assertTrue(upgrade.contains("information_schema.COLUMNS"), "升级必须先检查字段元数据以支持重跑");
    assertTrue(upgrade.contains("information_schema.TABLE_CONSTRAINTS"), "升级必须先检查约束元数据以支持重跑");
    assertFalse(upgrade.matches("(?s).*\\bUPDATE\\s+demand_jd\\b.*"), "升级不得猜测或回填历史 JD 的 HC");
  }

  @Test
  void jdRecruitmentCycleBaselineAndUpgradePreserveLegacyRows() throws IOException {
    Path sqlRoot = repositoryRoot().resolve("docs/sql");
    String schema = Files.readString(sqlRoot.resolve(CONSOLIDATED_SCHEMA));
    String upgrade =
        Files.readString(sqlRoot.resolve("hr_ai_upgrade_20260914_02__jd_recruitment_cycle.sql"));

    assertTrue(schema.contains(
        "recruitment_cycle_days INT NULL COMMENT '招聘周期（天），历史自动来源待 HR 补充'"));
    assertTrue(schema.contains(
        "ck_demand_jd_recruitment_cycle CHECK (recruitment_cycle_days IS NULL OR recruitment_cycle_days BETWEEN 1 AND 3650)"));
    assertTrue(upgrade.contains("information_schema.COLUMNS"), "升级必须先检查字段元数据以支持重跑");
    assertTrue(upgrade.contains("information_schema.TABLE_CONSTRAINTS"), "升级必须先检查约束元数据以支持重跑");
    assertFalse(upgrade.matches("(?s).*\\bUPDATE\\s+demand_jd\\b.*"), "升级不得猜测或回填历史 JD 的招聘周期");
  }

  @Test
  void progressiveFollowUpPreservesUnlimitedRoundsAndIndependentAppointmentFacts() throws IOException {
    String schema = Files.readString(repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));
    String upgrade =
        Files.readString(
            repositoryRoot()
                .resolve(LEGACY_UPGRADE_FIXTURES)
                .resolve("hr_ai_upgrade_20260909_02__progressive_follow_up.sql"));
    assertTrue(schema.contains("ck_interview_round CHECK (round_no >= 0)"), "第四轮及后续轮次必须可保存");
    assertTrue(upgrade.contains("ck_interview_round CHECK (round_no >= 0)"), "旧库升级必须解除三轮限制");
    assertFalse(schema.contains("round_no BETWEEN 0 AND 3"));
    assertTrue(schema.contains("uk_recruitment_application_step_sequence (application_id, sequence_no)"));
    assertFalse(schema.contains("uk_recruitment_application_step (application_id, step_definition_id)"));
    assertTrue(upgrade.contains("IS_NULLABLE='YES'"), "HR 自选动作的无模板来源必须可空");
    assertTrue(schema.contains("progressive_follow_up"));
    assertTrue(schema.contains("result_reason VARCHAR(500) NULL COMMENT '最近完成或取消的实际结果说明'"));
  }

  @Test
  void resumeIntakeBaselineAndUpgradeAcceptPendingImportWithoutDataRewrite() throws IOException {
    Path sqlRoot = repositoryRoot().resolve("docs/sql");
    String schema = Files.readString(sqlRoot.resolve(CONSOLIDATED_SCHEMA));
    String upgrade = Files.readString(
        repositoryRoot()
            .resolve(LEGACY_UPGRADE_FIXTURES)
            .resolve("hr_ai_upgrade_20260909_05__resume_intake_pending_import.sql"));
    assertTrue(schema.contains("'PENDING_IMPORT'"));
    assertTrue(upgrade.contains("PENDING_IMPORT"));
    assertTrue(upgrade.contains("information_schema.CHECK_CONSTRAINTS"));
    assertTrue(upgrade.contains("DROP CHECK ck_talent_intake_status"));
    assertFalse(upgrade.matches("(?s).*\\bUPDATE\\s+talent_resume_intake\\b.*"));
  }

  @Test
  void consolidatedSchemaContainsOnlyFinalTableDefinitionsWithStandardColumns() throws IOException {
    Path sqlRoot = repositoryRoot().resolve("docs/sql");
    String consolidated = Files.readString(sqlRoot.resolve(CONSOLIDATED_SCHEMA)).toLowerCase();

    assertFalse(
        Pattern.compile("(?im)^\\s*(?:alter\\s+table|update\\s+)").matcher(consolidated).find(),
        "最终态建库脚本不得包含升级 ALTER 或数据回填 UPDATE");
    assertFalse(
        consolidated.contains("add column") || consolidated.contains("modify column"),
        "最终态建库脚本的字段必须直接位于 CREATE TABLE 中");
    assertFalse(
        Pattern.compile(
                "(?i)(?<![a-z0-9_])(?:created_by_user_id|created_at|updated_by_user_id|updated_at|created_by|updated_by)(?![a-z0-9_])")
            .matcher(consolidated)
            .find(),
        "最终态建库脚本不得保留与统一字段重复的旧审计列");

    Set<String> consolidatedTables = createdTables(consolidated);
    assertEquals(177, consolidatedTables.size(), "最终态建库脚本必须完整包含 177 张表（含 JD 岗位名称建议、部门码映射、入职流程、显式重评绑定、AI 专业评分、邮件采集、企业微信面试日程投影、收件 Outbox 及搜索资源账本）");
    assertTrue(consolidatedTables.contains("search_index_resource"), "搜索创建、请求保护和回收必须有持久资源账本");
    assertTrue(consolidatedTables.contains("matching_ai_professional_assessment"), "冻结事实快照的 AI 专业评分必须独立持久化");
    assertTrue(consolidatedTables.contains("recruitment_follow_up_plan"), "沟通预约必须独立于正式面试持久化");
    assertTrue(consolidatedTables.contains("recruitment_follow_up_plan_history"), "沟通预约改期取消必须保留审计");
    assertTrue(consolidatedTables.contains("demand_jd_source"), "JD 来源必须独立保存来源及收件归属");
    assertTrue(consolidatedTables.contains("reporting_search_task_recovery"), "人工恢复必须保留独立关联记录");
    assertTrue(consolidatedTables.contains("search_state_replay_checkpoint"), "当前状态回放必须有独立持久进度表");

    List<String> violations = new ArrayList<>();
    for (String table : consolidatedTables) {
      Matcher createTable =
          Pattern.compile(
                  "(?is)create\\s+table\\s+`?"
                      + Pattern.quote(table)
                      + "`?\\s*\\((.*?)\\)\\s*(?:engine|comment\\s*=|;)")
              .matcher(consolidated);
      if (!createTable.find()) {
        violations.add(table + " -> 无法解析 CREATE TABLE");
        continue;
      }
      String definition = createTable.group(1);
      for (Map.Entry<String, String> column : STANDARD_COLUMN_PATTERNS.entrySet()) {
        if (!Pattern.compile("(?is)(?:^|,)\\s*" + column.getValue() + "(?:\\s*,|$)")
            .matcher(definition)
            .find()) {
          violations.add(table + " -> " + column.getKey());
        }
      }
    }

    assertTrue(
        violations.isEmpty(), () -> "最终态建库脚本中的表缺少标准字段或定义不规范:\n" + String.join("\n", violations));
  }

  @Test
  void consolidatedSchemaDeclaresUtf8mb4CharsetAndCollationForEveryTable() throws IOException {
    String consolidated =
        Files.readString(repositoryRoot().resolve("docs/sql").resolve(CONSOLIDATED_SCHEMA));
    Set<String> expectedTables = createdTables(consolidated);
    Matcher tables =
        Pattern.compile(
                "(?ims)^\\s*create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?"
                    + "`?([a-z0-9_]+)`?\\s*\\(([^;]*);")
            .matcher(consolidated);
    List<String> violations = new ArrayList<>();
    Set<String> inspectedTables = new HashSet<>();
    while (tables.find()) {
      String table = tables.group(1).toLowerCase();
      String definition = tables.group(2);
      inspectedTables.add(table);
      if (!Pattern.compile("(?i)engine\\s*=\\s*innodb").matcher(definition).find()
          || !Pattern.compile("(?i)default\\s+charset\\s*=\\s*utf8mb4")
              .matcher(definition)
              .find()
          || !Pattern.compile("(?i)collate\\s*=\\s*utf8mb4_0900_ai_ci")
              .matcher(definition)
              .find()) {
        violations.add(table);
      }
    }
    assertEquals(expectedTables, inspectedTables, "字符集门禁必须检查全部 CREATE TABLE 语句");
    assertTrue(
        violations.isEmpty(),
        () -> "每张表必须显式声明 utf8mb4/utf8mb4_0900_ai_ci:\n" + String.join("\n", violations));
  }

  @Test
  void matchingSchemaKeepsFiveProfessionalDimensionsAndFiveControlledFacts() throws IOException {
    String migration =
        Files.readString(repositoryRoot().resolve("docs/sql").resolve(CONSOLIDATED_SCHEMA));

    assertTrue(
        Pattern.compile(
                "criterion_code\\s+IN\\s*\\(\\s*'AGE'\\s*,\\s*'GENDER'\\s*,\\s*'EDUCATION'\\s*,\\s*'CERTIFICATE'\\s*,\\s*'LANGUAGE'\\s*\\)",
                Pattern.CASE_INSENSITIVE)
            .matcher(migration)
            .find(),
        "正式评分配置必须允许五类受控条件");
    assertFalse(migration.contains("education_weight"), "学历不得成为专业维度权重列");
    assertFalse(migration.contains("credential_language_weight"), "证书与语言不得成为专业维度权重列");
    assertFalse(migration.contains("education_enabled"), "学历不得成为专业维度开关");
    assertFalse(migration.contains("credential_language_enabled"), "证书与语言不得成为专业维度开关");
  }

  @Test
  void schemaTablesAndColumnsHaveChineseComments() throws IOException {
    List<String> violations = new ArrayList<>();
    String currentTable = null;
    for (String line :
        Files.readAllLines(repositoryRoot().resolve("docs/sql").resolve(CONSOLIDATED_SCHEMA))) {
      Matcher tableStart =
          Pattern.compile("(?i)^\\s*create\\s+table\\s+`?([a-z0-9_]+)`?\\s*\\(").matcher(line);
      if (tableStart.find()) {
        currentTable = tableStart.group(1);
        continue;
      }
      if (currentTable == null) {
        continue;
      }

      Matcher column = SQL_COLUMN_DEFINITION.matcher(line);
      if (column.find()
          && (!line.toLowerCase().contains(" comment '") || !CHINESE_TEXT.matcher(line).find())) {
        violations.add(currentTable + "." + column.group(1) + " -> 缺少中文 COMMENT");
      }
      if (line.stripLeading().startsWith(")") && line.stripTrailing().endsWith(";")) {
        String lowerLine = line.toLowerCase();
        if (!lowerLine.contains("comment='") || !CHINESE_TEXT.matcher(line).find()) {
          violations.add(currentTable + " -> 缺少表级中文 COMMENT");
        }
        currentTable = null;
      }
    }

    assertTrue(
        violations.isEmpty(), () -> "最终态建库脚本中的每张表和每个字段都必须有中文说明:\n" + String.join("\n", violations));
  }

  @Test
  void currentDocumentationKeepsTheFiveDimensionBaseline() throws IOException {
    Path root = repositoryRoot();
    String detail = Files.readString(root.resolve("docs/HR_AI招聘赋能系统_开发细节设计.md"));
    String scoringDiagram =
        Files.readString(root.resolve("docs/diagrams/architecture/06-scoring-version-model.puml"));
    String coreDiagram =
        Files.readString(root.resolve("docs/diagrams/architecture/05-core-domain-model.puml"));
    String cost = Files.readString(root.resolve("docs/上线资源与成本估算.md"));

    assertTrue(
        detail.contains("| 核心技能 | 20% |")
            && detail.contains("| 岗位经历 | 20% |")
            && detail.contains("| 相关年限 | 20% |")
            && detail.contains("| 职责匹配 | 20% |")
            && detail.contains("| 行业与项目 | 20% |"),
        "开发细节必须使用 20/20/20/20/20 五维默认权重");
    assertFalse(
        detail.contains("| 学历 | 10% |") || detail.contains("| 证书与语言 | 5% |"),
        "学历、证书、语言不得出现在专业维度权重表");
    assertTrue(
        scoringDiagram.contains("五维专业适配")
            && scoringDiagram.contains("默认权重20/20/20/20/20")
            && scoringDiagram.contains("fiveDimensionScores"),
        "评分架构图必须表达五维模型");
    assertFalse(scoringDiagram.contains("七维") || coreDiagram.contains("七维"), "当前架构图不得保留七维表述");
    assertTrue(
        cost.contains("每一组冻结的“JD 事实快照 + 简历事实快照 + 提示词版本 + 结果结构版本”调用一次模型")
            && cost.contains("只由 Java 复用已保存的五维原始分重新加权，不重复调用模型"),
        "成本口径必须区分一次 AI 专业评分与后续 Java 权重重算");
  }

  @Test
  void mutableOwnedTablesHaveCreatorAndUpdaterMetadata() throws IOException {
    Path sqlRoot = repositoryRoot().resolve("docs/sql");
    String migrations = Files.readString(sqlRoot.resolve(CONSOLIDATED_SCHEMA)).toLowerCase();

    List<String> violations = new ArrayList<>();
    for (String table : MUTABLE_AUDITED_TABLES) {
      Pattern statements =
          Pattern.compile(
              "(?is)(?:create\\s+table(?:\\s+if\\s+not\\s+exists)?\\s+`?"
                  + Pattern.quote(table)
                  + "`?\\s*\\(.*?;|alter\\s+table\\s+`?"
                  + Pattern.quote(table)
                  + "`?.*?;)");
      Matcher matcher = statements.matcher(migrations);
      StringBuilder definition = new StringBuilder();
      while (matcher.find()) {
        definition.append(matcher.group()).append('\n');
      }
      String sql = definition.toString();
      boolean complete =
          sql.contains("create_id")
              && sql.contains("create_time")
              && sql.contains("update_id")
              && sql.contains("update_time");
      if (!complete) {
        violations.add(table);
      }
    }

    assertTrue(
        violations.isEmpty(),
        () -> "可变业务表必须具备创建人/创建时间/更新人/更新时间:\n" + String.join("\n", violations));
  }

  @Test
  void lombokManagedTypesUseGeneratedAccessors() throws IOException {
    Path root = repositoryRoot();
    List<String> redundantAccessors = new ArrayList<>();

    try (Stream<Path> paths = JavaImportPolicy.repositorySources(root).keySet().stream()) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .filter(path -> containsSegment(path, "src"))
          .filter(path -> containsSegment(path, "main"))
          .filter(path -> !containsSegment(path, "target"))
          .filter(path -> !containsSegment(path, ".git"))
          .filter(path -> !containsSegment(path, ".planning"))
          .filter(path -> !containsSegment(path, ".runtime"))
          .forEach(
              path -> {
                String source = readUnchecked(path);
                if (!LOMBOK_ACCESSOR_ANNOTATION.matcher(source).find()) {
                  return;
                }
                Matcher accessor = REDUNDANT_LOMBOK_ACCESSOR.matcher(source);
                while (accessor.find()) {
                  redundantAccessors.add(root.relativize(path) + " -> " + accessor.group(1) + "()");
                }
              });
    }

    assertTrue(
        redundantAccessors.isEmpty(),
        () ->
            "Lombok 已生成 JavaBean 访问器，禁止保留重复访问器或用默认值掩盖 null:\n"
                + String.join("\n", redundantAccessors));
  }

  @Test
  void javaSourcesDoNotContainGeneratedPlaceholderJavadoc() throws IOException {
    Path root = repositoryRoot();
    List<String> violations = new ArrayList<>();

    try (Stream<Path> paths = JavaImportPolicy.repositorySources(root).keySet().stream()) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .filter(path -> containsSegment(path, "src"))
          .filter(path -> containsSegment(path, "main"))
          .filter(path -> !containsSegment(path, "target"))
          .filter(path -> !containsSegment(path, ".git"))
          .filter(path -> !containsSegment(path, ".planning"))
          .filter(path -> !containsSegment(path, ".runtime"))
          .filter(path -> containsGeneratedOrMangledJavadoc(readUnchecked(path)))
          .forEach(path -> violations.add(root.relativize(path).toString()));
    }

    assertTrue(
        violations.isEmpty(), () -> "禁止使用占位或词语残缺的生成式 JavaDoc:\n" + String.join("\n", violations));
  }

  @Test
  void entitiesKeepReviewableFormatting() throws IOException {
    Path root = repositoryRoot();
    List<String> formattingViolations = new ArrayList<>();

    try (Stream<Path> paths = JavaImportPolicy.repositorySources(root).keySet().stream()) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .filter(path -> containsSegment(path, "src"))
          .filter(path -> containsSegment(path, "main"))
          .filter(path -> containsSegment(path, "domain"))
          .filter(path -> containsSegment(path, "entity"))
          .filter(path -> !containsSegment(path, "target"))
          .filter(path -> !containsSegment(path, ".git"))
          .filter(path -> !containsSegment(path, ".planning"))
          .filter(path -> !containsSegment(path, ".runtime"))
          .filter(
              path ->
                  EXCESSIVE_ENTITY_BLANK_LINES
                      .matcher(readUnchecked(path).replace("\r\n", "\n").replace('\r', '\n'))
                      .find())
          .forEach(path -> formattingViolations.add(root.relativize(path).toString()));
    }

    assertTrue(
        formattingViolations.isEmpty(),
        () -> "Entity 禁止连续多行空白，保持可审阅格式:\n" + String.join("\n", formattingViolations));
  }

  @Test
  void optimisticCompareAndSetUpdatesAdvanceTheVersionToken() throws IOException {
    Path root = repositoryRoot();
    List<String> violations = new ArrayList<>();

    try (Stream<Path> paths = JavaImportPolicy.repositorySources(root).keySet().stream()) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .filter(path -> containsSegment(path, "src"))
          .filter(path -> containsSegment(path, "main"))
          .filter(path -> !containsSegment(path, "target"))
          .filter(path -> !containsSegment(path, ".git"))
          .filter(path -> !containsSegment(path, ".planning"))
          .filter(path -> !containsSegment(path, ".runtime"))
          .forEach(
              path -> {
                String[] statements = readUnchecked(path).split(";", -1);
                int statementStartLine = 1;
                for (String statement : statements) {
                  if (!statement.contains("lambdaUpdate")) {
                    statementStartLine += newlineCount(statement);
                    continue;
                  }
                  Matcher versionPredicate = VERSION_CAS_PREDICATE.matcher(statement);
                  while (versionPredicate.find()) {
                    String entityName = versionPredicate.group(1);
                    Pattern increment =
                        Pattern.compile(
                            "\\.setIncrBy\\(\\s*"
                                + Pattern.quote(entityName)
                                + "::getVersion\\s*,\\s*1(?:L)?\\s*\\)");
                    if (!increment.matcher(statement).find()) {
                      violations.add(
                          root.relativize(path)
                              + ":"
                              + (statementStartLine
                                  + newlineCount(statement.substring(0, versionPredicate.start())))
                              + " ("
                              + entityName
                              + ")");
                    }
                  }
                  statementStartLine += newlineCount(statement);
                }
              });
    }

    assertTrue(
        violations.isEmpty(),
        () ->
            "使用 version 条件执行 CAS 更新时必须在同一 SQL 中递增版本号:\n"
                + String.join("\n", violations));
  }

  @Test
  void queryWrappersDoNotConcatenateSqlFragments() throws IOException {
    Path root = repositoryRoot();
    List<String> violations = new ArrayList<>();

    try (Stream<Path> paths = JavaImportPolicy.repositorySources(root).keySet().stream()) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .filter(path -> containsSegment(path, "src"))
          .filter(path -> containsSegment(path, "main"))
          .filter(path -> !containsSegment(path, "target"))
          .filter(path -> !containsSegment(path, ".git"))
          .filter(path -> !containsSegment(path, ".planning"))
          .filter(path -> !containsSegment(path, ".runtime"))
          .forEach(
              path -> {
                String source = readUnchecked(path);
                Matcher matcher = CONCATENATED_SQL_WRAPPER_FRAGMENT.matcher(source);
                while (matcher.find()) {
                  violations.add(
                      root.relativize(path)
                          + ":"
                          + (1 + newlineCount(source.substring(0, matcher.start()))));
                }
              });
    }

    assertTrue(
        violations.isEmpty(),
        () ->
            "MyBatis 查询包装器禁止拼接 SQL 片段；动态分页必须使用参数化 Page API:\n"
                + String.join("\n", violations));
  }

  private static int newlineCount(String value) {
    return Math.toIntExact(value.chars().filter(character -> character == '\n').count());
  }

  private static String readUnchecked(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException exception) {
      throw new IllegalStateException("读取源码文件失败: " + path, exception);
    }
  }

  private static boolean containsGeneratedOrMangledJavadoc(String source) {
    Matcher comments = JAVADOC_BLOCK.matcher(source);
    while (comments.find()) {
      if (GENERATED_OR_MANGLED_JAVADOC.matcher(comments.group()).find()) {
        return true;
      }
    }
    return false;
  }

  private static Set<String> createdTables(String sql) {
    Set<String> tables = new HashSet<>();
    Matcher matcher =
        Pattern.compile(
                "(?im)^\\s*create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?`?([a-z0-9_]+)`?\\s*\\(")
            .matcher(sql);
    while (matcher.find()) {
      tables.add(matcher.group(1).toLowerCase());
    }
    return tables;
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

  private static boolean containsSegment(Path path, String segment) {
    for (Path part : path) {
      if (part.toString().equals(segment)) {
        return true;
      }
    }
    return false;
  }

  private static String join(List<Path> paths) {
    return paths.stream()
        .map(Path::toString)
        .sorted()
        .reduce((left, right) -> left + "\n" + right)
        .orElse("");
  }
}
