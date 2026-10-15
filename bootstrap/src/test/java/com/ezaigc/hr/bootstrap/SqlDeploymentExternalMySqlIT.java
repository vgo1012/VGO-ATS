package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.identity.mapper.IdentityAccountMapper;
import com.ezaigc.hr.identity.service.impl.IdentityAccountPersistenceServiceImpl;
import com.ezaigc.hr.recruiting.demand.mapper.JdEntityMapper;
import com.ezaigc.hr.recruiting.demand.mapper.JdCodeOptionMapper;
import com.ezaigc.hr.recruiting.demand.service.impl.JdEntityPersistenceServiceImpl;
import com.ezaigc.hr.recruiting.demand.service.impl.JdCodeOptionPersistenceServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.mapper.ApplicationMapper;
import com.ezaigc.hr.recruiting.recruitment.service.impl.ApplicationPersistenceServiceImpl;
import com.ezaigc.hr.reporting.mapper.AsyncTaskMapper;
import com.ezaigc.hr.intelligence.search.mapper.SearchIndexResourceMapper;
import com.ezaigc.hr.intelligence.search.enums.SearchIndexResourceStatus;
import com.ezaigc.hr.reporting.service.impl.AsyncTaskServiceImpl;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.init.ScriptException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** 在显式配置的外部测试服务中创建独立临时数据库，验证部署脚本，不改业务库。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
class SqlDeploymentExternalMySqlIT {
  @Test
  void loginProtectionUpgradePreservesPartialAndExistingAccountState() throws Exception {
    Path upgrade = repositoryRoot().resolve(
        "docs/sql/hr_ai_upgrade_20260928_01__login_protection.sql");
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.execute("CREATE TABLE identity_user_account (id CHAR(26) PRIMARY KEY, "
          + "normalized_email VARCHAR(320) NOT NULL, credential_hash VARCHAR(255) NOT NULL) ENGINE=InnoDB");
      jdbc.update("INSERT INTO identity_user_account VALUES ('account','user@example.com','synthetic-hash')");
      jdbc.execute("ALTER TABLE identity_user_account ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0");
      jdbc.update("UPDATE identity_user_account SET failed_login_count=3 WHERE id='account'");

      executeScript(connection, upgrade);
      assertThat(jdbc.queryForObject("SELECT failed_login_count FROM identity_user_account "
          + "WHERE id='account'", Integer.class)).isEqualTo(3);
      jdbc.update("UPDATE identity_user_account SET failed_login_window_started_at=?, "
          + "login_locked_until=? WHERE id='account'",
          Timestamp.from(Instant.parse("2026-09-28T00:00:00Z")),
          Timestamp.from(Instant.parse("2026-09-28T00:15:00Z")));
      var state = jdbc.queryForMap("SELECT id,normalized_email,credential_hash,failed_login_count,"
          + "failed_login_window_started_at,login_locked_until FROM identity_user_account");
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForMap("SELECT id,normalized_email,credential_hash,failed_login_count,"
          + "failed_login_window_started_at,login_locked_until FROM identity_user_account"))
          .isEqualTo(state);
    });
    withDatabase(connection -> {
      Flyway.configure().dataSource(new SingleConnectionDataSource(connection, true))
          .locations("classpath:db/migration").load().migrate();
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS "
          + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='identity_user_account' "
          + "AND COLUMN_NAME IN ('failed_login_count','failed_login_window_started_at',"
          + "'login_locked_until')", Integer.class)).isEqualTo(3);
    });
    withDatabase(connection -> {
      executeScript(connection, repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS "
          + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='identity_user_account' "
          + "AND COLUMN_NAME IN ('failed_login_count','failed_login_window_started_at',"
          + "'login_locked_until')", Integer.class)).isEqualTo(3);
    });
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.execute("CREATE TABLE identity_user_account (id CHAR(26) PRIMARY KEY, "
          + "normalized_email VARCHAR(320) NOT NULL, credential_hash VARCHAR(255) NOT NULL, "
          + "failed_login_count BIGINT NOT NULL DEFAULT 0) ENGINE=InnoDB");
      assertThatThrownBy(() -> executeScript(connection, upgrade))
          .isInstanceOf(ScriptException.class);
      assertThat(jdbc.queryForObject("SELECT DATA_TYPE FROM information_schema.COLUMNS "
          + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='identity_user_account' "
          + "AND COLUMN_NAME='failed_login_count'", String.class)).isEqualTo("bigint");
    });
  }

  @Test
  void loginAccountRowLockSerializesConcurrentFailures() throws Exception {
    withDatabase(connection -> {
      var source = new DriverManagerDataSource(connection.getMetaData().getURL(),
          requiredEnvironment("HR_DB_USER"), requiredEnvironment("HR_DB_PASSWORD"));
      var jdbc = new JdbcTemplate(source);
      jdbc.execute("CREATE TABLE identity_user_account (id CHAR(26) PRIMARY KEY, "
          + "display_name VARCHAR(100), work_email VARCHAR(320), "
          + "normalized_email VARCHAR(320) NOT NULL UNIQUE, credential_hash VARCHAR(255) NOT NULL "
          + "DEFAULT 'synthetic', source_title VARCHAR(100), home_department_id CHAR(26), "
          + "direct_leader_user_id CHAR(26), status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', "
          + "must_change_password BOOLEAN NOT NULL DEFAULT FALSE, "
          + "registered_at TIMESTAMP(6) NULL, credential_reset_at TIMESTAMP(6) NULL, "
          + "disabled_at TIMESTAMP(6) NULL, version INT NOT NULL DEFAULT 0, ext TEXT, "
          + "remark VARCHAR(255), create_id CHAR(26), create_name VARCHAR(64), "
          + "create_time TIMESTAMP(6) NULL, update_id CHAR(26), update_name VARCHAR(64), "
          + "update_time TIMESTAMP(6) NULL, "
          + "failed_login_count INT NOT NULL DEFAULT 0, "
          + "failed_login_window_started_at TIMESTAMP(6) NULL, login_locked_until TIMESTAMP(6) NULL) ENGINE=InnoDB");
      jdbc.update("INSERT INTO identity_user_account(id,normalized_email) VALUES ('account','user@example.com')");
      jdbc.execute("CREATE TABLE identity_user_account_history (user_id CHAR(26) NOT NULL, "
          + "change_type VARCHAR(40) NOT NULL) ENGINE=InnoDB");
      jdbc.execute("CREATE TABLE identity_role (id CHAR(26) PRIMARY KEY, code VARCHAR(64) NOT NULL, "
          + "status VARCHAR(16) NOT NULL) ENGINE=InnoDB");
      jdbc.execute("CREATE TABLE identity_user_role (user_id CHAR(26) NOT NULL, "
          + "role_id CHAR(26) NOT NULL) ENGINE=InnoDB");
      var configuration = new MybatisConfiguration();
      configuration.setEnvironment(new Environment("login-lock", new SpringManagedTransactionFactory(), source));
      configuration.addMapper(IdentityAccountMapper.class);
      var service = new IdentityAccountPersistenceServiceImpl();
      ReflectionTestUtils.setField(service, "baseMapper",
          new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration))
              .getMapper(IdentityAccountMapper.class));
      assertThat(service.isUsableBootstrappedSuperAdmin("account")).isFalse();
      jdbc.update("INSERT INTO identity_user_account_history VALUES ('account','BOOTSTRAPPED')");
      jdbc.update("INSERT INTO identity_role VALUES ('role','SUPER_ADMIN','ACTIVE')");
      jdbc.update("INSERT INTO identity_user_role VALUES ('account','role')");
      assertThat(service.isUsableBootstrappedSuperAdmin("account")).isTrue();
      jdbc.update("UPDATE identity_role SET status='DISABLED' WHERE id='role'");
      assertThat(service.isUsableBootstrappedSuperAdmin("account")).isFalse();
      jdbc.update("UPDATE identity_role SET status='ACTIVE' WHERE id='role'");
      jdbc.update("UPDATE identity_user_account SET status='DISABLED' WHERE id='account'");
      assertThat(service.isUsableBootstrappedSuperAdmin("account")).isFalse();
      jdbc.update("UPDATE identity_user_account SET status='ACTIVE' WHERE id='account'");
      jdbc.update("INSERT INTO identity_user_account(id,normalized_email) "
          + "VALUES ('successor','successor@example.com')");
      jdbc.update("INSERT INTO identity_user_role VALUES ('successor','role')");
      jdbc.update("INSERT INTO identity_user_account(id,normalized_email) "
          + "VALUES ('zzzz','later@example.com')");
      jdbc.update("INSERT INTO identity_user_role VALUES ('zzzz','role')");
      assertThat(service.isFallbackEmergencySuperAdmin("successor")).isFalse();
      jdbc.update("DELETE FROM identity_user_role WHERE user_id='account'");
      assertThat(service.isFallbackEmergencySuperAdmin("successor")).isTrue();
      assertThat(service.isFallbackEmergencySuperAdmin("zzzz")).isFalse();
      assertThat(service.isFallbackEmergencySuperAdmin("account")).isFalse();
      jdbc.update("DELETE FROM identity_user_role WHERE user_id='successor'");
      assertThat(service.isFallbackEmergencySuperAdmin("zzzz")).isTrue();
      jdbc.update("INSERT INTO identity_user_role VALUES ('account','role')");
      assertThat(service.isFallbackEmergencySuperAdmin("zzzz")).isFalse();
      assertThat(service.clearLoginFailures("account")).isEqualTo(1);
      var transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
      transactions.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
      var firstLocked = new CountDownLatch(1);
      var releaseFirst = new CountDownLatch(1);
      var secondStarted = new CountDownLatch(1);
      try (var executor = Executors.newFixedThreadPool(2)) {
        Future<Integer> first = executor.submit(() -> transactions.execute(status -> {
          var account = service.lockLoginAccount("user@example.com");
          assertThat(account.getNormalizedEmail()).isEqualTo("user@example.com");
          firstLocked.countDown();
          try {
            assertThat(releaseFirst.await(10, TimeUnit.SECONDS)).isTrue();
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
          }
          assertThat(service.updateLoginFailure(account.getId(), account.getFailedLoginCount() + 1,
              Instant.parse("2026-09-28T00:00:00Z"), null)).isEqualTo(1);
          return account.getFailedLoginCount() + 1;
        }));
        assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();
        Future<Integer> second = executor.submit(() -> transactions.execute(status -> {
          secondStarted.countDown();
          var account = service.lockLoginAccount("user@example.com");
          assertThat(service.updateLoginFailure(account.getId(), account.getFailedLoginCount() + 1,
              Instant.parse("2026-09-28T00:00:00Z"), null)).isEqualTo(1);
          return account.getFailedLoginCount() + 1;
        }));
        assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);
        releaseFirst.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(2);
      } finally {
        releaseFirst.countDown();
      }
      assertThat(jdbc.queryForObject("SELECT failed_login_count FROM identity_user_account "
          + "WHERE id='account'", Integer.class)).isEqualTo(2);
      jdbc.update("UPDATE identity_user_account SET login_locked_until='2030-01-01 00:00:00' "
          + "WHERE id='account'");
      assertThat(transactions.<Integer>execute(status -> service.updateCredential("account",
          "new-hash", true, "actor", Instant.parse("2026-09-28T01:00:00Z")))).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT failed_login_count FROM identity_user_account "
          + "WHERE id='account'", Integer.class)).isZero();
      assertThat(jdbc.queryForObject("SELECT failed_login_window_started_at IS NULL "
          + "AND login_locked_until IS NULL FROM identity_user_account WHERE id='account'",
          Boolean.class)).isTrue();
      assertThat(jdbc.queryForObject("SELECT credential_hash FROM identity_user_account "
          + "WHERE id='account'", String.class)).isEqualTo("new-hash");
    });
  }

  @Test
  void legacyJdEvidenceJsonUpgradeRemovesOnlyDeprecatedFieldsAndSupportsReruns() throws Exception {
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.execute("CREATE TABLE demand_jd_fact_snapshot(id VARCHAR(26) PRIMARY KEY, facts_json JSON NOT NULL)");
      jdbc.update("INSERT INTO demand_jd_fact_snapshot(id,facts_json) VALUES"
          + "('legacy-jd',CAST(? AS JSON)),('current-jd',CAST(? AS JSON))",
          "{\"professional\":{\"coreSkills\":[\"Java\"]},\"evidence\":[{\"quote\":\"旧 JD 摘录\"}],"
              + "\"confidence\":0.8,\"reliability\":\"MEDIUM\"}",
          "{\"professional\":{\"coreSkills\":[\"Go\"]}}");
      Path upgrade = repositoryRoot().resolve(
          "docs/sql/hr_ai_upgrade_20260921_03__remove_legacy_jd_evidence_json.sql");
      executeScript(connection, upgrade);
      executeScript(connection, upgrade);

      String legacy = jdbc.queryForObject(
          "SELECT facts_json FROM demand_jd_fact_snapshot WHERE id='legacy-jd'", String.class);
      String current = jdbc.queryForObject(
          "SELECT facts_json FROM demand_jd_fact_snapshot WHERE id='current-jd'", String.class);
      assertThat(legacy).contains("professional", "coreSkills", "Java")
          .doesNotContain("evidence", "confidence", "reliability", "旧 JD 摘录");
      assertThat(current).contains("professional", "coreSkills", "Go");
    });
  }

  @Test
  void legacyResumeEvidenceJsonUpgradeRemovesOnlyDeprecatedFieldsAndSupportsReruns() throws Exception {
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      Flyway.configure().dataSource(new SingleConnectionDataSource(connection, true))
          .locations("classpath:db/migration").target("2026092101").load().migrate();
      jdbc.update("INSERT INTO talent_resume_intake_processing(id,content_sha256,original_file_name,media_type,"
          + "object_key,status,attempt_count,max_attempts,parsed_facts_json) VALUES"
          + "('legacy-json',?,'resume.pdf','application/pdf','resume.pdf','SUCCEEDED',1,3,CAST(? AS JSON))",
          "a".repeat(64), "{\"candidate\":{\"fullName\":\"候选人\"},\"unknownItems\":[],"
              + "\"evidence\":[{\"quote\":\"旧摘录\"}],\"confidence\":0.9,\"reliability\":\"HIGH\"}");
      Path upgrade = repositoryRoot().resolve(
          "docs/sql/hr_ai_upgrade_20260921_02__remove_legacy_resume_evidence_json.sql");
      executeScript(connection, upgrade);
      executeScript(connection, upgrade);
      String result = jdbc.queryForObject(
          "SELECT parsed_facts_json FROM talent_resume_intake_processing WHERE id='legacy-json'", String.class);
      assertThat(result).contains("candidate", "unknownItems", "候选人")
          .doesNotContain("evidence", "confidence", "reliability", "旧摘录");
    });
  }

  @Test
  void independentJdUpgradePreservesCodesAndSequenceAcrossPartialExecutionAndReruns() throws Exception {
    withDatabase(connection -> {
      var source = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(source);
      Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091706").load().migrate();
      jdbc.update("INSERT INTO demand_jd_code_option(id,kind,code,name) VALUES"
          + " ('type','JOB_TYPE','01','工程师'),('region','REGION','02','杭州')");
      jdbc.update("INSERT INTO demand_jd_code_pair_sequence(id,`last_value`) VALUES ('0102',999)");
      jdbc.update("INSERT INTO demand_jd(id,code,title,distinction,status,job_type_code,work_region_code,owner_user_id)"
          + " VALUES ('old-jd','JD-010201','工程师','杭州','DRAFT','01','02','hr-a')");
      jdbc.execute("ALTER TABLE demand_jd DROP INDEX uk_jd_active_identity");
      var upgrade = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091801").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
      upgrade.validate();
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
      executeScript(connection, repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20260918_01__jd_independent_grouping.sql"));
      assertThat(jdbc.queryForMap("SELECT code,job_type_code,work_region_code FROM demand_jd WHERE id='old-jd'"))
          .containsEntry("code", "JD-010201").containsEntry("job_type_code", "001").containsEntry("work_region_code", "002");
      assertThat(jdbc.queryForObject("SELECT code FROM demand_jd_code_option WHERE id='type'", String.class)).isEqualTo("001");
      assertThat(jdbc.queryForObject("SELECT `last_value` FROM demand_jd_code_pair_sequence WHERE id='001002'", Long.class)).isEqualTo(999L);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()"
          + " AND TABLE_NAME='demand_jd' AND COLUMN_NAME='active_identity_key'", Integer.class)).isZero();
      jdbc.update("INSERT INTO demand_jd(id,code,title,distinction,status,job_type_code,work_region_code,owner_user_id)"
          + " VALUES ('new-jd','JD-0010021000','工程师','杭州','DRAFT','001','002','hr-b')");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demand_jd WHERE title='工程师'", Integer.class)).isEqualTo(2);
      assertThatThrownBy(() -> jdbc.update("INSERT INTO demand_jd(id,code,title,status)"
          + " VALUES ('duplicate-code','JD-0010021000','其他岗位','DRAFT')")).isInstanceOf(DataIntegrityViolationException.class);
      jdbc.update("INSERT INTO demand_jd_code_option(id,kind,code,name) VALUES ('last-type','JOB_TYPE','999','最后岗位')");
      assertThatThrownBy(() -> jdbc.update("INSERT INTO demand_jd_code_option(id,kind,code,name)"
          + " VALUES ('zero-type','JOB_TYPE','000','非法岗位')")).isInstanceOf(DataIntegrityViolationException.class);
    });
  }

  @Test
  void jdTitleSuggestionUpgradePreservesPendingReviewsAndReruns() throws Exception {
    withDatabase(connection -> {
      var source = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(source);
      Flyway.configure().dataSource(source).locations("classpath:db/migration")
          .target("2026092301").load().migrate();
      Path script = repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20260923_02__jd_title_suggestions.sql");
      executeScript(connection, script);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demand_jd_title_suggestion WHERE status='APPROVED'",
          Integer.class)).isEqualTo(3);
      jdbc.update("INSERT INTO demand_jd_title_suggestion(id,name,status,submitted_by_user_id) "
          + "VALUES ('pending-title','自定义岗位','PENDING','owner-a')");
      executeScript(connection, script);
      assertThat(jdbc.queryForObject("SELECT status FROM demand_jd_title_suggestion "
          + "WHERE name='自定义岗位'", String.class)).isEqualTo("PENDING");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demand_jd_title_suggestion", Integer.class)).isEqualTo(4);
      var upgrade = Flyway.configure().dataSource(source).locations("classpath:db/migration")
          .target("2026092302").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
      upgrade.validate();
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
      assertThatThrownBy(() -> jdbc.update("INSERT INTO demand_jd_title_suggestion(id,name,status) "
          + "VALUES ('same-title','自定义岗位','PENDING')"))
          .isInstanceOf(DataIntegrityViolationException.class);
    });
  }

  @Test
  void departmentJdSourceUpgradePreservesHistoryAndRerunsAfterPartialCompletion() throws Exception {
    withDatabase(connection -> {
      var source = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(source);
      Flyway.configure().dataSource(source).locations("classpath:db/migration")
          .target("2026092302").load().migrate();
      jdbc.update("INSERT INTO demand_jd(id,code,title,status,current_version_no,owner_department_id) "
          + "VALUES ('source-jd','JD-01001001001','原岗位','ACTIVE',1,'department-a')");
      jdbc.update("INSERT INTO demand_jd_version(id,jd_id,version_no,title,raw_text,status,source_type) "
          + "VALUES ('source-v1','source-jd',1,'原岗位','原始内容','PUBLISHED','MANUAL')");
      jdbc.execute("ALTER TABLE demand_jd ADD COLUMN applied_from_jd_version_id CHAR(26) "
          + "CHARACTER SET ascii COLLATE ascii_bin NULL");
      Path script = repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20260924_01__department_jd_sources.sql");
      executeScript(connection, script);
      jdbc.update("INSERT INTO demand_jd(id,code,title,status,current_version_no,owner_department_id,applied_from_jd_version_id) "
          + "VALUES ('copy-jd','JD-01001001002','原岗位','ACTIVE',1,'department-a','source-v1')");
      executeScript(connection, script);

      assertThat(jdbc.queryForObject("SELECT applied_from_jd_version_id FROM demand_jd WHERE id='source-jd'",
          String.class)).isNull();
      assertThat(jdbc.queryForObject("SELECT applied_from_jd_version_id FROM demand_jd WHERE id='copy-jd'",
          String.class)).isEqualTo("source-v1");
      assertThat(jdbc.queryForList("SELECT code FROM demand_jd ORDER BY code", String.class))
          .containsExactly("JD-01001001001", "JD-01001001002");
      var upgrade = Flyway.configure().dataSource(source).locations("classpath:db/migration")
          .target("2026092401").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
      upgrade.validate();
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
    });
  }

  @Test
  void jdDepartmentCodeUpgradePreservesLegacySequenceAndResumesPartialExpansion() throws Exception {
    withDatabase(connection -> {
      var source = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(source);
      Flyway.configure().dataSource(source).locations("classpath:db/migration")
          .target("2026092202").load().migrate();
      jdbc.update("INSERT INTO demand_jd_code_pair_sequence(id,`last_value`) VALUES ('001002',999)");
      jdbc.update("INSERT INTO demand_jd(id,code,title,status) VALUES "
          + "('old-jd','JD-001002999','历史岗位','DRAFT')");
      Path script = repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20260923_01__jd_department_code.sql");
      executeScript(connection, script);
      jdbc.update("INSERT INTO demand_jd_department_code(department_id,code) VALUES ('department-a','01')");
      jdbc.execute("ALTER TABLE demand_jd_code_pair_sequence MODIFY COLUMN id CHAR(6) "
          + "CHARACTER SET ascii COLLATE ascii_bin NOT NULL");
      executeScript(connection, script);
      jdbc.update("INSERT INTO demand_jd_code_pair_sequence(id,`last_value`) VALUES ('01001002',1000)");
      jdbc.update("INSERT INTO demand_jd(id,code,title,status) VALUES "
          + "('new-jd','JD-010010021000','新岗位','DRAFT')");
      executeScript(connection, script);

      assertThat(jdbc.queryForObject("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
          + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd_code_pair_sequence' "
          + "AND COLUMN_NAME='id'", Integer.class)).isEqualTo(8);
      assertThat(jdbc.queryForObject("SELECT `last_value` FROM demand_jd_code_pair_sequence WHERE id='001002'", Long.class))
          .isEqualTo(999L);
      assertThat(jdbc.queryForObject("SELECT `last_value` FROM demand_jd_code_pair_sequence WHERE id='01001002'", Long.class))
          .isEqualTo(1000L);
      assertThat(jdbc.queryForObject("SELECT code FROM demand_jd_department_code WHERE department_id='department-a'", String.class))
          .isEqualTo("01");
      assertThat(jdbc.queryForList("SELECT code FROM demand_jd ORDER BY id", String.class))
          .containsExactly("JD-010010021000", "JD-001002999");
      assertThatThrownBy(() -> jdbc.update("INSERT INTO demand_jd_department_code(department_id,code) "
          + "VALUES ('department-b','01')")).isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> jdbc.update("INSERT INTO demand_jd_department_code(department_id,code) "
          + "VALUES ('department-b','00')")).isInstanceOf(DataIntegrityViolationException.class);
    });
  }

  @Test
  void consolidatedSchemaCreatesJdDepartmentCodeAndEightDigitSequence() throws Exception {
    withDatabase(connection -> {
      executeScript(connection, repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.update("INSERT INTO demand_jd_department_code(department_id,code) VALUES ('department-a','99')");
      jdbc.update("INSERT INTO demand_jd_code_pair_sequence(id,`last_value`) VALUES ('99001002',1)");
      assertThat(jdbc.queryForObject("SELECT code FROM demand_jd_department_code WHERE department_id='department-a'", String.class))
          .isEqualTo("99");
      assertThat(jdbc.queryForObject("SELECT `last_value` FROM demand_jd_code_pair_sequence WHERE id='99001002'", Long.class))
          .isEqualTo(1L);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS "
          + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd' "
          + "AND COLUMN_NAME='applied_from_jd_version_id'", Integer.class)).isEqualTo(1);
    });
  }

  @Test
  void jdDimensionUpgradePreservesLegacyCodesAndResumesPartialColumns() throws Exception {
    withDatabase(connection -> {
      var source = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(source);
      Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091705").load().migrate();
      var history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
      jdbc.update("INSERT INTO demand_jd(id,code,title,status,current_version_no,owner_user_id) "
          + "VALUES ('legacy-dimension','JD-010101','历史流水号岗位','DRAFT',1,'owner')");
      jdbc.execute("ALTER TABLE demand_jd ADD COLUMN job_type_code CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NULL");
      var upgrade = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091706").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
      upgrade.validate();
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
      assertThat(jdbc.queryForObject("SELECT code FROM demand_jd WHERE id='legacy-dimension'", String.class))
          .isEqualTo("JD-010101");
      assertThat(jdbc.queryForMap("SELECT job_type_code,work_region_code FROM demand_jd WHERE id='legacy-dimension'").values())
          .containsOnlyNulls();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demand_jd_code_option", Integer.class)).isZero();
      jdbc.update("INSERT INTO demand_jd_code_option(id,kind,code,name) VALUES ('type','JOB_TYPE','01','IDC工程师')");
      assertThatThrownBy(() -> jdbc.update("INSERT INTO demand_jd_code_option(id,kind,code,name) "
          + "VALUES ('duplicate-code','JOB_TYPE','01','另一岗位')")).isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> jdbc.update("INSERT INTO demand_jd_code_option(id,kind,code,name) "
          + "VALUES ('duplicate-name','JOB_TYPE','02','IDC工程师')")).isInstanceOf(DataIntegrityViolationException.class);
      jdbc.update("INSERT INTO demand_jd_code_pair_sequence(id,`last_value`) VALUES ('0101',42)");
      executeScript(connection, repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20260917_06__jd_dimension_code.sql"));
      assertThat(jdbc.queryForObject("SELECT name FROM demand_jd_code_option WHERE id='type'", String.class)).isEqualTo("IDC工程师");
      assertThat(jdbc.queryForObject("SELECT `last_value` FROM demand_jd_code_pair_sequence WHERE id='0101'", Long.class)).isEqualTo(42L);
      assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE version<>'2026091706' ORDER BY installed_rank"))
          .isEqualTo(history);
    });
  }

  @Test
  void jdDimensionUpgradeRejectsIncompatibleExistingColumn() throws Exception {
    withDatabase(connection -> {
      var source = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(source);
      Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091705").load().migrate();
      jdbc.execute("ALTER TABLE demand_jd ADD COLUMN job_type_code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NULL");
      assertThatThrownBy(() -> executeScript(connection,
          repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20260917_06__jd_dimension_code.sql")))
          .isInstanceOf(ScriptException.class);
      assertThat(jdbc.queryForObject("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
          + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd' AND COLUMN_NAME='job_type_code'", Integer.class))
          .isEqualTo(3);
    });
  }

  @Test
  void jdDepartmentAllocatorPreservesLegacyCollisionProtectionAcrossTransactions() throws Exception {
    withDatabase(connection -> {
      executeScript(connection, repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.update("INSERT INTO demand_jd(id,code,title,status,current_version_no,owner_user_id,deleted) "
          + "VALUES ('deleted-code','JD-01100100001','已删除的历史流水号岗位','DRAFT',1,'owner',TRUE)");
      var source = new DriverManagerDataSource(connection.getMetaData().getURL(),
          requiredEnvironment("HR_DB_USER"), requiredEnvironment("HR_DB_PASSWORD"));
      var configuration = new MybatisConfiguration();
      configuration.setEnvironment(new Environment("jd-dimension-collision", new SpringManagedTransactionFactory(), source));
      configuration.addMapper(JdEntityMapper.class);
      configuration.addMapper(JdCodeOptionMapper.class);
      var factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
      var session = new SqlSessionTemplate(factory);
      var service = new JdEntityPersistenceServiceImpl();
      ReflectionTestUtils.setField(service, "baseMapper", session.getMapper(JdEntityMapper.class));
      var options = new JdCodeOptionPersistenceServiceImpl();
      ReflectionTestUtils.setField(options, "baseMapper", session.getMapper(JdCodeOptionMapper.class));
      var transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
      transactions.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
      assertThat(transactions.<String>execute(status -> service.departmentCode("department-a"))).isEqualTo("01");
      assertThat(transactions.<String>execute(status -> service.nextCode("01", "100", "100")))
          .isEqualTo("JD-01100100002");
      jdbc.update("INSERT INTO demand_jd(id,code,title,status,current_version_no,owner_user_id) "
          + "VALUES ('pair-code','JD-01100100002','新组合编号岗位','DRAFT',1,'owner')");
      jdbc.update("UPDATE demand_jd_code_sequence SET `last_value`=100100001 WHERE id=1");
      assertThat(jdbc.queryForObject("SELECT `last_value` FROM demand_jd_code_sequence WHERE id=1", Long.class))
          .isEqualTo(100100001L);
      var ready = new CountDownLatch(8);
      var start = new CountDownLatch(1);
      List<Future<String>> futures = new ArrayList<>();
      try (var executor = Executors.newFixedThreadPool(8)) {
        for (int index = 0; index < 8; index++) {
          futures.add(executor.submit(() -> {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            return transactions.execute(status -> service.nextCode("01", "100", "100"));
          }));
        }
        try {
          assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
          start.countDown();
        }
        List<String> allocated = new ArrayList<>();
        for (Future<String> future : futures) allocated.add(future.get(30, TimeUnit.SECONDS));
        assertThat(allocated).containsExactlyInAnyOrder("JD-01100100003", "JD-01100100004", "JD-01100100005",
            "JD-01100100006", "JD-01100100007", "JD-01100100008", "JD-01100100009", "JD-01100100010");
      }
      var transactionalJdbc = new JdbcTemplate(source);
      assertThat(transactions.<String>execute(status -> service.departmentCode("department-b"))).isEqualTo("02");
      assertThat(transactions.<String>execute(status -> {
        assertThat(transactionalJdbc.queryForObject("SELECT COUNT(*) FROM demand_jd WHERE code='JD-02002002001'", Integer.class)).isZero();
        jdbc.update("INSERT INTO demand_jd(id,code,title,status,current_version_no,owner_user_id) "
            + "VALUES ('late-code','JD-02002002001','快照建立后写入的岗位','DRAFT',1,'owner')");
        return service.nextCode("02", "002", "002");
      })).isEqualTo("JD-02002002002");
      assertThat(transactions.<String>execute(status -> {
        assertThat(transactionalJdbc.queryForObject("SELECT COUNT(*) FROM demand_jd_department_code", Integer.class))
            .isEqualTo(2);
        jdbc.update("INSERT INTO demand_jd_department_code(department_id,code) VALUES ('department-c','03')");
        return service.departmentCode("department-d");
      })).isEqualTo("04");
      transactions.executeWithoutResult(status -> {
        assertThat(transactionalJdbc.queryForObject("SELECT COUNT(*) FROM demand_jd_code_option WHERE kind='REGION'", Integer.class)).isZero();
        jdbc.update("INSERT INTO demand_jd_code_option(id,kind,code,name) VALUES ('late-option','REGION','001','杭州')");
        service.lockCodeAllocation();
        assertThat(options.lastOptionCode("REGION")).isEqualTo("001");
        assertThat(options.findCodeOptionByName("REGION", "杭州").getId()).isEqualTo("late-option");
      });
      jdbc.update("UPDATE demand_jd_code_pair_sequence SET `last_value`=999 WHERE id='01100100'");
      assertThat(transactions.<String>execute(status -> service.nextCode("01", "100", "100")))
          .isEqualTo("JD-011001001000");
    });
  }

  @Test
  void jdShortCodeUpgradePreservesHistoryAndResumesMissingSeed() throws Exception {
    withDatabase(connection -> {
      var source = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(source);
      Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091704").load().migrate();
      var history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
      jdbc.update("INSERT INTO demand_jd(id,code,title,status,current_version_no,owner_user_id) "
          + "VALUES ('legacy-code','JD-01JABCDEF23456789GHJKMNPQR','历史岗位','DRAFT',1,'owner')");
      jdbc.update("INSERT INTO demand_jd(id,code,title,status,current_version_no,owner_user_id,deleted) "
          + "VALUES ('short-code','JD-000042','已删除岗位','DRAFT',1,'owner',TRUE)");
      var before = jdbc.queryForList("SELECT id,code FROM demand_jd ORDER BY id");
      var upgrade = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091705").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
      upgrade.validate();
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
      assertThat(jdbc.queryForObject("SELECT `last_value` FROM demand_jd_code_sequence WHERE id=1", Long.class)).isEqualTo(42L);
      Path script = repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20260917_05__jd_short_code_sequence.sql");
      jdbc.update("UPDATE demand_jd_code_sequence SET `last_value`=100 WHERE id=1");
      executeScript(connection, script);
      assertThat(jdbc.queryForObject("SELECT `last_value` FROM demand_jd_code_sequence WHERE id=1", Long.class)).isEqualTo(100L);
      jdbc.update("DELETE FROM demand_jd_code_sequence WHERE id=1");
      executeScript(connection, script);
      assertThat(jdbc.queryForObject("SELECT `last_value` FROM demand_jd_code_sequence WHERE id=1", Long.class)).isEqualTo(42L);
      assertThat(jdbc.queryForList("SELECT id,code FROM demand_jd ORDER BY id")).isEqualTo(before);
      assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE version<>'2026091705' ORDER BY installed_rank"))
          .isEqualTo(history);
    });
  }

  @Test
  void jdDepartmentCodeBaselineAllocatesDistinctNumbersAcrossConcurrentTransactions() throws Exception {
    withDatabase(connection -> {
      executeScript(connection, repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      assertThat(jdbc.queryForObject("SELECT `last_value` FROM demand_jd_code_sequence WHERE id=1", Long.class)).isZero();
      var source = new DriverManagerDataSource(connection.getMetaData().getURL(),
          requiredEnvironment("HR_DB_USER"), requiredEnvironment("HR_DB_PASSWORD"));
      var configuration = new MybatisConfiguration();
      configuration.setEnvironment(new Environment("jd-code-concurrency", new SpringManagedTransactionFactory(), source));
      configuration.addMapper(JdEntityMapper.class);
      var factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
      var service = new JdEntityPersistenceServiceImpl();
      ReflectionTestUtils.setField(service, "baseMapper", new SqlSessionTemplate(factory).getMapper(JdEntityMapper.class));
      var transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
      String isolatedSchema = connection.getCatalog();
      var ready = new CountDownLatch(8);
      var start = new CountDownLatch(1);
      List<Future<String>> futures = new ArrayList<>();
      try (var executor = Executors.newFixedThreadPool(8)) {
        for (int index = 0; index < 8; index++) {
          String departmentId = "department-" + index;
          futures.add(executor.submit(() -> {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            return transactions.execute(status -> {
              assertThat(new JdbcTemplate(source).queryForObject("SELECT DATABASE()", String.class))
                  .isEqualTo(isolatedSchema);
              return service.departmentCode(departmentId);
            });
          }));
        }
        try {
          assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
          start.countDown();
        }
        List<String> allocated = new ArrayList<>();
        for (Future<String> future : futures) allocated.add(future.get(30, TimeUnit.SECONDS));
        assertThat(allocated).containsExactlyInAnyOrder("01", "02", "03", "04", "05", "06", "07", "08");
      }
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demand_jd_department_code", Integer.class)).isEqualTo(8);
    });
  }

  @Test
  void wecomVersionUpgradeResumesMissingColumnsAndPreservesExistingVersions() throws Exception {
    withDatabase(connection -> {
      var source = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(source);
      Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091601").load().migrate();
      var history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
      jdbc.update("INSERT INTO identity_wecom_sync_state(corp_id,version) VALUES ('synthetic-corp',7)");
      jdbc.update("INSERT INTO identity_wecom_member(id,corp_id,user_id,name,department_ids_json,directory_status,changed_batch_id) "
          + "VALUES ('synthetic-member','synthetic-corp','member','合成人员','[2]','VISIBLE','synthetic-batch')");
      for (String table : List.of("identity_wecom_sync_batch", "identity_wecom_department", "identity_wecom_member")) {
        jdbc.execute("ALTER TABLE " + table + " DROP COLUMN version");
      }
      Path upgrade = repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20260917_01__wecom_directory_version.sql");
      var latest = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091701").load();
      assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
      latest.validate();
      executeScript(connection, upgrade);
      assertThat(latest.migrate().migrationsExecuted).isZero();
      assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE version <> '2026091701' ORDER BY installed_rank"))
          .isEqualTo(history);
      assertThat(jdbc.queryForObject("SELECT version FROM identity_wecom_sync_state WHERE corp_id='synthetic-corp'", Integer.class)).isEqualTo(7);
      assertThat(jdbc.queryForObject("SELECT version FROM identity_wecom_member WHERE id='synthetic-member'", Integer.class)).isZero();
      assertThat(jdbc.queryForObject("SELECT JSON_LENGTH(department_ids_json) FROM identity_wecom_member WHERE id='synthetic-member'", Integer.class))
          .isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
          + "AND TABLE_NAME IN ('identity_wecom_sync_state','identity_wecom_sync_batch','identity_wecom_department','identity_wecom_member') "
          + "AND COLUMN_NAME='version' AND DATA_TYPE='int' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='0'", Integer.class)).isEqualTo(4);
    });
  }

  @Test
  void wecomVersionUpgradeRejectsIncompatibleColumnsBeforeAnyDdl() throws Exception {
    withDatabase(connection -> {
      var source = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(source);
      Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091601").load().migrate();
      jdbc.execute("ALTER TABLE identity_wecom_sync_state DROP COLUMN version");
      jdbc.execute("ALTER TABLE identity_wecom_member MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0");
      Path upgrade = repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20260917_01__wecom_directory_version.sql");
      assertThatThrownBy(() -> executeScript(connection, upgrade)).isInstanceOf(ScriptException.class);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
          + "AND TABLE_NAME='identity_wecom_sync_state' AND COLUMN_NAME='version'", Integer.class)).isZero();
      assertThat(jdbc.queryForObject("SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
          + "AND TABLE_NAME='identity_wecom_member' AND COLUMN_NAME='version'", String.class)).isEqualTo("bigint");
    });
  }

  @Test
  void wecomMigrationFromReleasedVersionPreservesFlywayHistory() throws Exception {
    withDatabase(connection -> {
      var source = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(source);
      Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091502").load().migrate();
      var previous = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
      var latest = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091701").load();
      assertThat(latest.migrate().migrationsExecuted).isEqualTo(2);
      latest.validate();
      assertThat(latest.migrate().migrationsExecuted).isZero();
      assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE version NOT IN ('2026091601','2026091701') ORDER BY installed_rank"))
          .isEqualTo(previous);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
          + "AND TABLE_NAME IN ('identity_wecom_sync_state','identity_wecom_sync_batch','identity_wecom_department','identity_wecom_member')",
          Integer.class)).isEqualTo(4);
    });
  }

  @Test
  void wecomAccountLinkMigrationKeepsIndexSemanticsAndSupportsReruns() throws Exception {
    withDatabase(connection -> {
      var source = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(source);
      Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091701").load().migrate();
      var upgrade = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091702").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
      upgrade.validate();
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
      executeScript(connection, repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20260917_02__wecom_account_link.sql"));
      assertThat(jdbc.queryForObject("SELECT NON_UNIQUE FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
          + "AND TABLE_NAME='identity_wecom_department_link' AND INDEX_NAME='uk_wecom_department_link_source'", Integer.class))
          .isZero();
      assertThat(jdbc.queryForObject("SELECT NON_UNIQUE FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
          + "AND TABLE_NAME='identity_wecom_department_link' AND INDEX_NAME='idx_wecom_department_link_local'", Integer.class))
          .isEqualTo(1);
    });
  }

  @Test
  void activeJdIdentityMigrationValidatesItsGeneratedColumnAndSupportsReruns() throws Exception {
    withDatabase(connection -> {
      var source = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(source);
      Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091702").load().migrate();
      var upgrade = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091703").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
      upgrade.validate();
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
      executeScript(connection, repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20260917_03__jd_active_identity.sql"));
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
          + "AND TABLE_NAME='demand_jd' AND COLUMN_NAME='active_identity_key' AND EXTRA LIKE '%STORED GENERATED%'", Integer.class))
          .isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
          + "AND TABLE_NAME='demand_jd' AND INDEX_NAME='uk_jd_active_identity' AND NON_UNIQUE=0 "
          + "AND COLUMN_NAME='active_identity_key'", Integer.class)).isEqualTo(1);
    });
  }

  @Test
  void mailMigrationFollowsIndependentJdWithoutRewritingDeployedWecomHistory() throws Exception {
    withDatabase(connection -> {
      var source = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(source);
      Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091801").load().migrate();
      var history = jdbc.queryForList("SELECT version, description, checksum, success FROM flyway_schema_history ORDER BY installed_rank");
      var upgrade = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091802").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
      upgrade.validate();
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
      executeScript(connection, repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20260918_02__mail_resume_intake.sql"));
      assertThat(jdbc.queryForList("SELECT version, description, checksum, success FROM flyway_schema_history ORDER BY installed_rank"))
          .containsAll(history);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
          + "AND TABLE_NAME IN ('talent_mailbox_cursor','talent_mail_resume','talent_mail_route_rule','talent_mail_ingest_run')", Integer.class))
          .isEqualTo(4);
    });
  }

  @Test
  void resumeIntakeOutboxMigrationPreservesProcessingRowsAndSupportsReruns() throws Exception {
    withDatabase(connection -> {
      var source = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(source);
      Flyway.configure().dataSource(source).locations("classpath:db/migration")
          .target("2026091802").load().migrate();
      jdbc.update("INSERT INTO talent_resume_intake_processing(id,content_sha256,original_file_name,"
          + "media_type,object_key,status,attempt_count,max_attempts,create_time,update_time)"
          + " VALUES (?,?,?,?,?,'QUEUED',0,3,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
          "P0000000000000000000000000", "a".repeat(64), "resume.pdf", "application/pdf", "object");
      var upgrade = Flyway.configure().dataSource(source).locations("classpath:db/migration")
          .target("2026091803").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
      upgrade.validate();
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
      executeScript(connection,
          repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20260918_03__resume_intake_outbox.sql"));
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM talent_resume_intake_processing"
          + " WHERE id='P0000000000000000000000000' AND status='QUEUED'", Integer.class)).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES"
          + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_resume_intake_outbox'", Integer.class))
          .isEqualTo(1);
    });
  }

  @Test
  void jdVersionRegionMigrationBackfillsOnceAndPreservesHistoricalSnapshots() throws Exception {
    withDatabase(connection -> {
      var source = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(source);
      Flyway.configure().dataSource(source).locations("classpath:db/migration")
          .target("2026091803").load().migrate();
      jdbc.update("INSERT INTO demand_jd_code_option(id,kind,code,name) VALUES"
          + " ('region-1','REGION','001','北京'),('region-2','REGION','002','上海')");
      jdbc.update("INSERT INTO demand_jd(id,code,job_type_code,work_region_code,title,status,current_version_no)"
          + " VALUES ('jd-region','JD-001001001','001','001','运维工程师','ACTIVE',1)");
      jdbc.update("INSERT INTO demand_jd_version(id,jd_id,version_no,title,raw_text,status,source_type)"
          + " VALUES ('version-1','jd-region',1,'运维工程师','Linux','PUBLISHED','MANUAL')");
      // 模拟 DDL 已成功而回填尚未执行的部分完成状态。
      jdbc.execute("ALTER TABLE demand_jd_version ADD COLUMN work_region_code CHAR(3)"
          + " CHARACTER SET ascii COLLATE ascii_bin NULL AFTER recruitment_type");
      var upgrade = Flyway.configure().dataSource(source).locations("classpath:db/migration")
          .target("2026091901").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
      upgrade.validate();
      assertThat(jdbc.queryForObject("SELECT work_region_code FROM demand_jd_version"
          + " WHERE id='version-1'", String.class)).isEqualTo("001");
      jdbc.update("UPDATE demand_jd SET work_region_code='002',current_version_no=2 WHERE id='jd-region'");
      jdbc.update("INSERT INTO demand_jd_version(id,jd_id,version_no,title,raw_text,status,source_type,work_region_code)"
          + " VALUES ('version-2','jd-region',2,'运维工程师','Linux 与网络','PUBLISHED','MANUAL','002')");
      executeScript(connection,
          repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20260919_01__jd_version_work_region.sql"));
      assertThat(jdbc.queryForList("SELECT id,work_region_code FROM demand_jd_version ORDER BY version_no"))
          .extracting(row -> row.get("work_region_code"))
          .containsExactly("001", "002");
    });
  }

  @Test
  void wecomInterviewCalendarMigrationCreatesProjectionAndAuditAndSupportsReruns() throws Exception {
    withDatabase(connection -> {
      var source = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(source);
      Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091703").load().migrate();
      var upgrade = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091704").load();
      assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
      upgrade.validate();
      assertThat(upgrade.migrate().migrationsExecuted).isZero();
      executeScript(connection,
          repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20260917_04__wecom_interview_calendar.sql"));
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
          + "AND TABLE_NAME IN ('interview_external_calendar_projection','interview_external_calendar_attempt')",
          Integer.class)).isEqualTo(2);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
          + "AND TABLE_NAME='interview_external_calendar_projection' "
          + "AND INDEX_NAME='uk_interview_external_calendar_interview' AND NON_UNIQUE=0", Integer.class)).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS "
          + "WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME IN "
          + "('fk_interview_external_calendar_interview','fk_interview_external_attempt_projection')",
          Integer.class)).isEqualTo(2);
    });
  }

  @Test
  void wecomUpgradeResumesPartialDdlAndNeverRewritesExistingAccountsOrDirectoryIdentities() throws Exception {
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.execute("CREATE TABLE identity_user_account(id VARCHAR(26) PRIMARY KEY, password_hash VARCHAR(255))");
      jdbc.update("INSERT INTO identity_user_account VALUES ('old-account','synthetic-password-hash')");
      Path upgrade = repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20260916_01__wecom_directory.sql");
      String script = Files.readString(upgrade);
      int start = script.indexOf("CREATE TABLE IF NOT EXISTS identity_wecom_sync_state");
      jdbc.execute(script.substring(start, script.indexOf(';', start)));
      executeScript(connection, upgrade);
      jdbc.update("INSERT INTO identity_wecom_member(id,corp_id,user_id,name,department_ids_json,direct_leader_ids_json,directory_status,changed_batch_id) "
          + "VALUES ('stable-id','corp','member','合成人员','[2,3]','[\"leader\"]','VISIBLE','batch')");
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForObject("SELECT password_hash FROM identity_user_account WHERE id='old-account'", String.class))
          .isEqualTo("synthetic-password-hash");
      assertThat(jdbc.queryForObject("SELECT id FROM identity_wecom_member WHERE corp_id='corp' AND user_id='member'", String.class))
          .isEqualTo("stable-id");
      assertThat(jdbc.queryForObject("SELECT JSON_LENGTH(department_ids_json) FROM identity_wecom_member WHERE id='stable-id'", Integer.class))
          .isEqualTo(2);
      assertThatThrownBy(() -> jdbc.update("INSERT INTO identity_wecom_member(id,corp_id,user_id,name,department_ids_json,directory_status,changed_batch_id) "
          + "VALUES ('duplicate','corp','member','合成人员','[2]','VISIBLE','batch')")).isInstanceOf(DataIntegrityViolationException.class);
    });
  }

  @Test
  void releasedHolidayMigrationUpgradesInterviewLabelsWithoutRepair() throws Exception {
    withDatabase(connection -> {
      var source = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(source);
      Flyway.configure().dataSource(source).locations("classpath:db/migration")
          .target("2026091501").load().migrate();
      var history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
      assertThat(jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='2026091501'", Integer.class))
          .isEqualTo(13057002);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
          + "AND TABLE_NAME='interview_interview' AND COLUMN_NAME='initial_comment'", Integer.class)).isZero();
      var upgraded = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091502").load();
      assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
      upgraded.validate();
      assertThat(upgraded.migrate().migrationsExecuted).isZero();
      assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE version <> '2026091502' ORDER BY installed_rank"))
          .isEqualTo(history);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
          + "AND TABLE_NAME='interview_interview' AND COLUMN_NAME='initial_comment'", Integer.class)).isEqualTo(1);
    });
  }

  @Test
  void interviewLabelUpgradeKeepsHistoricalRowsAndSupportsPartialReruns() throws Exception {
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.execute("CREATE TABLE interview_interview (id VARCHAR(26) PRIMARY KEY, interview_type VARCHAR(32), "
          + "CONSTRAINT ck_interview_type CHECK (interview_type IN ('INTERNAL','EXTERNAL_CLIENT','EXTERNAL_CLIENT_AI')))");
      jdbc.update("INSERT INTO interview_interview VALUES ('old','EXTERNAL_CLIENT')");
      var upgrade = repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20260915_02__interview_labels.sql");
      executeScript(connection, upgrade);
      jdbc.update("INSERT INTO interview_interview(id,interview_type,initial_comment) VALUES ('new','EXTERNAL',?)", "评".repeat(1000));
      executeScript(connection, upgrade);
      jdbc.execute("ALTER TABLE interview_interview DROP CHECK ck_interview_type");
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForObject("SELECT interview_type FROM interview_interview WHERE id='old'", String.class))
          .isEqualTo("EXTERNAL_CLIENT");
      assertThat(jdbc.queryForObject("SELECT CHAR_LENGTH(initial_comment) FROM interview_interview WHERE id='new'", Integer.class))
          .isEqualTo(1000);
      assertThatThrownBy(() -> jdbc.update("INSERT INTO interview_interview(id,interview_type) VALUES ('bad','UNKNOWN')"))
          .rootCause().isInstanceOfSatisfying(SQLException.class,
              error -> assertThat(error.getErrorCode()).isEqualTo(3819));
    });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void activityAndClientRestrictionUpgradesPreserveHistoryAndResume(boolean partial) throws Exception {
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.execute("CREATE TABLE recruitment_application (id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY) ENGINE=InnoDB");
      jdbc.execute("CREATE TABLE submission_client_restriction (id CHAR(26) PRIMARY KEY, "
          + "source_submission_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL) ENGINE=InnoDB");
      jdbc.update("INSERT INTO submission_client_restriction VALUES ('old-restriction','old-submission')");
      if (partial) jdbc.execute("ALTER TABLE submission_client_restriction ADD COLUMN source_application_id "
          + "CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL");
      Path sql = repositoryRoot().resolve("docs/sql");
      for (int run = 0; run < 2; run++) {
        executeScript(connection, sql.resolve("hr_ai_upgrade_20260914_05__recruitment_activity_origin.sql"));
        executeScript(connection, sql.resolve("hr_ai_upgrade_20260914_06__application_client_restrictions.sql"));
      }
      assertThat(jdbc.queryForObject("SELECT source_submission_id FROM submission_client_restriction "
          + "WHERE id='old-restriction'", String.class)).isEqualTo("old-submission");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission_client_restriction "
          + "WHERE source_application_id IS NULL AND evidence_reference IS NULL", Integer.class)).isEqualTo(1);
      jdbc.update("INSERT INTO recruitment_application VALUES ('activity')");
      jdbc.update("INSERT INTO submission_client_restriction "
          + "(id,source_application_id,evidence_reference) VALUES ('new-restriction','activity','人工证据')");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recruitment_activity_origin", Integer.class)).isZero();
      assertThatThrownBy(() -> jdbc.update("INSERT INTO submission_client_restriction "
          + "(id,source_application_id) VALUES ('bad-source','missing')"))
          .isInstanceOf(DataIntegrityViolationException.class);
    });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void packagedFlywaySupportsEmptyAndReleasedDatabasesWithoutRepair(boolean existing) throws Exception {
    withDatabase(connection -> {
      var source = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(source);
      Integer originalChecksum = null;
      if (existing) {
        // 只加载已发布的 V0001，模拟本次变更之前正常启动过的数据库。
        Flyway.configure().dataSource(source).locations("filesystem:"
            + repositoryRoot().resolve("bootstrap/src/main/resources/db/migration").toString().replace('\\', '/'))
            .load().migrate();
        originalChecksum = jdbc.queryForObject(
            "SELECT checksum FROM flyway_schema_history WHERE version='0001' OR version='1'", Integer.class);
        jdbc.update("INSERT INTO demand_jd (id,code,title,status,current_version_no,owner_user_id,"
            + "public_visible) VALUES ('flyway-scope','flyway-scope','历史岗位','ACTIVE',1,'original-owner',TRUE)");
      }
      var upgraded = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2026091701").load();
      assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(existing ? 13 : 14);
      upgraded.validate();
      assertThat(upgraded.info().applied()).hasSize(14);
      assertThat(upgraded.migrate().migrationsExecuted).isZero();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_role WHERE code='HR_DIRECTOR'", Integer.class))
          .isEqualTo(1);
      if (existing) {
        assertThat(jdbc.queryForObject(
            "SELECT checksum FROM flyway_schema_history WHERE version='0001' OR version='1'", Integer.class))
            .isEqualTo(originalChecksum);
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM demand_jd WHERE id='flyway-scope'", String.class))
            .isEqualTo("original-owner");
        assertThat(jdbc.queryForObject("SELECT public_visible FROM demand_jd WHERE id='flyway-scope'", Boolean.class)).isFalse();
      }
    });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void ownerScopeUpgradePreservesHistoryAndSupportsPartialCompletion(boolean partial) throws Exception {
    withDatabase(connection -> {
      Path sqlRoot = repositoryRoot().resolve("docs/sql");
      executeScript(connection, sqlRoot.resolve("hr_ai_complete_schema.sql"));
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.update("DELETE FROM identity_role_permission WHERE role_id IN "
          + "(SELECT id FROM identity_role WHERE code='HR_DIRECTOR')");
      jdbc.update("DELETE FROM identity_role WHERE code='HR_DIRECTOR'");
      jdbc.update("UPDATE identity_role SET name='招聘负责人' WHERE code='RECRUITMENT_MANAGER'");
      jdbc.execute("ALTER TABLE demand_jd DROP INDEX ix_demand_jd_owner_page");
      jdbc.execute("ALTER TABLE talent_candidate DROP INDEX ix_talent_candidate_owner_page");
      jdbc.execute("ALTER TABLE talent_resume_intake DROP INDEX ix_talent_intake_owner_page");
      jdbc.execute("ALTER TABLE matching_weight_profile DROP INDEX ix_matching_weight_profile_owner_page, "
          + "DROP INDEX ix_matching_weight_profile_page");
      if (partial) {
        jdbc.execute("ALTER TABLE talent_candidate ADD INDEX ix_talent_candidate_owner_page (owner_user_id,update_time,id)");
        jdbc.update("INSERT INTO identity_role (id,code,name,system_role,status,version,create_time) "
            + "VALUES ('00000000000000000000000005','HR_DIRECTOR','人力负责人',TRUE,'ACTIVE',0,UTC_TIMESTAMP(6))");
      }
      jdbc.update("INSERT INTO demand_jd (id,code,title,status,current_version_no,owner_user_id,"
          + "owner_department_id,public_visible,source_fingerprint) VALUES "
          + "('scope-jd','scope-jd','历史岗位','ACTIVE',1,'historical-owner','historical-dept',TRUE,?)", "a".repeat(64));
      var before = jdbc.queryForMap("SELECT id,code,title,owner_user_id,source_fingerprint FROM demand_jd WHERE id='scope-jd'");
      Path upgrade = sqlRoot.resolve("hr_ai_upgrade_20260912_01__owner_scope.sql");
      executeScript(connection, upgrade);
      var migrated = jdbc.queryForMap("SELECT id,code,title,owner_user_id,source_fingerprint FROM demand_jd WHERE id='scope-jd'");
      assertThat(migrated).isEqualTo(before);
      assertThat(jdbc.queryForObject("SELECT public_visible FROM demand_jd WHERE id='scope-jd'", Boolean.class)).isFalse();
      int version = jdbc.queryForObject("SELECT version FROM demand_jd WHERE id='scope-jd'", Integer.class);
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForObject("SELECT version FROM demand_jd WHERE id='scope-jd'", Integer.class)).isEqualTo(version);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_role_permission rp JOIN identity_role r ON r.id=rp.role_id "
          + "JOIN identity_permission p ON p.code=rp.permission_code WHERE r.code='HR_DIRECTOR' AND p.category='IDENTITY'", Integer.class)).isZero();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_role_permission rp JOIN identity_role r ON r.id=rp.role_id "
          + "WHERE r.code='HR_DIRECTOR' AND rp.permission_code='recruitment.manage'", Integer.class)).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_role_permission rp JOIN identity_role r ON r.id=rp.role_id "
          + "WHERE r.code='INTERVIEWER' AND rp.permission_code='talent.read'", Integer.class)).isZero();
      assertThat(jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() "
          + "AND TABLE_NAME='demand_jd' AND INDEX_NAME='ix_demand_jd_owner_page' ORDER BY SEQ_IN_INDEX", String.class))
          .containsExactly("owner_user_id", "deleted", "create_time", "id");
      jdbc.execute("ALTER TABLE demand_jd DROP INDEX ix_demand_jd_owner_page, ADD INDEX ix_demand_jd_owner_page (id)");
      assertThatThrownBy(() -> executeScript(connection, upgrade)).hasRootCauseInstanceOf(SQLException.class)
          .hasStackTraceContaining("incompatible");
    });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void followUpPlanColumnsUpgradePreservesHistoryAndResumesPartialUpgrade(boolean partial) throws Exception {
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.execute("CREATE TABLE recruitment_follow_up_plan (id INT PRIMARY KEY, note VARCHAR(500) NOT NULL)");
      jdbc.execute("CREATE TABLE recruitment_follow_up_plan_history (id INT PRIMARY KEY, reason VARCHAR(500) NOT NULL)");
      jdbc.update("INSERT INTO recruitment_follow_up_plan VALUES (1,'原预约')");
      jdbc.update("INSERT INTO recruitment_follow_up_plan_history VALUES (1,'原审计')");
      if (partial) {
        jdbc.execute("ALTER TABLE recruitment_follow_up_plan ADD COLUMN result_reason VARCHAR(500) NULL");
        jdbc.update("UPDATE recruitment_follow_up_plan SET result_reason='已记录结果' WHERE id=1");
      }
      Path upgrade = legacyUpgrade("hr_ai_upgrade_20260909_04__follow_up_plan_columns.sql");
      executeScript(connection, upgrade);
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForObject("SELECT note FROM recruitment_follow_up_plan WHERE id=1", String.class))
          .isEqualTo("原预约");
      assertThat(jdbc.queryForObject("SELECT result_reason FROM recruitment_follow_up_plan WHERE id=1", String.class))
          .isEqualTo(partial ? "已记录结果" : null);
      assertThat(jdbc.queryForObject("SELECT reason FROM recruitment_follow_up_plan_history WHERE id=1", String.class))
          .isEqualTo("原审计");
      assertThat(jdbc.queryForObject("SELECT version FROM recruitment_follow_up_plan_history WHERE id=1", Integer.class))
          .isZero();
      jdbc.update("UPDATE recruitment_follow_up_plan_history SET version=7 WHERE id=1");
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForObject("SELECT version FROM recruitment_follow_up_plan_history WHERE id=1", Integer.class))
          .isEqualTo(7);
    });
  }

  @Test
  void asyncTaskTraceUpgradePreservesHistoricalTasksAndExistingTraceOnRerun() throws Exception {
    withDatabase(connection -> {
      Path sqlRoot = repositoryRoot().resolve("docs/sql");
      Path upgrade = legacyUpgrade("hr_ai_upgrade_20260908_14__async_task_trace.sql");
      executeScript(connection, sqlRoot.resolve("hr_ai_complete_schema.sql"));
      var dataSource = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(dataSource);
      // ALTER 会规范化既有 CHECK 的字符集标记；列契约直接比较完整元数据。
      String columnDefinition = "SELECT COLUMN_TYPE,CHARACTER_SET_NAME,COLLATION_NAME,IS_NULLABLE,"
          + "COLUMN_DEFAULT,EXTRA,COLUMN_COMMENT FROM information_schema.COLUMNS "
          + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_async_task' AND COLUMN_NAME='trace_id'";
      var definition = jdbc.queryForList(columnDefinition);
      assertThat(definition).hasSize(1);
      // 模拟升级前已有失败记录；DDL 不得重置次数、版本、时间或扩展数据。
      jdbc.execute("ALTER TABLE reporting_async_task DROP COLUMN trace_id");
      insertOutboxUpgradeTask(jdbc, "legacy-trace", "RESUME_PARSE");
      jdbc.update("UPDATE reporting_async_task SET status='FAILED',attempt_count=5,version=7,ext=? WHERE id='legacy-trace'",
          "{\"sentinel\":\"preserve\"}");
      var configuration = new MybatisConfiguration();
      configuration.setMapUnderscoreToCamelCase(true);
      configuration.setEnvironment(new Environment("async-task-trace-test", new JdbcTransactionFactory(), dataSource));
      var pagination = new MybatisPlusInterceptor();
      pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
      configuration.addInterceptor(pagination);
      configuration.addMapper(AsyncTaskMapper.class);
      var sessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
      Supplier<List<TaskSnapshot>> recentTasks = () -> {
        try (var session = sessionFactory.openSession()) {
          var service = new AsyncTaskServiceImpl();
          ReflectionTestUtils.setField(service, "baseMapper", session.getMapper(AsyncTaskMapper.class));
          return service.findRecentTasks(null, null, null, null, null, null, null, 10);
        }
      };
      assertThatThrownBy(recentTasks::get).hasRootCauseInstanceOf(SQLException.class)
          .rootCause().hasMessageContaining("trace_id");
      String history = "SELECT id,status,attempt_count,max_attempts,version,ext,create_time,update_time "
          + "FROM reporting_async_task WHERE id='legacy-trace'";
      var before = jdbc.queryForList(history);
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForList(columnDefinition)).isEqualTo(definition);
      assertThat(jdbc.queryForList(history)).isEqualTo(before);
      assertThat(jdbc.queryForObject("SELECT trace_id FROM reporting_async_task WHERE id='legacy-trace'", String.class)).isNull();
      assertThat(recentTasks.get()).singleElement().satisfies(task -> {
        assertThat(task.id()).isEqualTo("legacy-trace");
        assertThat(task.traceId()).isNull();
      });
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForList(history)).isEqualTo(before);
      jdbc.update("UPDATE reporting_async_task SET trace_id=? WHERE id='legacy-trace'", "persisted-task-trace-123456");
      var traced = jdbc.queryForList("SELECT * FROM reporting_async_task WHERE id='legacy-trace'");
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForList("SELECT * FROM reporting_async_task WHERE id='legacy-trace'")).isEqualTo(traced);
      assertThat(recentTasks.get()).singleElement().satisfies(task -> {
        assertThat(task.id()).isEqualTo("legacy-trace");
        assertThat(task.traceId()).isEqualTo("persisted-task-trace-123456");
      });
      jdbc.execute("ALTER TABLE reporting_async_task MODIFY trace_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL");
      assertThatThrownBy(() -> executeScript(connection, upgrade)).hasRootCauseInstanceOf(SQLException.class)
          .hasStackTraceContaining("incompatible");
      assertThat(jdbc.queryForList("SELECT * FROM reporting_async_task WHERE id='legacy-trace'")).isEqualTo(traced);
    });
  }

  @Test
  void candidatePageIndexUpgradeMatchesBaselineAndPreservesRowsOnRerun() throws Exception {
    withDatabase(connection -> {
      Path sqlRoot = repositoryRoot().resolve("docs/sql");
      executeScript(connection, sqlRoot.resolve("hr_ai_complete_schema.sql"));
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      String columns = "SELECT COLUMN_NAME FROM information_schema.STATISTICS "
          + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_candidate' "
          + "AND INDEX_NAME='ix_talent_candidate_page' ORDER BY SEQ_IN_INDEX";
      assertThat(jdbc.queryForList(columns, String.class)).containsExactly("update_time", "id");
      jdbc.execute("ALTER TABLE talent_candidate DROP INDEX ix_talent_candidate_page");
      jdbc.update("INSERT INTO talent_candidate (id,full_name,owner_user_id,owner_department_id,status) "
          + "VALUES ('page-sentinel','Synthetic candidate','synthetic-owner','synthetic-department','ACTIVE')");
      var before = jdbc.queryForList("SELECT id,full_name,status,update_time FROM talent_candidate");
      Path upgrade = legacyUpgrade("hr_ai_upgrade_20260908_11__candidate_page_index.sql");
      executeScript(connection, upgrade);
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForList(columns, String.class)).containsExactly("update_time", "id");
      assertThat(jdbc.queryForList("SELECT id,full_name,status,update_time FROM talent_candidate")).isEqualTo(before);
      jdbc.execute("ALTER TABLE talent_candidate DROP INDEX ix_talent_candidate_page, "
          + "ADD INDEX ix_talent_candidate_page (id,update_time)");
      assertThatThrownBy(() -> executeScript(connection, upgrade)).hasRootCauseInstanceOf(SQLException.class)
          .hasStackTraceContaining("incompatible");
      assertThat(jdbc.queryForList(columns, String.class)).containsExactly("id", "update_time");
      assertThat(jdbc.queryForList("SELECT id,full_name,status,update_time FROM talent_candidate")).isEqualTo(before);
    });
  }


  @Test
  void searchResourceUpgradeMatchesBaselinePreservesHistoryAndSupportsReruns() throws Exception {
    withDatabase(connection -> {
      executeScript(connection, repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      var baseline = jdbc.queryForList("SHOW CREATE TABLE search_index_resource");
      var baselineDefinition = searchResourceDefinition(jdbc);
      Path upgrade = repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20261008_03__search_index_retention.sql");
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForList("SHOW CREATE TABLE search_index_resource")).isEqualTo(baseline);
      jdbc.execute("DROP TABLE search_index_resource");
      jdbc.update("INSERT INTO search_index_rebuild_run (id,task_id,target_index,status,requested_by_user_id) "
          + "VALUES (?,?,?,'SUCCEEDED',?)", "R".repeat(26), "T".repeat(26), "legacy-owned", "U".repeat(26));
      var history = jdbc.queryForList("SELECT * FROM search_index_rebuild_run ORDER BY id");
      executeScript(connection, upgrade);
      jdbc.update("INSERT INTO search_index_resource (id,index_name,scope_name,resource_kind,status) "
          + "VALUES (?,?,'scope','REBUILD','CREATE_UNKNOWN')", "I".repeat(26), "unknown-owned");
      var resources = jdbc.queryForList("SELECT * FROM search_index_resource ORDER BY id");
      // 早期失败已建表但未包含状态索引，接续升级必须保留历史并补齐最终结构。
      jdbc.execute("ALTER TABLE search_index_resource DROP INDEX ix_search_resource_status");
      executeScript(connection, upgrade);
      executeScript(connection, upgrade);
      assertThat(searchResourceDefinition(jdbc)).isEqualTo(baselineDefinition);
      assertThat(jdbc.queryForList("SELECT * FROM search_index_resource ORDER BY id")).isEqualTo(resources);
      assertThat(jdbc.queryForList("SELECT * FROM search_index_rebuild_run ORDER BY id")).isEqualTo(history);
      assertThat(jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.STATISTICS "
          + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource' "
          + "AND INDEX_NAME='ix_search_resource_status' ORDER BY SEQ_IN_INDEX"))
          .extracting(row -> row.get("COLUMN_NAME")).containsExactly("status", "id");
      assertThat(jdbc.queryForList("EXPLAIN SELECT id FROM search_index_resource "
          + "FORCE INDEX (ix_search_resource_status) WHERE status IN ('CREATING','CREATE_UNKNOWN') "
          + "ORDER BY status,id LIMIT 11 FOR UPDATE"))
          .extracting(row -> row.get("key")).containsOnly("ix_search_resource_status");
    });
  }

  @Test
  void searchResourceUpgradeRejectsIncompatibleExistingObjectsWithoutReplacingThem() throws Exception {
    withDatabase(connection -> {
      executeScript(connection, repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.execute("DROP TABLE search_index_resource");
      jdbc.execute("CREATE TABLE search_index_resource (id INT PRIMARY KEY, marker VARCHAR(30)) ENGINE=InnoDB");
      jdbc.update("INSERT INTO search_index_resource VALUES (7,'preserve')");
      var before = jdbc.queryForList("SHOW CREATE TABLE search_index_resource");
      assertThatThrownBy(() -> executeScript(connection,
          repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20261008_03__search_index_retention.sql")))
          .hasRootCauseInstanceOf(SQLException.class).hasStackTraceContaining("incompatible");
      assertThat(jdbc.queryForList("SHOW CREATE TABLE search_index_resource")).isEqualTo(before);
      assertThat(jdbc.queryForObject("SELECT marker FROM search_index_resource WHERE id=7", String.class))
          .isEqualTo("preserve");
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"status", "kind"})
  void searchResourceUpgradeRejectsDifferentLifecycleChecksWithoutChangingRows(String check) throws Exception {
    withDatabase(connection -> {
      executeScript(connection, repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.update("INSERT INTO search_index_resource (id,index_name,scope_name,resource_kind,status) "
          + "VALUES (?,?,'scope','REBUILD','READY')", "I".repeat(26), "preserve-resource");
      String definition = check.equals("status")
          ? "status IN ('CREATING','CREATE_UNKNOWN','READY','RETIRING','DELETED','UNEXPECTED')"
          : "resource_kind IN ('BOOTSTRAP','REBUILD','LEGACY_APPROVED','LEGACY_ATTEMPT','UNEXPECTED')";
      jdbc.execute("ALTER TABLE search_index_resource DROP CHECK ck_search_resource_" + check
          + ", ADD CONSTRAINT ck_search_resource_" + check + " CHECK (" + definition + ")");
      var schema = jdbc.queryForList("SHOW CREATE TABLE search_index_resource");
      var rows = jdbc.queryForList("SELECT * FROM search_index_resource ORDER BY id");
      assertThatThrownBy(() -> executeScript(connection,
          repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20261008_03__search_index_retention.sql")))
          .hasRootCauseInstanceOf(SQLException.class)
          .hasStackTraceContaining("ck_search_resource_" + check + " expression incompatible");
      assertThat(jdbc.queryForList("SHOW CREATE TABLE search_index_resource")).isEqualTo(schema);
      assertThat(jdbc.queryForList("SELECT * FROM search_index_resource ORDER BY id")).isEqualTo(rows);
    });
  }

  @Test
  void searchResourceFailedMigrationCanBeRepairedAndRerunWithoutChangingSuccessfulHistory() throws Exception {
    withDatabase(connection -> {
      var flyway = Flyway.configure().dataSource(new SingleConnectionDataSource(connection, true))
          .locations("classpath:db/migration").load();
      flyway.migrate();
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.update("INSERT INTO search_index_resource (id,index_name,scope_name,resource_kind,status) "
          + "VALUES (?,?,'scope','REBUILD','CREATE_UNKNOWN')", "I".repeat(26), "partial-resource");
      var resources = jdbc.queryForList("SELECT * FROM search_index_resource ORDER BY id");
      var history = jdbc.queryForList("SELECT * FROM flyway_schema_history "
          + "WHERE version <> '2026100803' ORDER BY installed_rank");
      // 模拟 MySQL DDL 已提交、末尾元数据检查失败后留下的唯一失败记录。
      assertThat(jdbc.update("UPDATE flyway_schema_history SET success=0 WHERE version='2026100803'"))
          .isOne();
      jdbc.execute("ALTER TABLE search_index_resource DROP INDEX ix_search_resource_status");
      flyway.repair();
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
      assertThat(flyway.migrate().migrationsExecuted).isZero();
      assertThat(jdbc.queryForObject("SELECT success FROM flyway_schema_history "
          + "WHERE version='2026100803'", Boolean.class)).isTrue();
      assertThat(jdbc.queryForList("SELECT * FROM search_index_resource ORDER BY id")).isEqualTo(resources);
      assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history "
          + "WHERE version <> '2026100803' ORDER BY installed_rank")).isEqualTo(history);
    });
  }

  @Test
  void searchResourceUpgradeRejectsIncompatibleCapacityIndexWithoutReplacingIt() throws Exception {
    withDatabase(connection -> {
      executeScript(connection, repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.execute("ALTER TABLE search_index_resource DROP INDEX ix_search_resource_status, "
          + "ADD INDEX ix_search_resource_status (id,status)");
      var schema = jdbc.queryForList("SHOW CREATE TABLE search_index_resource");
      assertThatThrownBy(() -> executeScript(connection,
          repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20261008_03__search_index_retention.sql")))
          .hasRootCauseInstanceOf(SQLException.class).hasStackTraceContaining("capacity index incompatible");
      assertThat(jdbc.queryForList("SHOW CREATE TABLE search_index_resource")).isEqualTo(schema);
    });
  }

  @Test
  void realMapperCurrentCapacityReadSeesCommittedIntentDespiteAnEarlierRepeatableReadSnapshot() throws Exception {
    withDatabase(connection -> {
      executeScript(connection, repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.update("INSERT INTO search_projection_checkpoint (projection_name,last_created_at,last_snapshot_id) "
          + "VALUES ('RESUME_FACTS_APPROVED_INDEX',UTC_TIMESTAMP(6),?)", "0".repeat(26));
      try (var other = DriverManager.getConnection(connection.getMetaData().getURL(),
          requiredEnvironment("HR_DB_USER"), requiredEnvironment("HR_DB_PASSWORD"))) {
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        other.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        connection.setAutoCommit(false); other.setAutoCommit(false);
        var second = new JdbcTemplate(new SingleConnectionDataSource(other, true));
        // 两个不同历史任务入场先读业务记录，都建立空资源的旧 RR 快照。
        assertThat(jdbc.queryForObject("SELECT COUNT(id) FROM search_index_resource", Long.class)).isZero();
        assertThat(second.queryForObject("SELECT COUNT(id) FROM search_index_resource", Long.class)).isZero();
        jdbc.queryForList("SELECT projection_name FROM search_projection_checkpoint "
            + "WHERE projection_name='RESUME_FACTS_APPROVED_INDEX' FOR UPDATE");
        jdbc.update("INSERT INTO search_index_resource (id,index_name,scope_name,resource_kind,status) "
            + "VALUES (?,?,'scope','LEGACY_ATTEMPT','CREATING')", "I".repeat(26), "first-legacy-attempt");
        connection.commit();
        second.queryForList("SELECT projection_name FROM search_projection_checkpoint "
            + "WHERE projection_name='RESUME_FACTS_APPROVED_INDEX' FOR UPDATE");
        assertThat(second.queryForObject("SELECT COUNT(id) FROM search_index_resource", Long.class)).isZero();
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("rr-capacity", new JdbcTransactionFactory(),
            new SingleConnectionDataSource(other, true)));
        configuration.addMapper(SearchIndexResourceMapper.class);
        var sessions = new MybatisSqlSessionFactoryBuilder().build(configuration);
        try (var session = sessions.openSession(other)) {
          var mapper = session.getMapper(SearchIndexResourceMapper.class);
          assertThat(mapper.unresolvedCount(SearchIndexResourceStatus.CREATING.getCode(),
              SearchIndexResourceStatus.CREATE_UNKNOWN.getCode())).containsExactly("I".repeat(26));
          assertThat(mapper.liveCount(SearchIndexResourceStatus.DELETED.getCode())).containsExactly("I".repeat(26));
        }
        // 当前读已达到未决上限1，第二入场不得登记/发送任何创建。
        assertThat(jdbc.queryForObject("SELECT COUNT(id) FROM search_index_resource", Long.class)).isOne();
      } finally {
        connection.rollback(); connection.setAutoCommit(true);
      }
    });
  }

  @Test
  void realMapperTimestampPinsAndCandidateAgesRemainCoherentAcrossSessionTimeZones() throws Exception {
    withDatabase(connection -> {
      executeScript(connection, repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));
      String resourceId = UUID.randomUUID().toString().replace("-", "").substring(0, 26);
      String url = connection.getMetaData().getURL();
      try (var east = DriverManager.getConnection(url, requiredEnvironment("HR_DB_USER"), requiredEnvironment("HR_DB_PASSWORD"));
          var utc = DriverManager.getConnection(url, requiredEnvironment("HR_DB_USER"), requiredEnvironment("HR_DB_PASSWORD"))) {
        var eastJdbc = new JdbcTemplate(new SingleConnectionDataSource(east, true));
        var utcJdbc = new JdbcTemplate(new SingleConnectionDataSource(utc, true));
        eastJdbc.execute("SET SESSION time_zone='+08:00'"); utcJdbc.execute("SET SESSION time_zone='+00:00'");
        // 专用隔离连接；该例验证时区，旧 RR 快照的容量协议由独立用例覆盖。
        east.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        utc.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        east.setAutoCommit(false); utc.setAutoCommit(false);
        eastJdbc.update("INSERT INTO search_index_resource "
            + "(id,index_name,scope_name,resource_kind,status,managed,ready_at) "
            + "VALUES (?,?,'scope','BOOTSTRAP',?,1,TIMESTAMPADD(SECOND,-660,CURRENT_TIMESTAMP(6)))",
            resourceId, "cross-zone-" + resourceId, SearchIndexResourceStatus.READY.getCode());
        east.commit();
        try (var eastSession = resourceClockSessions(east).openSession(east);
            var utcSession = resourceClockSessions(utc).openSession(utc)) {
          var eastMapper = eastSession.getMapper(SearchIndexResourceMapper.class);
          var utcMapper = utcSession.getMapper(SearchIndexResourceMapper.class);
          String ready = SearchIndexResourceStatus.READY.getCode();
          String retiring = SearchIndexResourceStatus.RETIRING.getCode();
          assertThat(eastMapper.candidates(600, 8, ready, retiring)).extracting(row -> row.getId()).contains(resourceId);
          assertThat(utcMapper.candidates(600, 8, ready, retiring)).extracting(row -> row.getId()).contains(resourceId);
          assertThat(eastMapper.pin(resourceId, 120_000_000L, ready)).isOne(); eastSession.commit();
          assertThat(utcJdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,CURRENT_TIMESTAMP(6),pinned_until) "
              + "FROM search_index_resource WHERE id=?", Long.class, resourceId)).isBetween(90L, 120L);
          assertThat(utcMapper.retiring(resourceId, 0, "timezone-test", ready, retiring)).isZero(); utcSession.commit();
          String firstPin = utcJdbc.queryForObject("SELECT CAST(UNIX_TIMESTAMP(pinned_until) AS CHAR) "
              + "FROM search_index_resource WHERE id=?", String.class, resourceId);
          assertThat(utcMapper.pin(resourceId, 1_000_000L, ready)).isOne(); utcSession.commit();
          assertThat(eastJdbc.queryForObject("SELECT CAST(UNIX_TIMESTAMP(pinned_until) AS CHAR) "
              + "FROM search_index_resource WHERE id=?", String.class, resourceId)).isEqualTo(firstPin);
          assertThat(utcMapper.pin(resourceId, 120_000_000L, ready)).isOne(); utcSession.commit();
          assertThat(eastJdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,CURRENT_TIMESTAMP(6),pinned_until) "
              + "FROM search_index_resource WHERE id=?", Long.class, resourceId)).isBetween(90L, 120L);
          assertThat(eastMapper.retiring(resourceId, 0, "timezone-test", ready, retiring)).isZero(); eastSession.commit();
          // 仅让隔离夹具的精确资源到期，验证同版本 CAS 和审计 TIMESTAMP 也沿用会话时钟。
          utcJdbc.update("UPDATE search_index_resource SET pinned_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP(6)) WHERE id=?", resourceId);
          utc.commit();
          assertThat(eastMapper.retiring(resourceId, 0, "timezone-test", ready, retiring)).isOne(); eastSession.commit();
          assertThat(utcJdbc.queryForObject("SELECT ABS(TIMESTAMPDIFF(SECOND,retired_at,CURRENT_TIMESTAMP(6))) "
              + "FROM search_index_resource WHERE id=?", Long.class, resourceId)).isLessThanOrEqualTo(30L);
          assertThat(utcJdbc.queryForObject("SELECT version FROM search_index_resource WHERE id=?", Long.class, resourceId)).isOne();
        }
      }
    });
  }

  private static SqlSessionFactory resourceClockSessions(Connection connection) {
    var configuration = new MybatisConfiguration();
    configuration.setEnvironment(new Environment("resource-clock", new JdbcTransactionFactory(),
        new SingleConnectionDataSource(connection, true)));
    configuration.addMapper(SearchIndexResourceMapper.class);
    return new MybatisSqlSessionFactoryBuilder().build(configuration);
  }

  @Test
  void candidateMergeHistoryIndexesMatchEmptyBaselineAndRerunWithoutSchemaChanges() throws Exception {
    withDatabase(connection -> {
      executeScript(connection, repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      var before = new ArrayList<Map<String, Object>>();
      for (String[] index : candidateMergeIndexes()) before.addAll(jdbc.queryForList("SHOW CREATE TABLE " + index[0]));
      Path upgrade = repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20261008_02__candidate_merge_history_indexes.sql");
      executeScript(connection, upgrade);
      executeScript(connection, upgrade);
      var after = new ArrayList<Map<String, Object>>();
      for (String[] index : candidateMergeIndexes()) {
        assertCandidateMergeIndex(jdbc, index);
        after.addAll(jdbc.queryForList("SHOW CREATE TABLE " + index[0]));
      }
      assertThat(after).isEqualTo(before);
    });
  }

  @Test
  void candidateMergeHistoryIndexesPreserveHistoricalRowsAndPartialCompletionOnRerun() throws Exception {
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      createCandidateMergeIndexHistory(jdbc);
      String[] first = candidateMergeIndexes()[0];
      jdbc.execute("ALTER TABLE " + first[0] + " ADD INDEX " + first[1] + " (candidate_id)");
      var before = candidateMergeHistoricalRows(jdbc);
      Path upgrade = repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20261008_02__candidate_merge_history_indexes.sql");
      executeScript(connection, upgrade);
      executeScript(connection, upgrade);
      for (String[] index : candidateMergeIndexes()) assertCandidateMergeIndex(jdbc, index);
      assertThat(candidateMergeHistoricalRows(jdbc)).isEqualTo(before);
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"matching_candidate_selection_item", "matching_match_run_item_outcome",
      "matching_weighted_result", "submission_client_restriction_current"})
  void candidateMergeHistoryIndexUpgradeRejectsIncompatibleObjectsBeforeAnyDdl(String table) throws Exception {
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      createCandidateMergeIndexHistory(jdbc);
      String indexName = null;
      for (String[] index : candidateMergeIndexes()) if (index[0].equals(table)) indexName = index[1];
      jdbc.execute("ALTER TABLE " + table + " ADD INDEX " + indexName + " (id,candidate_id)");
      var before = candidateMergeHistoricalRows(jdbc);
      var definitions = new ArrayList<Map<String, Object>>();
      for (String[] index : candidateMergeIndexes()) definitions.addAll(jdbc.queryForList("SHOW CREATE TABLE " + index[0]));
      Path upgrade = repositoryRoot().resolve("docs/sql/hr_ai_upgrade_20261008_02__candidate_merge_history_indexes.sql");
      assertThatThrownBy(() -> executeScript(connection, upgrade)).hasRootCauseInstanceOf(SQLException.class)
          .hasStackTraceContaining("incompatible");
      var after = new ArrayList<Map<String, Object>>();
      for (String[] index : candidateMergeIndexes()) after.addAll(jdbc.queryForList("SHOW CREATE TABLE " + index[0]));
      assertThat(after).isEqualTo(definitions);
      assertThat(candidateMergeHistoricalRows(jdbc)).isEqualTo(before);
    });
  }

  private static String[][] candidateMergeIndexes() {
    return new String[][] {
        {"matching_candidate_selection_item", "ix_matching_selection_candidate"},
        {"matching_match_run_item_outcome", "ix_matching_item_outcome_candidate"},
        {"matching_weighted_result", "ix_matching_result_candidate"},
        {"submission_client_restriction_current", "ix_client_restriction_current_candidate"}
    };
  }

  private static void createCandidateMergeIndexHistory(JdbcTemplate jdbc) {
    // 仅重建升级所需历史表形状；业务数据保留断言不依赖空库完整基线的外键夹具。
    for (String[] index : candidateMergeIndexes()) {
      jdbc.execute("CREATE TABLE " + index[0] + " (id VARCHAR(26) PRIMARY KEY, "
          + "candidate_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, "
          + "payload VARCHAR(100) NOT NULL) ENGINE=InnoDB");
      jdbc.update("INSERT INTO " + index[0] + " (id,candidate_id,payload) VALUES (?,?,?)",
          "historical", "merged-candidate", "保留历史业务事实");
    }
  }

  private static List<Map<String, Object>> candidateMergeHistoricalRows(JdbcTemplate jdbc) {
    var rows = new ArrayList<Map<String, Object>>();
    for (String[] index : candidateMergeIndexes()) rows.addAll(jdbc.queryForList("SELECT * FROM " + index[0] + " ORDER BY id"));
    return rows;
  }

  private static void assertCandidateMergeIndex(JdbcTemplate jdbc, String[] index) {
    assertThat(jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.STATISTICS "
        + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=? ORDER BY SEQ_IN_INDEX",
        String.class, index[0], index[1])).containsExactly("candidate_id");
  }

  @Test
  void searchRecoveryUpgradePreservesFailedTasksAndRerunsWithoutReplacingRecoveryHistory() throws Exception {
    withDatabase(connection -> {
      Path sqlRoot = repositoryRoot().resolve("docs/sql");
      Path upgrade = legacyUpgrade("hr_ai_upgrade_20260908_08__search_task_recovery.sql");
      executeScript(connection, sqlRoot.resolve("hr_ai_complete_schema.sql"));
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      var definition = jdbc.queryForList("SHOW CREATE TABLE reporting_search_task_recovery");
      insertOutboxUpgradeTask(jdbc, "recovery-old", "OUTBOX_PUBLISH");
      insertOutboxUpgradeTask(jdbc, "recovery-work", "RESUME_SEARCH_PROJECT");
      insertOutboxUpgradeTask(jdbc, "recovery-new", "OUTBOX_PUBLISH");
      jdbc.update("UPDATE reporting_async_task SET status='FAILED',attempt_count=5,finished_at='2026-09-08 01:02:03.123456' WHERE id='recovery-old'");
      jdbc.update("INSERT INTO reporting_outbox_event(id,work_task_id,work_task_type,event_type,event_version,aggregate_type,aggregate_id,occurred_at,trace_id,actor_user_id,owner_user_id,owner_department_id)"
          + " VALUES('recovery-old','recovery-work','RESUME_SEARCH_PROJECT','TASK_AVAILABLE',1,'CANDIDATE','synthetic-candidate',"
          + "'2026-09-08 01:02:03.123456','synthetic-recovery-trace','actor','actor','department')");
      var tasks = jdbc.queryForList("SELECT * FROM reporting_async_task ORDER BY id");
      var events = jdbc.queryForList("SELECT * FROM reporting_outbox_event ORDER BY id");
      jdbc.execute("DROP TABLE reporting_search_task_recovery");
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForList("SHOW CREATE TABLE reporting_search_task_recovery")).isEqualTo(definition);
      jdbc.update("INSERT INTO reporting_search_task_recovery(id,original_task_id,outbox_event_id,reason,requested_by_user_id)"
          + " VALUES('recovery-new','recovery-old','recovery-old','保留人工原因','actor')");
      var history = jdbc.queryForList("SELECT * FROM reporting_search_task_recovery");
      executeScript(connection, upgrade);
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForList("SELECT * FROM reporting_search_task_recovery")).isEqualTo(history);
      assertThat(jdbc.queryForList("SELECT * FROM reporting_async_task ORDER BY id")).isEqualTo(tasks);
      assertThat(jdbc.queryForList("SELECT * FROM reporting_outbox_event ORDER BY id")).isEqualTo(events);
      assertThatThrownBy(() -> jdbc.update("DELETE FROM reporting_async_task WHERE id='recovery-old'"))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> jdbc.update("DELETE FROM reporting_outbox_event WHERE id='recovery-old'"))
          .isInstanceOf(DataIntegrityViolationException.class);
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "ALTER TABLE reporting_search_task_recovery MODIFY reason VARCHAR(499) NOT NULL",
      "ALTER TABLE reporting_search_task_recovery DROP FOREIGN KEY fk_reporting_search_recovery_original, DROP INDEX uk_reporting_search_recovery_original",
      "ALTER TABLE reporting_search_task_recovery DROP FOREIGN KEY fk_reporting_search_recovery_event"})
  void searchRecoveryUpgradeRejectsAnIncompatibleTableWithoutChangingIt(String damage) throws Exception {
    withDatabase(connection -> {
      executeScript(connection, repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.execute(damage);
      var before = jdbc.queryForList("SHOW CREATE TABLE reporting_search_task_recovery");
      assertThatThrownBy(() -> executeScript(connection, legacyUpgrade("hr_ai_upgrade_20260908_08__search_task_recovery.sql")))
          .isInstanceOf(ScriptException.class).rootCause().isInstanceOfSatisfying(SQLException.class, failure -> {
            assertThat(failure.getErrorCode()).isEqualTo(1054);
            assertThat(failure.getMessage()).contains("incompatible");
          });
      assertThat(jdbc.queryForList("SHOW CREATE TABLE reporting_search_task_recovery")).isEqualTo(before);
    });
  }

  @Test
  void searchStateReplayUpgradeMatchesBaselineAndPreservesHistoryOnRerun() throws Exception {
    Path sqlRoot = repositoryRoot().resolve("docs/sql");
    Path upgrade = legacyUpgrade("hr_ai_upgrade_20260908_07__search_state_replay.sql");
    withDatabase(connection -> {
      executeScript(connection, sqlRoot.resolve("hr_ai_complete_schema.sql"));
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      var definition = jdbc.queryForList("SHOW CREATE TABLE search_state_replay_checkpoint");
      jdbc.update("INSERT INTO search_projection_checkpoint(projection_name,index_name,last_created_at,last_snapshot_id,projected_count,version)"
          + " VALUES('RESUME_FACTS','historical-index','2026-09-08 01:02:03.123456','historical-snapshot',17,9)");
      var history = jdbc.queryForList("SELECT * FROM search_projection_checkpoint");
      jdbc.execute("DROP TABLE search_state_replay_checkpoint");
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForList("SHOW CREATE TABLE search_state_replay_checkpoint")).isEqualTo(definition);
      assertThat(jdbc.queryForList("SELECT * FROM search_projection_checkpoint")).isEqualTo(history);
      // 原子 CREATE 已完成、后续步骤中断，以及已有新版游标后的重跑，均不得清零。
      jdbc.update("INSERT INTO search_state_replay_checkpoint(projection_name,index_name,last_candidate_id,completed,projected_count,version)"
          + " VALUES('RESUME_FACTS','replayed-index','historical-candidate',TRUE,37,11)");
      var progress = jdbc.queryForList("SELECT * FROM search_state_replay_checkpoint");
      executeScript(connection, upgrade);
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForList("SELECT * FROM search_state_replay_checkpoint")).isEqualTo(progress);
      assertThat(jdbc.queryForList("SELECT * FROM search_projection_checkpoint")).isEqualTo(history);
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"ALTER TABLE search_state_replay_checkpoint MODIFY index_name VARCHAR(120) NOT NULL",
      "ALTER TABLE search_state_replay_checkpoint MODIFY last_candidate_id CHAR(26) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL",
      "ALTER TABLE search_state_replay_checkpoint DROP PRIMARY KEY"})
  void searchStateReplayUpgradeRejectsIncompatibleExistingTableWithoutChangingIt(String damage) throws Exception {
    withDatabase(connection -> {
      executeScript(connection, repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.execute(damage);
      var definition = jdbc.queryForList("SHOW CREATE TABLE search_state_replay_checkpoint");
      assertThatThrownBy(() -> executeScript(connection, legacyUpgrade("hr_ai_upgrade_20260908_07__search_state_replay.sql")))
          .isInstanceOf(ScriptException.class).rootCause().isInstanceOfSatisfying(SQLException.class, failure -> {
            assertThat(failure.getErrorCode()).isEqualTo(1054);
            assertThat(failure.getMessage()).contains("incompatible");
          });
      assertThat(jdbc.queryForList("SHOW CREATE TABLE search_state_replay_checkpoint")).isEqualTo(definition);
    });
  }

  @Test
  void applicationStageRangeQueryUsesBoundedKeysetPagesOnRealMySql() throws Exception {
    withDatabase(connection -> {
      var dataSource = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(dataSource);
      // 仅验证实际 MyBatis 分页和驱动边界；招聘流程创建与外键链由业务集成测试覆盖。
      jdbc.execute("CREATE TABLE recruitment_application (id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,"
          + "candidate_id CHAR(26),demand_id CHAR(26),jd_id CHAR(26),jd_version_id CHAR(26),resume_version_id CHAR(26),"
          + "workflow_template_version_id CHAR(26),match_plan_version_id CHAR(26),recruitment_type VARCHAR(20),"
          + "progressive_follow_up BOOLEAN NOT NULL DEFAULT FALSE,"
          + "current_stage VARCHAR(32),outcome VARCHAR(16),owner_user_id CHAR(26),owner_department_id CHAR(26),"
          + "active_slot TINYINT,terminal_at DATETIME(6),version INT,ext TEXT,remark VARCHAR(255),"
          + "create_id CHAR(26),create_name VARCHAR(64),create_time TIMESTAMP(6),"
          + "update_id CHAR(26),update_name VARCHAR(64),update_time TIMESTAMP(6)) ENGINE=InnoDB");
      Instant from = Instant.parse("2026-09-06T16:00:00Z");
      Instant to = from.plusSeconds(86400);
      var rows = new ArrayList<Object[]>();
      for (int index = 0; index < 205; index++) {
        rows.add(new Object[] {"row-" + String.format("%04d", index), "jd-a", "v-a", Timestamp.from(from.plusSeconds(index))});
      }
      rows.add(new Object[] {"wrong-jd", "jd-b", "v-a", Timestamp.from(from)});
      rows.add(new Object[] {"wrong-version", "jd-a", "v-b", Timestamp.from(from)});
      rows.add(new Object[] {"before-range", "jd-a", "v-a", Timestamp.from(from.minusSeconds(1))});
      rows.add(new Object[] {"exclusive-end", "jd-a", "v-a", Timestamp.from(to)});
      jdbc.batchUpdate("INSERT INTO recruitment_application(id,jd_id,jd_version_id,create_time) VALUES(?,?,?,?)", rows);
      var configuration = new MybatisConfiguration();
      configuration.setMapUnderscoreToCamelCase(true);
      configuration.setEnvironment(new Environment("stage-query-test", new JdbcTransactionFactory(), dataSource));
      var pagination = new MybatisPlusInterceptor();
      pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
      configuration.addInterceptor(pagination);
      configuration.addMapper(ApplicationMapper.class);
      try (var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession()) {
        var service = new ApplicationPersistenceServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", session.getMapper(ApplicationMapper.class));
        var first = service.findCreatedInRange(from, to, "jd-a", "v-a", null, Integer.MAX_VALUE);
        assertThat(first).hasSize(200);
        assertThat(first.getFirst().getId()).isEqualTo("row-0000");
        assertThat(first.getLast().getId()).isEqualTo("row-0199");
        var second = service.findCreatedInRange(from, to, "jd-a", "v-a", first.getLast().getId(), 200);
        assertThat(second).extracting(Application::getId).containsExactly("row-0200", "row-0201", "row-0202", "row-0203", "row-0204");
        assertThat(service.findCreatedInRange(from, to, "jd-a", "v-a", second.getLast().getId(), 200)).isEmpty();
        assertThat(service.findCreatedInRange(from, to, "jd-a", "v-a", null, 0)).hasSize(1);
      }
    });
  }

  @Test
  void outboxAndDelayedRetryUpgradePreserveHistoryAndResumePartialDeployment() throws Exception {
    Path sqlRoot = repositoryRoot().resolve("docs/sql");
    Path upgrade = legacyUpgrade("hr_ai_upgrade_20260908_05__transactional_task_outbox.sql");
    withDatabase(connection -> {
      executeScript(connection, sqlRoot.resolve("hr_ai_complete_schema.sql"));
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      var baselineColumns = outboxColumnMetadata(jdbc);
      insertOutboxUpgradeTask(jdbc, "outbox-publication", "OUTBOX_PUBLISH");
      insertOutboxUpgradeTask(jdbc, "outbox-work", "RESUME_SEARCH_PROJECT");
      var originalTasks = jdbc.queryForList(TASK_SNAPSHOT);
      // 构造 SQL05 发布前的历史结构时，先移除其后 SQL08 的空关联表及外键。
      jdbc.execute("DROP TABLE reporting_search_task_recovery");
      jdbc.execute("DROP TABLE reporting_outbox_event");
      jdbc.execute("ALTER TABLE reporting_async_task DROP INDEX ix_reporting_task_retry");
      jdbc.execute("ALTER TABLE reporting_async_task DROP COLUMN next_retry_at");
      executeScript(connection, upgrade);
      assertThat(outboxColumnMetadata(jdbc)).isEqualTo(baselineColumns);
      assertThat(jdbc.queryForList(TASK_SNAPSHOT)).isEqualTo(originalTasks);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reporting_async_task WHERE next_retry_at IS NOT NULL", Integer.class)).isZero();
      jdbc.update("INSERT INTO reporting_outbox_event (id,work_task_id,work_task_type,event_type,event_version,"
          + "aggregate_type,aggregate_id,occurred_at,trace_id,actor_user_id,owner_user_id,owner_department_id)"
          + " VALUES('outbox-publication','outbox-work','RESUME_SEARCH_PROJECT','TASK_AVAILABLE',1,"
          + "'CANDIDATE','synthetic-candidate','2026-09-08 01:02:03.123456','synthetic-trace-0123456789','actor','actor','department')");
      jdbc.update("UPDATE reporting_async_task SET next_retry_at='2026-09-09 01:02:03.123456' WHERE id='outbox-publication'");
      var events = jdbc.queryForList("SELECT * FROM reporting_outbox_event ORDER BY id");
      var tasks = jdbc.queryForList("SELECT * FROM reporting_async_task ORDER BY id");
      executeScript(connection, upgrade);
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForList("SELECT * FROM reporting_outbox_event ORDER BY id")).isEqualTo(events);
      assertThat(jdbc.queryForList("SELECT * FROM reporting_async_task ORDER BY id")).isEqualTo(tasks);
      // 模拟字段和信封已完成、索引步骤尚未完成的中断，重跑只补索引。
      jdbc.execute("ALTER TABLE reporting_async_task DROP INDEX ix_reporting_task_retry");
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForList("SELECT * FROM reporting_async_task ORDER BY id")).isEqualTo(tasks);
      assertThat(jdbc.queryForList("SELECT * FROM reporting_outbox_event ORDER BY id")).isEqualTo(events);
      assertThat(jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()"
          + " AND TABLE_NAME='reporting_async_task' AND INDEX_NAME='ix_reporting_task_retry' ORDER BY SEQ_IN_INDEX", String.class))
          .containsExactly("task_type", "status", "next_retry_at", "create_time", "id");
      assertThatThrownBy(() -> jdbc.update("DELETE FROM reporting_async_task WHERE id='outbox-work'"))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> jdbc.update("UPDATE reporting_outbox_event SET event_version=0"))
          .rootCause().isInstanceOfSatisfying(SQLException.class, failure -> assertThat(failure.getErrorCode()).isEqualTo(3819));
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"retry-type", "retry-index", "event-type", "event-constraint"})
  void outboxUpgradeRejectsIncompatibleMetadataWithoutOverwritingHistory(String mismatch) throws Exception {
    withDatabase(connection -> {
      executeScript(connection, repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      insertOutboxUpgradeTask(jdbc, "outbox-work", "RESUME_SEARCH_PROJECT");
      switch (mismatch) {
        case "retry-type" -> jdbc.execute("ALTER TABLE reporting_async_task MODIFY COLUMN next_retry_at VARCHAR(64) NULL");
        case "retry-index" -> {
          jdbc.execute("ALTER TABLE reporting_async_task DROP INDEX ix_reporting_task_retry");
          jdbc.execute("ALTER TABLE reporting_async_task ADD INDEX ix_reporting_task_retry (status,task_type,next_retry_at,create_time,id)");
        }
        case "event-type" -> jdbc.execute("ALTER TABLE reporting_outbox_event MODIFY COLUMN trace_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL");
        case "event-constraint" -> jdbc.execute("ALTER TABLE reporting_outbox_event DROP CHECK ck_reporting_outbox_protocol");
        default -> throw new AssertionError("unknown synthetic metadata mismatch");
      }
      var rows = jdbc.queryForList("SELECT * FROM reporting_async_task ORDER BY id");
      var taskDefinition = jdbc.queryForList("SHOW CREATE TABLE reporting_async_task");
      var eventDefinition = jdbc.queryForList("SHOW CREATE TABLE reporting_outbox_event");
      assertThatThrownBy(() -> executeScript(connection, legacyUpgrade("hr_ai_upgrade_20260908_05__transactional_task_outbox.sql")))
          .isInstanceOf(ScriptException.class).rootCause().isInstanceOfSatisfying(SQLException.class, failure -> {
            assertThat(failure.getErrorCode()).isEqualTo(1054);
            assertThat(failure.getMessage()).contains("incompatible");
          });
      assertThat(jdbc.queryForList("SELECT * FROM reporting_async_task ORDER BY id")).isEqualTo(rows);
      assertThat(jdbc.queryForList("SHOW CREATE TABLE reporting_async_task")).isEqualTo(taskDefinition);
      assertThat(jdbc.queryForList("SHOW CREATE TABLE reporting_outbox_event")).isEqualTo(eventDefinition);
    });
  }

  private static void insertOutboxUpgradeTask(JdbcTemplate jdbc, String id, String taskType) {
    jdbc.update("INSERT INTO reporting_async_task (id,task_type,resource_type,resource_id,requested_by_user_id,"
        + "owner_user_id,owner_department_id,view_permission,manage_permission,status,max_attempts,idempotency_key,"
        + "request_fingerprint) VALUES(?,?,'CANDIDATE','synthetic-candidate','actor','actor','department',"
        + "'talent.read','talent.manage','QUEUED',5,?,?)", id, taskType, id, "a".repeat(64));
  }

  private static List<Map<String, Object>> outboxColumnMetadata(JdbcTemplate jdbc) {
    return jdbc.queryForList("SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,CHARACTER_SET_NAME,COLLATION_NAME,"
        + "COLUMN_KEY,EXTRA,COLUMN_COMMENT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()"
        + " AND TABLE_NAME='reporting_outbox_event' ORDER BY COLUMN_NAME");
  }

  @Test
  void nestedTemporaryDatabasePreservesSourceSchemaAndLeaseCanary() throws Exception {
    withDatabase(source -> {
      var parent = new JdbcTemplate(new SingleConnectionDataSource(source, true));
      parent.execute("CREATE TABLE isolation_probe (id INT PRIMARY KEY, lease_until TIMESTAMP(6))");
      parent.update("INSERT INTO isolation_probe VALUES (1,'2030-01-01 00:00:00')");
      var before = parent.queryForList("SELECT * FROM isolation_probe");
      withDatabase(source.getMetaData().getURL(), isolated -> {
        try (Statement statement = isolated.createStatement()) {
          statement.execute("CREATE TABLE isolation_probe (id INT PRIMARY KEY, lease_until TIMESTAMP(6))");
          statement.execute("ALTER TABLE isolation_probe DROP COLUMN lease_until");
        }
      });
      assertThat(parent.queryForList("SELECT * FROM isolation_probe")).isEqualTo(before);
    });
  }

  private static final String TASK_SNAPSHOT =
      "SELECT id,status,attempt_count,max_attempts,version,started_at,finished_at,"
          + "request_fingerprint,create_time,update_time FROM reporting_async_task ORDER BY id";
  private static final String RECOVERY_QUERY =
      "SELECT id,task_type,status,attempt_count,max_attempts FROM reporting_async_task"
          + " WHERE task_type='LEASE_TEST' AND status IN ('RUNNING','CANCELLING')"
          + " AND (lease_until IS NULL OR lease_until<='2026-09-07 00:00:00')"
          + " ORDER BY lease_until,id LIMIT 10 FOR UPDATE SKIP LOCKED";

  @Test
  void baselineAndLeaseUpgradePreserveHistoryAndSupportPartialCompletionAndReruns()
      throws Exception {
    Path sqlRoot = repositoryRoot().resolve("docs/sql");
    Path upgrade = legacyUpgrade("hr_ai_upgrade_20260907_01__async_task_lease.sql");
    withDatabase(connection -> {
      try (Statement statement = connection.createStatement()) {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        statement.execute("ALTER DATABASE CHARACTER SET utf8mb3 COLLATE utf8mb3_bin");
        executeScript(connection, sqlRoot.resolve("hr_ai_complete_schema.sql"));
        Integer businessTableCount =
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES"
                    + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE'",
                Integer.class);
        assertThat(businessTableCount).isPositive();
        assertThat(
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES"
                        + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE'"
                        + " AND TABLE_COLLATION='utf8mb4_0900_ai_ci'",
                    Integer.class))
            .isEqualTo(businessTableCount);
        executeScript(connection, legacyUpgrade("hr_ai_upgrade_20260908_04__expired_resume_model_history.sql"));
        executeScript(connection, legacyUpgrade("hr_ai_upgrade_20260908_04__expired_resume_model_history.sql"));
        executeScript(connection, legacyUpgrade("hr_ai_upgrade_20260908_03__queued_cancel_finished_time.sql"));
        executeScript(connection, legacyUpgrade("hr_ai_upgrade_20260908_03__queued_cancel_finished_time.sql"));
        executeScript(connection, legacyUpgrade("hr_ai_upgrade_20260908_02__search_checkpoint_index.sql"));
        executeScript(connection, legacyUpgrade("hr_ai_upgrade_20260908_02__search_checkpoint_index.sql"));
        assertFeedbackLengths(jdbc, 1000, 500);
        executeScript(connection, legacyUpgrade("hr_ai_upgrade_20260908_01__interview_feedback_lengths.sql"));
        executeScript(connection, legacyUpgrade("hr_ai_upgrade_20260908_01__interview_feedback_lengths.sql"));
        assertFeedbackLengths(jdbc, 1000, 500);
        assertInterviewHistoryIndexes(jdbc);
        executeScript(connection, legacyUpgrade("hr_ai_upgrade_20260907_03__interview_history_pagination.sql"));
        executeScript(connection, legacyUpgrade("hr_ai_upgrade_20260907_03__interview_history_pagination.sql"));
        assertInterviewHistoryIndexes(jdbc);
        assertThat(
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()",
                    Integer.class))
            .isEqualTo(businessTableCount);

        for (String status : new String[] {"RUNNING", "CANCELLING", "SUCCEEDED"}) {
          jdbc.update(
              "INSERT INTO reporting_async_task (id,task_type,resource_type,resource_id,"
                  + "requested_by_user_id,owner_user_id,owner_department_id,view_permission,"
                  + "manage_permission,status,attempt_count,max_attempts,idempotency_key,"
                  + "request_fingerprint,started_at)"
                  + " VALUES (?,'LEASE_TEST','TEST','resource','actor','owner','department',"
                  + "'test:view','test:manage',?,1,3,?,?,'2026-09-01 00:00:00')",
              status,
              status,
              status,
              "a".repeat(64));
        }

        var history = jdbc.queryForList(TASK_SNAPSHOT);
        executeScript(connection, upgrade);
        executeScript(connection, upgrade);
        assertLeaseIndex(jdbc);
        assertThat(jdbc.queryForList(TASK_SNAPSHOT)).isEqualTo(history);

        // 在本测试刚创建的数据库内还原租约功能上线前结构，证明原查询确实因缺列失败。
        statement.execute("ALTER TABLE reporting_async_task DROP INDEX ix_reporting_task_lease");
        statement.execute("ALTER TABLE reporting_async_task DROP COLUMN lease_until");
        assertThatThrownBy(() -> statement.executeQuery(RECOVERY_QUERY))
            .isInstanceOfSatisfying(
                SQLException.class, error -> assertThat(error.getErrorCode()).isEqualTo(1054));

        executeScript(connection, upgrade);
        assertThat(jdbc.queryForList(TASK_SNAPSHOT)).isEqualTo(history);
        assertThat(jdbc.queryForList(RECOVERY_QUERY)).hasSize(2);
        assertThat(
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM reporting_async_task WHERE lease_until IS NULL",
                    Integer.class))
            .isEqualTo(3);
        assertLeaseIndex(jdbc);

        // 模拟仅完成字段新增；接续和重复执行必须保留已有租约值。
        jdbc.update(
            "UPDATE reporting_async_task SET lease_until='2026-09-08 00:00:00' WHERE id='RUNNING'");
        var leases = jdbc.queryForList("SELECT id,lease_until FROM reporting_async_task ORDER BY id");
        history = jdbc.queryForList(TASK_SNAPSHOT);
        statement.execute("ALTER TABLE reporting_async_task DROP INDEX ix_reporting_task_lease");
        executeScript(connection, upgrade);
        executeScript(connection, upgrade);
        assertLeaseIndex(jdbc);
        assertThat(jdbc.queryForList(TASK_SNAPSHOT)).isEqualTo(history);
        assertThat(jdbc.queryForList("SELECT id,lease_until FROM reporting_async_task ORDER BY id"))
            .isEqualTo(leases);
        assertThat(jdbc.queryForList(RECOVERY_QUERY)).hasSize(1);
      }
    });
  }

  @Test
  void exportObjectRegistryUpgradePreservesHistoryAndSupportsReruns() throws Exception {
    Path upgrade = legacyUpgrade("hr_ai_upgrade_20260907_07__export_object_registry.sql");
    withDatabase(connection -> {
      try (Statement statement = connection.createStatement()) {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        statement.execute("CREATE TABLE reporting_export_job (id CHAR(26) CHARACTER SET ascii"
            + " COLLATE ascii_bin PRIMARY KEY, payload VARCHAR(100) NOT NULL) ENGINE=InnoDB");
        String jobId = "J".repeat(26);
        String objectId = "O".repeat(26);
        String objectKey = "exports/" + jobId + "/attempt-1/part-00001.csv";
        jdbc.update("INSERT INTO reporting_export_job VALUES (?,?)", jobId, "历史导出任务");
        var jobs = jdbc.queryForList("SELECT id,payload FROM reporting_export_job ORDER BY id");
        executeScript(connection, upgrade);
        jdbc.update("INSERT INTO reporting_export_object (id,export_job_id,object_key,cleanup_after)"
            + " VALUES (?,?,?,'2026-09-14 00:00:00')", objectId, jobId, objectKey);
        var objects = jdbc.queryForList(
            "SELECT id,export_job_id,object_key,cleanup_after,version FROM reporting_export_object ORDER BY id");

        executeScript(connection, upgrade);
        executeScript(connection, upgrade);

        assertThat(jdbc.queryForList("SELECT id,payload FROM reporting_export_job ORDER BY id")).isEqualTo(jobs);
        assertThat(jdbc.queryForList(
            "SELECT id,export_job_id,object_key,cleanup_after,version FROM reporting_export_object ORDER BY id"))
            .isEqualTo(objects);
        assertThat(jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.STATISTICS"
            + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_export_object'"
            + " AND INDEX_NAME='ix_reporting_export_object_cleanup' ORDER BY SEQ_IN_INDEX", String.class))
            .containsExactly("cleanup_after", "id");
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO reporting_export_object (id,export_job_id,object_key,cleanup_after)"
                + " VALUES (?,?,?,'2026-09-14 00:00:00')", "P".repeat(26), jobId, objectKey))
            .isInstanceOf(DataIntegrityViolationException.class);
      }
    });
  }

  @Test
  void interviewHistoryIndexUpgradePreservesLegacyRowsAndResumesAfterOneIndex() throws Exception {
    Path upgrade = legacyUpgrade("hr_ai_upgrade_20260907_03__interview_history_pagination.sql");
    withDatabase(connection -> {
      try (Statement statement = connection.createStatement()) {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        // 最小历史夹具保留本次索引依赖的原始列类型；完整基线另由上面的空库路径验证。
        for (String table : new String[] {"interview_feedback", "interview_notification_intent"}) {
          statement.execute("CREATE TABLE " + table + " (id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,"
              + " interview_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,"
              + " create_time TIMESTAMP(6) NOT NULL, payload TEXT NOT NULL) ENGINE=InnoDB");
          for (int index = 0; index < 3; index++) {
            jdbc.update("INSERT INTO " + table + " VALUES (?,?,'2026-09-01 00:00:00',?)",
                String.format("%026d", index), "I".repeat(26), "历史事实 " + index);
          }
        }
        var feedbackRows = jdbc.queryForList("SELECT * FROM interview_feedback ORDER BY id");
        var notificationRows = jdbc.queryForList("SELECT * FROM interview_notification_intent ORDER BY id");
        executeScript(connection, upgrade);
        assertInterviewHistoryIndexes(jdbc);
        // 模拟第一项索引已完成，第二项尚未完成的中断位置。
        statement.execute("ALTER TABLE interview_notification_intent DROP INDEX ix_interview_notification_page");
        executeScript(connection, upgrade);
        executeScript(connection, upgrade);
        assertInterviewHistoryIndexes(jdbc);
        assertThat(jdbc.queryForList("SELECT * FROM interview_feedback ORDER BY id")).isEqualTo(feedbackRows);
        assertThat(jdbc.queryForList("SELECT * FROM interview_notification_intent ORDER BY id")).isEqualTo(notificationRows);
      }
    });
  }

  @Test
  void feedbackLengthUpgradePreservesHistoryResumesAndNeverShrinksWiderColumns() throws Exception {
    Path upgrade = legacyUpgrade("hr_ai_upgrade_20260908_01__interview_feedback_lengths.sql");
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.execute("CREATE TABLE interview_feedback_version (id INT PRIMARY KEY,"
          + " short_comment VARCHAR(500) COLLATE utf8mb4_bin NULL COMMENT '历史意见',"
          + " external_feedback_source VARCHAR(120) COLLATE utf8mb4_unicode_ci NULL COMMENT '历史来源')"
          + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
      jdbc.update("INSERT INTO interview_feedback_version VALUES (1,?,?),(2,NULL,NULL)",
          "评".repeat(500), "源".repeat(120));
      var history = jdbc.queryForList("SELECT * FROM interview_feedback_version ORDER BY id");
      var metadata = feedbackColumnMetadata(jdbc);
      assertThatThrownBy(() -> jdbc.update("INSERT INTO interview_feedback_version VALUES (3,?,NULL)",
          "评".repeat(1000))).isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> jdbc.update("INSERT INTO interview_feedback_version VALUES (3,NULL,?)",
          "源".repeat(500))).isInstanceOf(DataIntegrityViolationException.class);

      executeScript(connection, upgrade);
      assertFeedbackLengths(jdbc, 1000, 500);
      assertThat(jdbc.queryForList("SELECT * FROM interview_feedback_version ORDER BY id")).isEqualTo(history);
      assertThat(feedbackColumnMetadata(jdbc)).isEqualTo(metadata);
      // 仅在本用例临时库恢复第二列旧容量，模拟第一列已经完成的中断状态。
      jdbc.execute("ALTER TABLE interview_feedback_version MODIFY external_feedback_source"
          + " VARCHAR(120) COLLATE utf8mb4_unicode_ci NULL COMMENT '历史来源'");
      executeScript(connection, upgrade);
      executeScript(connection, upgrade);
      assertFeedbackLengths(jdbc, 1000, 500);
      assertThat(jdbc.queryForList("SELECT * FROM interview_feedback_version ORDER BY id")).isEqualTo(history);
      assertThat(feedbackColumnMetadata(jdbc)).isEqualTo(metadata);
      jdbc.update("INSERT INTO interview_feedback_version VALUES (3,?,?)", "评".repeat(1000), "源".repeat(500));
      assertThat(jdbc.queryForMap("SELECT short_comment,external_feedback_source FROM interview_feedback_version WHERE id=3"))
          .containsEntry("short_comment", "评".repeat(1000)).containsEntry("external_feedback_source", "源".repeat(500));

      jdbc.execute("ALTER TABLE interview_feedback_version MODIFY short_comment VARCHAR(1400) NULL,"
          + " MODIFY external_feedback_source VARCHAR(900) NULL");
      jdbc.update("INSERT INTO interview_feedback_version VALUES (4,?,?)", "评".repeat(1400), "源".repeat(900));
      var widerHistory = jdbc.queryForList("SELECT * FROM interview_feedback_version ORDER BY id");
      executeScript(connection, upgrade);
      executeScript(connection, upgrade);
      assertFeedbackLengths(jdbc, 1400, 900);
      assertThat(jdbc.queryForList("SELECT * FROM interview_feedback_version ORDER BY id")).isEqualTo(widerHistory);
    });
  }

  @Test
  void feedbackLengthUpgradePreservesQuotedCommentsWithoutBackslashEscapes() throws Exception {
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.execute("SET SESSION sql_mode=CONCAT(@@SESSION.sql_mode,',NO_BACKSLASH_ESCAPES')");
      jdbc.execute("CREATE TABLE interview_feedback_version (short_comment VARCHAR(500) NULL COMMENT '意见''原文\\保留',"
          + " external_feedback_source VARCHAR(120) NULL COMMENT '来源''原文\\保留') DEFAULT CHARSET=utf8mb4");
      var metadata = feedbackColumnMetadata(jdbc);
      executeScript(connection, legacyUpgrade("hr_ai_upgrade_20260908_01__interview_feedback_lengths.sql"));
      assertFeedbackLengths(jdbc, 1000, 500);
      assertThat(feedbackColumnMetadata(jdbc)).isEqualTo(metadata);
    });
  }

  @Test
  void feedbackLengthUpgradeRejectsMissingPrerequisitesBeforeChangingEitherColumn() throws Exception {
    Path upgrade = legacyUpgrade("hr_ai_upgrade_20260908_01__interview_feedback_lengths.sql");
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      assertThatThrownBy(() -> executeScript(connection, upgrade))
          .hasRootCauseInstanceOf(SQLException.class)
          .rootCause().hasMessageContaining("interview_feedback_version requires");
      jdbc.execute("CREATE TABLE interview_feedback_version (id INT PRIMARY KEY, short_comment VARCHAR(500) NULL)"
          + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
      assertThatThrownBy(() -> executeScript(connection, upgrade))
          .hasRootCauseInstanceOf(SQLException.class)
          .rootCause().hasMessageContaining("interview_feedback_version requires");
      assertThat(jdbc.queryForObject("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS"
          + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='interview_feedback_version'"
          + " AND COLUMN_NAME='short_comment'", Integer.class)).isEqualTo(500);
    });
  }

  @Test
  void searchCheckpointUpgradePreservesHistoryAndAlreadyBoundIndicesOnRerun() throws Exception {
    Path upgrade = legacyUpgrade("hr_ai_upgrade_20260908_02__search_checkpoint_index.sql");
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      jdbc.execute("CREATE TABLE search_projection_checkpoint(projection_name VARCHAR(80) PRIMARY KEY,"
          + "last_created_at TIMESTAMP(6) NOT NULL,last_snapshot_id CHAR(26) NOT NULL,"
          + "projected_count BIGINT NOT NULL,version INT NOT NULL,update_time TIMESTAMP(6) NOT NULL)");
      jdbc.update("INSERT INTO search_projection_checkpoint VALUES('resume-facts','2026-09-01 01:02:03.123456',?,42,7,"
          + "'2026-09-01 02:03:04.654321')", "S".repeat(26));
      String historyQuery = "SELECT projection_name,last_created_at,last_snapshot_id,projected_count,version,update_time "
          + "FROM search_projection_checkpoint";
      var before = jdbc.queryForList(historyQuery);
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForList(historyQuery)).isEqualTo(before);
      assertThat(jdbc.queryForObject("SELECT index_name FROM search_projection_checkpoint", String.class)).isNull();
      jdbc.update("UPDATE search_projection_checkpoint SET index_name='resume-facts-vbound' WHERE projection_name='resume-facts'");
      var bound = jdbc.queryForList("SELECT * FROM search_projection_checkpoint");
      executeScript(connection, upgrade);
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForList("SELECT * FROM search_projection_checkpoint")).isEqualTo(bound);
    });
  }

  @Test
  void searchCheckpointUpgradeRejectsMissingOrIncompatiblePrerequisites() throws Exception {
    Path upgrade = legacyUpgrade("hr_ai_upgrade_20260908_02__search_checkpoint_index.sql");
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      assertThatThrownBy(() -> executeScript(connection, upgrade)).hasRootCauseInstanceOf(SQLException.class);
      jdbc.execute("CREATE TABLE search_projection_checkpoint(projection_name VARCHAR(80),version INT,"
          + "last_created_at TIMESTAMP(6),last_snapshot_id CHAR(26),index_name INT)");
      jdbc.update("INSERT INTO search_projection_checkpoint(projection_name,version,index_name) VALUES('unchanged',3,123)");
      var before = jdbc.queryForList("SELECT * FROM search_projection_checkpoint");
      assertThatThrownBy(() -> executeScript(connection, upgrade)).hasRootCauseInstanceOf(SQLException.class)
          .rootCause().hasMessageContaining("incompatible definition");
      assertThat(jdbc.queryForList("SELECT * FROM search_projection_checkpoint")).isEqualTo(before);
    });
  }

  @Test
  void queuedCancellationRepairPreservesHistoryAndSupportsRerunsAndPartialCompletion() throws Exception {
    Path upgrade = legacyUpgrade("hr_ai_upgrade_20260908_03__queued_cancel_finished_time.sql");
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      createCancellationHistory(jdbc);
      jdbc.update("UPDATE reporting_async_task SET finished_at='2026-09-01 01:02:03.123456' WHERE id='already-fixed'");
      var untouched = jdbc.queryForList("SELECT * FROM reporting_async_task WHERE id<>'missing-time' ORDER BY id");
      Object originalAuditTime = jdbc.queryForMap("SELECT update_time FROM reporting_async_task WHERE id='missing-time'")
          .get("update_time");

      executeScript(connection, upgrade);

      var repaired = jdbc.queryForMap("SELECT * FROM reporting_async_task WHERE id='missing-time'");
      assertThat(repaired.get("finished_at")).isEqualTo(originalAuditTime);
      assertThat(((Number) repaired.get("version")).intValue()).isEqualTo(8);
      assertThat(repaired.get("update_id")).isEqualTo("0".repeat(26));
      assertThat(repaired.get("update_name")).isNull();
      assertThat(repaired.get("safe_error_detail")).isEqualTo("synthetic cancellation reason");
      assertThat(jdbc.queryForObject("SELECT retryable FROM reporting_async_task WHERE id='missing-time'", Boolean.class)).isFalse();
      assertThat(jdbc.queryForList("SELECT * FROM reporting_async_task WHERE id<>'missing-time' ORDER BY id")).isEqualTo(untouched);
      var completed = jdbc.queryForList("SELECT * FROM reporting_async_task ORDER BY id");
      executeScript(connection, upgrade);
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForList("SELECT * FROM reporting_async_task ORDER BY id")).isEqualTo(completed);
    });
  }

  @Test
  void queuedCancellationRepairRejectsMissingPrerequisitesAndUnreliableAuditTime() throws Exception {
    Path upgrade = legacyUpgrade("hr_ai_upgrade_20260908_03__queued_cancel_finished_time.sql");
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      assertThatThrownBy(() -> executeScript(connection, upgrade)).hasRootCauseInstanceOf(SQLException.class);
      createCancellationHistory(jdbc);
      jdbc.update("UPDATE reporting_async_task SET update_time='2020-01-01 00:00:00' WHERE id='missing-time'");
      var before = jdbc.queryForList("SELECT * FROM reporting_async_task ORDER BY id");
      assertThatThrownBy(() -> executeScript(connection, upgrade)).hasRootCauseInstanceOf(SQLException.class)
          .rootCause().hasMessageContaining("manual review");
      assertThat(jdbc.queryForList("SELECT * FROM reporting_async_task ORDER BY id")).isEqualTo(before);
    });
  }

  @Test
  void expiredResumeModelRepairRequiresUnambiguousHistoryAndPreservesReferencedFacts() throws Exception {
    Path upgrade = legacyUpgrade("hr_ai_upgrade_20260908_04__expired_resume_model_history.sql");
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      createExpiredModelTables(jdbc);
      for (String id : List.of("repair", "partial", "referenced", "ambiguous", "wrong-task", "wrong-version",
          "bad-result", "late-model", "current-running", "nonlease", "finished", "failed", "audit",
          "bad-winner", "foreign-winner", "bad-old-time", "nonparse")) insertExpiredModelHistory(jdbc, id);
      jdbc.update("UPDATE talent_resume_ai_model_run SET status='FAILED',error_code='TASK_LEASE_EXPIRED',"
          + "finished_at='2026-09-01 01:10:00',version=8 WHERE id='partial-m'");
      jdbc.update("INSERT INTO talent_resume_parse_snapshot VALUES('old-reference','referenced-v','referenced-e','referenced-m')");
      jdbc.update("UPDATE reporting_async_task SET attempt_count=3 WHERE id='ambiguous'");
      jdbc.update("UPDATE reporting_async_task_attempt SET attempt_no=3 WHERE id='ambiguous-new'");
      jdbc.update("INSERT INTO reporting_async_task_attempt VALUES('overlap','ambiguous',2,'FAILED','TASK_LEASE_EXPIRED',"
          + "'2026-09-01 01:01:00','2026-09-01 01:03:00')");
      jdbc.update("UPDATE reporting_async_task SET resource_id='different-version' WHERE id='wrong-task'");
      jdbc.update("UPDATE talent_resume_ai_model_run SET resume_version_id='different-version' WHERE id='wrong-version-m'");
      jdbc.update("UPDATE reporting_async_task SET result_resource_id=NULL WHERE id='bad-result'");
      jdbc.update("UPDATE talent_resume_ai_model_run SET create_time='2026-09-01 01:15:00' WHERE id='late-model-m'");
      jdbc.update("UPDATE reporting_async_task_attempt SET status='RUNNING',finished_at=NULL WHERE id='current-running-new'");
      jdbc.update("UPDATE reporting_async_task_attempt SET error_code='TASK_EXECUTION_FAILED' WHERE id='nonlease-old'");
      jdbc.update("UPDATE talent_resume_ai_model_run SET finished_at='2026-09-01 01:10:00' WHERE id='finished-m'");
      jdbc.update("UPDATE talent_resume_ai_model_run SET status='FAILED' WHERE id='failed-m'");
      jdbc.update("UPDATE talent_resume_ai_model_run SET version=2147483647 WHERE id='audit-m'");
      jdbc.update("UPDATE talent_resume_ai_model_run SET status='FAILED' WHERE id='bad-winner-w'");
      jdbc.update("UPDATE talent_resume_extraction_run SET task_id='repair' WHERE id='foreign-winner-we'");
      jdbc.update("UPDATE talent_resume_extraction_run SET create_time='2026-09-01 00:59:00' WHERE id='bad-old-time-e'");
      jdbc.update("UPDATE reporting_async_task SET task_type='OTHER' WHERE id='nonparse'");
      var untouchedModels = jdbc.queryForList("SELECT * FROM talent_resume_ai_model_run WHERE id<>'repair-m' ORDER BY id");
      var otherTables = expiredModelRelatedHistory(jdbc);
      var before = jdbc.queryForMap("SELECT * FROM talent_resume_ai_model_run WHERE id='repair-m'");

      executeScript(connection, upgrade);

      var after = jdbc.queryForMap("SELECT * FROM talent_resume_ai_model_run WHERE id='repair-m'");
      assertThat(after.get("status")).isEqualTo("FAILED");
      assertThat(after.get("error_code")).isEqualTo("TASK_LEASE_EXPIRED");
      assertThat(after.get("finished_at")).isEqualTo(jdbc.queryForMap(
          "SELECT finished_at FROM reporting_async_task_attempt WHERE id='repair-old'").get("finished_at"));
      assertThat(((Number) after.get("version")).intValue()).isEqualTo(8);
      assertThat(after.get("update_id")).isEqualTo("0".repeat(26));
      assertThat(after.get("update_name")).isNull();
      assertThat(after.get("update_time")).isNotEqualTo(before.get("update_time"));
      for (String field : List.of("status", "error_code", "finished_at", "version", "update_id", "update_name", "update_time")) {
        before.remove(field);
        after.remove(field);
      }
      assertThat(after).isEqualTo(before);
      assertThat(jdbc.queryForList("SELECT * FROM talent_resume_ai_model_run WHERE id<>'repair-m' ORDER BY id")).isEqualTo(untouchedModels);
      assertThat(expiredModelRelatedHistory(jdbc)).isEqualTo(otherTables);
      var completed = jdbc.queryForList("SELECT * FROM talent_resume_ai_model_run ORDER BY id");
      executeScript(connection, upgrade);
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForList("SELECT * FROM talent_resume_ai_model_run ORDER BY id")).isEqualTo(completed);
      assertThat(expiredModelRelatedHistory(jdbc)).isEqualTo(otherTables);
    });
  }

  @Test
  void expiredResumeModelRepairRollsBackTheWholeStatementOnRealSqlRejection() throws Exception {
    Path upgrade = legacyUpgrade("hr_ai_upgrade_20260908_04__expired_resume_model_history.sql");
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      createExpiredModelTables(jdbc);
      insertExpiredModelHistory(jdbc, "first");
      insertExpiredModelHistory(jdbc, "second");
      var before = jdbc.queryForList("SELECT * FROM talent_resume_ai_model_run ORDER BY id");
      jdbc.execute("CREATE TRIGGER reject_history_repair BEFORE UPDATE ON talent_resume_ai_model_run FOR EACH ROW BEGIN "
          + "IF NEW.id='second-m' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic history repair rejection'; END IF; END");
      assertThatThrownBy(() -> executeScript(connection, upgrade)).hasRootCauseInstanceOf(SQLException.class)
          .rootCause().hasMessageContaining("Synthetic history repair rejection");
      assertThat(jdbc.queryForList("SELECT * FROM talent_resume_ai_model_run ORDER BY id")).isEqualTo(before);
      jdbc.execute("DROP TRIGGER reject_history_repair");
      executeScript(connection, upgrade);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM talent_resume_ai_model_run WHERE error_code='TASK_LEASE_EXPIRED'", Integer.class)).isEqualTo(2);
    });
  }

  @Test
  void expiredResumeModelRepairRejectsMissingTablesAndColumnsWithoutChangingHistory() throws Exception {
    Path upgrade = legacyUpgrade("hr_ai_upgrade_20260908_04__expired_resume_model_history.sql");
    withDatabase(connection -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      assertThatThrownBy(() -> executeScript(connection, upgrade)).hasRootCauseInstanceOf(SQLException.class)
          .rootCause().hasMessageContaining("requires existing");
      createExpiredModelTables(jdbc);
      insertExpiredModelHistory(jdbc, "missing-column");
      jdbc.execute("ALTER TABLE talent_resume_ai_model_run DROP COLUMN update_name");
      var before = jdbc.queryForList("SELECT * FROM talent_resume_ai_model_run ORDER BY id");
      assertThatThrownBy(() -> executeScript(connection, upgrade)).hasRootCauseInstanceOf(SQLException.class)
          .rootCause().hasMessageContaining("requires existing");
      assertThat(jdbc.queryForList("SELECT * FROM talent_resume_ai_model_run ORDER BY id")).isEqualTo(before);
    });
  }

  private static List<List<Map<String, Object>>> expiredModelRelatedHistory(JdbcTemplate jdbc) {
    return List.of("reporting_async_task", "reporting_async_task_attempt", "talent_resume_extraction_run",
        "talent_resume_parse_snapshot", "talent_resume_fact_snapshot").stream()
        .map(table -> jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id")).toList();
  }

  private static void createExpiredModelTables(JdbcTemplate jdbc) {
    jdbc.execute("CREATE TABLE reporting_async_task(id VARCHAR(26) PRIMARY KEY,task_type VARCHAR(80),resource_type VARCHAR(60),"
        + "resource_id VARCHAR(26),status VARCHAR(24),attempt_count INT,finished_at TIMESTAMP(6),result_resource_id VARCHAR(26)) ENGINE=InnoDB");
    jdbc.execute("CREATE TABLE reporting_async_task_attempt(id VARCHAR(26) PRIMARY KEY,task_id VARCHAR(26),attempt_no INT,"
        + "status VARCHAR(20),error_code VARCHAR(100),started_at TIMESTAMP(6),finished_at TIMESTAMP(6) NULL,"
        + "UNIQUE(task_id,attempt_no),FOREIGN KEY(task_id) REFERENCES reporting_async_task(id)) ENGINE=InnoDB");
    jdbc.execute("CREATE TABLE talent_resume_extraction_run(id VARCHAR(26) PRIMARY KEY,resume_version_id VARCHAR(26),"
        + "task_id VARCHAR(26),create_time TIMESTAMP(6),FOREIGN KEY(task_id) REFERENCES reporting_async_task(id)) ENGINE=InnoDB");
    jdbc.execute("CREATE TABLE talent_resume_ai_model_run(id VARCHAR(26) PRIMARY KEY,resume_version_id VARCHAR(26),"
        + "extraction_run_id VARCHAR(26),status VARCHAR(24),create_time TIMESTAMP(6),finished_at TIMESTAMP(6) NULL,"
        + "error_code VARCHAR(100),version INT,update_id CHAR(26),update_name VARCHAR(64),update_time TIMESTAMP(6),"
        + "validation_status VARCHAR(30),raw_output_object_key VARCHAR(500),raw_output_hash CHAR(64),input_tokens INT,"
        + "FOREIGN KEY(extraction_run_id) REFERENCES talent_resume_extraction_run(id)) ENGINE=InnoDB");
    jdbc.execute("CREATE TABLE talent_resume_parse_snapshot(id VARCHAR(26) PRIMARY KEY,resume_version_id VARCHAR(26),"
        + "extraction_run_id VARCHAR(26),model_run_id VARCHAR(26),FOREIGN KEY(model_run_id) REFERENCES talent_resume_ai_model_run(id)) ENGINE=InnoDB");
    jdbc.execute("CREATE TABLE talent_resume_fact_snapshot(id VARCHAR(26) PRIMARY KEY,resume_version_id VARCHAR(26),"
        + "parse_snapshot_id VARCHAR(26),FOREIGN KEY(parse_snapshot_id) REFERENCES talent_resume_parse_snapshot(id)) ENGINE=InnoDB");
  }

  private static void insertExpiredModelHistory(JdbcTemplate jdbc, String id) {
    jdbc.update("INSERT INTO reporting_async_task VALUES(?,'RESUME_PARSE','RESUME_VERSION',?,'SUCCEEDED',2,'2026-09-01 01:20:00',?)",
        id, id + "-v", id + "-f");
    jdbc.update("INSERT INTO reporting_async_task_attempt VALUES(?,?,1,'FAILED','TASK_LEASE_EXPIRED','2026-09-01 01:00:00','2026-09-01 01:10:00')", id + "-old", id);
    jdbc.update("INSERT INTO reporting_async_task_attempt VALUES(?,?,2,'SUCCEEDED',NULL,'2026-09-01 01:10:00','2026-09-01 01:20:00')", id + "-new", id);
    jdbc.update("INSERT INTO talent_resume_extraction_run VALUES(?,?,?,'2026-09-01 01:01:00'),(?,?,?,'2026-09-01 01:11:00')",
        id + "-e", id + "-v", id, id + "-we", id + "-v", id);
    jdbc.update("INSERT INTO talent_resume_ai_model_run VALUES(?,?,?,'RUNNING','2026-09-01 01:02:00',NULL,NULL,7,?,'Synthetic actor',"
        + "'2026-09-01 01:02:00','PENDING','synthetic-output',?,23)", id + "-m", id + "-v", id + "-e", "A".repeat(26), "f".repeat(64));
    jdbc.update("INSERT INTO talent_resume_ai_model_run VALUES(?,?,?,'SUCCEEDED','2026-09-01 01:12:00','2026-09-01 01:19:00',NULL,1,NULL,NULL,"
        + "'2026-09-01 01:19:00','VALID','synthetic-winner',?,42)", id + "-w", id + "-v", id + "-we", "a".repeat(64));
    jdbc.update("INSERT INTO talent_resume_parse_snapshot VALUES(?,?,?,?)", id + "-p", id + "-v", id + "-we", id + "-w");
    jdbc.update("INSERT INTO talent_resume_fact_snapshot VALUES(?,?,?)", id + "-f", id + "-v", id + "-p");
  }

  private static void createCancellationHistory(JdbcTemplate jdbc) {
    jdbc.execute("CREATE TABLE reporting_async_task(id VARCHAR(30) PRIMARY KEY,status VARCHAR(20),attempt_count INT,"
        + "lease_until TIMESTAMP(6) NULL,finished_at TIMESTAMP(6) NULL,create_time TIMESTAMP(6),"
        + "update_time TIMESTAMP(6),version INT,update_id CHAR(26),update_name VARCHAR(64),retryable BOOLEAN,"
        + "safe_error_detail VARCHAR(500)) ENGINE=InnoDB");
    for (String id : List.of("missing-time", "already-fixed", "claimed", "running", "failed")) {
      String status = id.equals("running") ? "RUNNING" : id.equals("failed") ? "FAILED" : "CANCELLED";
      jdbc.update("INSERT INTO reporting_async_task VALUES(?,?,?,NULL,NULL,'2026-09-01 00:00:00',"
          + "'2026-09-01 01:02:03.123456',7,?,'Synthetic actor',TRUE,'synthetic cancellation reason')",
          id, status, id.equals("claimed") || id.equals("running") ? 1 : 0, "A".repeat(26));
    }
  }

  private static List<Map<String, Object>> feedbackColumnMetadata(JdbcTemplate jdbc) {
    return jdbc.queryForList("SELECT COLUMN_NAME,CHARACTER_SET_NAME,COLLATION_NAME,IS_NULLABLE,COLUMN_DEFAULT,COLUMN_COMMENT"
        + " FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='interview_feedback_version'"
        + " AND COLUMN_NAME IN ('short_comment','external_feedback_source') ORDER BY COLUMN_NAME");
  }

  private static void assertFeedbackLengths(JdbcTemplate jdbc, int commentLength, int sourceLength) {
    assertThat(jdbc.queryForList("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS"
        + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='interview_feedback_version'"
        + " AND COLUMN_NAME IN ('short_comment','external_feedback_source') ORDER BY COLUMN_NAME", Integer.class))
        .containsExactly(sourceLength, commentLength);
  }

  /** 比较实际表定义，不依赖 ALTER 后的展示格式或索引行数统计。 */
  private static List<List<Map<String, Object>>> searchResourceDefinition(JdbcTemplate jdbc) {
    return List.of(
        jdbc.queryForList("SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
            + "AND TABLE_NAME='search_index_resource' ORDER BY ORDINAL_POSITION"),
        jdbc.queryForList("SELECT INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,COLLATION,SUB_PART,"
            + "NULLABLE,INDEX_TYPE,INDEX_COMMENT,IS_VISIBLE,EXPRESSION FROM information_schema.STATISTICS "
            + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource' ORDER BY INDEX_NAME,SEQ_IN_INDEX"),
        // MySQL ALTER 会将 ASCII 枚举字面量的 _utf8mb4 标记重写为 _ascii，约束语义不变。
        jdbc.queryForList("SELECT tc.CONSTRAINT_NAME,tc.CONSTRAINT_TYPE,tc.ENFORCED,"
            + "REPLACE(cc.CHECK_CLAUSE,'_utf8mb4','_ascii') AS CHECK_CLAUSE "
            + "FROM information_schema.TABLE_CONSTRAINTS tc LEFT JOIN information_schema.CHECK_CONSTRAINTS cc "
            + "ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME "
            + "WHERE tc.CONSTRAINT_SCHEMA=DATABASE() AND tc.TABLE_NAME='search_index_resource' ORDER BY tc.CONSTRAINT_NAME"),
        jdbc.queryForList("SELECT ENGINE,TABLE_COLLATION,CREATE_OPTIONS,TABLE_COMMENT FROM information_schema.TABLES "
            + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_index_resource'"));
  }

  /** 每个用例直接连接临时库，禁止复用切库前创建的 Statement。 */
  private static void withDatabase(DatabaseWork work) throws Exception {
    withDatabase(requiredEnvironment("HR_DB_URL"), work);
  }

  private static void withDatabase(String sourceUrl, DatabaseWork work) throws Exception {
    String schema = "hr_sql_it_" + UUID.randomUUID().toString().replace("-", "");
    URI endpoint = URI.create(sourceUrl.substring("jdbc:".length()));
    if (!"mysql".equals(endpoint.getScheme()) || endpoint.getRawUserInfo() != null) {
      throw new IllegalArgumentException("External tests require a plain MySQL JDBC endpoint");
    }
    String isolatedUrl = "jdbc:mysql://" + endpoint.getRawAuthority() + "/" + schema
        + (endpoint.getRawQuery() == null ? "" : "?" + endpoint.getRawQuery());
    String user = requiredEnvironment("HR_DB_USER");
    String password = requiredEnvironment("HR_DB_PASSWORD");
    try (Connection administration = DriverManager.getConnection(sourceUrl, user, password);
        Statement databaseStatement = administration.createStatement()) {
      databaseStatement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
      try (Connection isolated = DriverManager.getConnection(isolatedUrl, user, password)) {
        assertThat(isolated.getCatalog()).isEqualTo(schema);
        assertThat(isolated.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
        assertThat(isolated.getMetaData().getDatabaseMajorVersion()).isGreaterThanOrEqualTo(8);
        work.run(isolated);
      } finally {
        databaseStatement.execute("DROP DATABASE `" + schema + "`");
      }
    }
  }

  @FunctionalInterface
  private interface DatabaseWork {
    void run(Connection connection) throws Exception;
  }

  private static void assertInterviewHistoryIndexes(JdbcTemplate jdbc) {
    for (String index : new String[] {"ix_interview_feedback_page", "ix_interview_notification_page"}) {
      assertThat(jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.STATISTICS"
              + " WHERE TABLE_SCHEMA=DATABASE() AND INDEX_NAME=? ORDER BY SEQ_IN_INDEX", String.class, index))
          .containsExactly("interview_id", "create_time", "id");
    }
  }

  private static void assertLeaseIndex(JdbcTemplate jdbc) {
    assertThat(
            jdbc.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.STATISTICS"
                    + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_async_task'"
                    + " AND INDEX_NAME='ix_reporting_task_lease' ORDER BY SEQ_IN_INDEX",
                String.class))
        .containsExactly("task_type", "status", "lease_until", "id");
  }

  private static void executeScript(Connection connection, Path script) {
    ScriptUtils.executeSqlScript(
        connection, new EncodedResource(new FileSystemResource(script), StandardCharsets.UTF_8));
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    assertThat(value).as("必须显式配置测试环境变量 %s", name).isNotBlank();
    return value;
  }

  private static Path repositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("docs/sql/hr_ai_complete_schema.sql"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("无法定位仓库根目录");
  }

  private static Path legacyUpgrade(String name) {
    return repositoryRoot()
        .resolve("bootstrap/src/test/resources/db/legacy-upgrades")
        .resolve(name);
  }
}
