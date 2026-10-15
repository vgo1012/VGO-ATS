package com.ezaigc.hr.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MonthlyReportSchemaLayoutContractTest {

  @Test
  void finalSchemaMapsMonthlyReportEntitiesAndLifecycleIndexes() throws IOException {
    String sql = normalized(schema());

    assertThat(sql)
        .contains("create table `reporting_monthly_report`")
        .contains("task_id char(26) character set ascii collate ascii_bin null")
        .contains("management_remarks varchar(2000)")
        .contains("snapshot_json longtext")
        .contains("version int not null default 0 comment '技术乐观锁版本号'")
        .contains("uk_reporting_monthly_report_request (owner_user_id, idempotency_key)")
        .contains("ix_reporting_monthly_report_history (create_time desc, id desc)")
        .contains("ix_reporting_monthly_report_month_history (report_month, create_time desc, id desc)")
        .contains("ix_reporting_monthly_report_active (status, create_time, id)")
        .contains("'uploading', 'validating', 'invalid', 'ready', 'queued', 'succeeded', 'failed', 'cancelled', 'deleted'")
        .contains("'upload', 'validate', 'generate'")
        .contains("create table `reporting_monthly_report_file`")
        .contains("file_name varchar(200)")
        .contains("unique key uk_reporting_monthly_report_file_object (object_key)")
        .contains("ix_reporting_monthly_report_file_kind (report_id, file_kind)")
        .contains("ix_reporting_monthly_report_file_expiry (deleted, expires_at, id)")
        .contains("foreign key (report_id) references reporting_monthly_report (id) on delete restrict")
        .contains("'offer', 'demand', 'report_temp', 'report'");
  }

  @Test
  void baselineAndForwardUpgradeGrantMonthlyReportOnlyToHrDirector() throws IOException {
    String baseline = normalized(schema());
    String upgrade = normalized(repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20260914_03__monthly_report.sql"));
    String bootstrapPom = Files.readString(repositoryRoot().resolve("bootstrap/pom.xml"));

    assertThat(baseline)
        .contains("('reporting.monthly-report.manage','管理招聘月度汇报','reporting'")
        .contains("where r.code = 'hr_director' and p.code = 'reporting.monthly-report.manage'");
    assertThat(upgrade)
        .contains("information_schema.tables", "information_schema.columns", "information_schema.statistics",
            "information_schema.table_constraints", "information_schema.referential_constraints", "prepare", "execute", "deallocate")
        .contains("comment ''技术乐观锁版本号''", "comment ''主键 id''", "comment ''创建时间''")
        .contains("count(distinct index_name)", "delete_rule='restrict'",
            "existing reporting_monthly_report has an incompatible definition",
            "existing reporting_monthly_report_file has an incompatible definition")
        .contains("where r.code='hr_director' and p.code='reporting.monthly-report.manage'")
        .doesNotContain("drop table", "drop column", "truncate");
    assertThat(countOccurrences(upgrade, "comment ''")).isEqualTo(46);
    assertThat(bootstrapPom).contains("V2026091403__monthly_report.sql");
  }

  @Test
  void forwardUpgradeMetadataUsesSqlLiteralsOutsideDynamicDdl() throws IOException {
    String upgrade = normalized(repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20260914_03__monthly_report.sql"));
    var checks = Pattern.compile("if\\(\\(select count\\(\\*\\) from information_schema\\.columns.*?(?='do 0')")
        .matcher(upgrade).results().map(result -> result.group()).toList();

    assertThat(checks).hasSize(2).allSatisfy(check -> assertThat(check)
        .contains("delete_rule='restrict'", "index_name,':'", "separator '|'")
        .doesNotContain("''"));
    assertThat(checks.getFirst())
        .contains("fk_reporting_monthly_report_task:1:btree:task_id|")
        .contains("table_name='reporting_monthly_report')=6");
  }

  @Test
  void deletionUpgradeAtomicallyExtendsStatusWithoutRemovingHistory() throws IOException {
    String upgrade = normalized(repositoryRoot().resolve(
        "docs/sql/hr_ai_upgrade_20260914_04__monthly_report_delete.sql"));

    assertThat(upgrade)
        .contains("database() is not null", "table_schema=database()", "constraint_schema=database()")
        .contains("information_schema.tables", "information_schema.columns", "information_schema.table_constraints")
        .contains("column_name='status'", "data_type='varchar'", "character_maximum_length=20",
            "is_nullable='no'", "collation_name='ascii_bin'", "constraint_type<>'check'")
        .contains("concat('alter table reporting_monthly_report '")
        .contains("'drop check ck_reporting_monthly_report_status, ', ''")
        .contains("add constraint ck_reporting_monthly_report_status check (status in")
        .contains("''cancelled'', ''deleted''", "prepare", "execute", "deallocate")
        .doesNotContain("delete from", "drop table", "drop column", "truncate", "update reporting_monthly_report");
    assertThat(countOccurrences(upgrade, "alter table reporting_monthly_report")).isEqualTo(1);
  }

  @Test
  void packagedForwardMigrationsHaveDistinctVersions() throws IOException {
    String bootstrapPom = Files.readString(repositoryRoot().resolve("bootstrap/pom.xml"));
    var versions = Pattern.compile("/V(\\d+)__[^\"]+\\.sql")
        .matcher(bootstrapPom).results().map(result -> result.group(1)).toList();

    assertThat(versions).containsExactly("2026091201", "2026091401", "2026091402", "2026091403", "2026091404", "2026091405", "2026091406", "2026091407", "2026091408", "2026091501", "2026091502", "2026091601", "2026091701", "2026091702", "2026091703", "2026091704", "2026091705", "2026091706", "2026091801", "2026091802", "2026091803", "2026091901", "2026092001", "2026092101", "2026092102", "2026092103", "2026092201", "2026092202", "2026092301", "2026092302", "2026092401", "2026092801", "2026093001", "2026100801", "2026100802", "2026100803", "2026101001")
        .doesNotHaveDuplicates();
  }

  private static int countOccurrences(String value, String token) {
    int count = 0;
    for (int offset = value.indexOf(token); offset >= 0; offset = value.indexOf(token, offset + token.length())) {
      count++;
    }
    return count;
  }

  private static Path schema() {
    return repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql");
  }

  private static String normalized(Path path) throws IOException {
    return Files.readString(path).toLowerCase(Locale.ROOT).replaceAll("--[^\\r\\n]*", " ")
        .replaceAll("\\s+", " ").trim();
  }

  private static Path repositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("docs/sql"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("无法定位仓库根目录");
  }
}
