package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.ezaigc.hr.identity.domain.entity.AdminUserRecord;
import com.ezaigc.hr.identity.mapper.IdentityAdministrationMapper;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.mapper.DemandCatalogMapper;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** 真实预编译参数必须能跨 Unicode 展示字段和 ASCII 业务标识执行搜索。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
class CatalogUnicodeSearchExternalMySqlIT {
  @Test
  void unicodeTitlesAndNamesCanBeSearchedAlongsideAsciiCodesAndEmails() throws Exception {
    var configured = URI.create(System.getenv("HR_DB_URL").substring("jdbc:".length()));
    String server = "jdbc:mysql://" + configured.getRawAuthority() + "/";
    String query = "?useServerPrepStmts=true&characterEncoding=UTF-8";
    String schema = "qa_unicode_search_" + UUID.randomUUID().toString().replace("-", "");
    try (var admin = DriverManager.getConnection(server + query,
        System.getenv("HR_DB_USER"), System.getenv("HR_DB_PASSWORD"))) {
      try (var ddl = admin.createStatement()) {
        ddl.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
      }
      try (var connection = DriverManager.getConnection(server + schema + query,
          System.getenv("HR_DB_USER"), System.getenv("HR_DB_PASSWORD"))) {
        verifyQueries(connection);
      } finally {
        if (!schema.matches("qa_unicode_search_[0-9a-f]{32}")) throw new IllegalStateException("unexpected fixture schema");
        try (var ddl = admin.createStatement()) { ddl.execute("DROP DATABASE `" + schema + "`"); }
      }
    }
  }

  private void verifyQueries(Connection connection) {
    var source = new SingleConnectionDataSource(connection, true);
    var jdbc = new JdbcTemplate(source);
    // 仅保留实际 mapper 使用的列；编码与邮箱的 ASCII/BIN 定义和部署基线一致。
    jdbc.execute("CREATE TABLE demand_jd (id VARCHAR(26), demand_id VARCHAR(26),source_owner_department_id VARCHAR(26),source_fingerprint VARCHAR(64),applied_from_jd_version_id VARCHAR(26),code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin,job_type_code CHAR(3),work_region_code CHAR(3),headcount INT,recruitment_cycle_days INT,distinction VARCHAR(180),public_visible BOOLEAN DEFAULT FALSE,content_shared BOOLEAN DEFAULT FALSE,deleted BOOLEAN DEFAULT FALSE,recruitment_type VARCHAR(20),client_id VARCHAR(26),owner_user_id VARCHAR(26),owner_department_id VARCHAR(26),title VARCHAR(200),status VARCHAR(20),current_version_no INT,closed_by_user_id VARCHAR(26),closed_at DATETIME,version BIGINT,create_id VARCHAR(26),create_name VARCHAR(60),create_time DATETIME,update_id VARCHAR(26),update_name VARCHAR(60),update_time DATETIME,ext JSON,remark VARCHAR(100))");
    jdbc.execute("CREATE TABLE demand_jd_code_option(kind VARCHAR(16),code CHAR(3),name VARCHAR(80))");
    jdbc.execute("CREATE TABLE demand_jd_version(id VARCHAR(26),jd_id VARCHAR(26),version_no INT)");
    jdbc.execute("CREATE TABLE reporting_async_task(id VARCHAR(26),resource_id VARCHAR(26),task_type VARCHAR(30),status VARCHAR(30),create_time DATETIME(6))");
    jdbc.execute("CREATE TABLE recruitment_application(id VARCHAR(26),jd_id VARCHAR(26))");
    jdbc.execute("CREATE TABLE recruitment_offer_status_record(id VARCHAR(26),application_id VARCHAR(26),status VARCHAR(20),create_time DATETIME(6))");
    jdbc.execute("CREATE TABLE demand_recruitment_demand (id VARCHAR(26),code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin,client_id VARCHAR(26),owner_user_id VARCHAR(26),owner_department_id VARCHAR(26))");
    jdbc.execute("CREATE TABLE demand_client (id VARCHAR(26),name VARCHAR(100))");
    jdbc.execute("CREATE TABLE demand_recruitment_collaborator (demand_id VARCHAR(26),user_id VARCHAR(26))");
    jdbc.execute("CREATE TABLE identity_object_grant (user_id VARCHAR(26),object_type VARCHAR(30),object_id VARCHAR(26),permission_code VARCHAR(50))");
    jdbc.execute("CREATE TABLE identity_department (id VARCHAR(26),name VARCHAR(60))");
    jdbc.execute("CREATE TABLE identity_user_account (id VARCHAR(26),display_name VARCHAR(60),work_email VARCHAR(100),normalized_email VARCHAR(320) CHARACTER SET ascii COLLATE ascii_bin,source_title VARCHAR(60),home_department_id VARCHAR(26),direct_leader_user_id VARCHAR(26),status VARCHAR(20),must_change_password BOOLEAN,version BIGINT,registered_at DATETIME)");
    jdbc.update("INSERT INTO demand_recruitment_demand VALUES ('demand','QA_OPS',NULL,'hr','dept'),('other-demand','QA_MISC',NULL,'hr','dept')");
    jdbc.update("INSERT INTO demand_jd(id,demand_id,title,status,current_version_no,version,create_id,source_owner_department_id,create_time) VALUES ('bound','demand','合成Linux运维','ACTIVE',1,0,'hr','dept','2026-09-08 02:00:00'),('source',NULL,'合成🚀研发','ACTIVE',1,0,'hr','dept','2026-09-08 01:00:00'),('private',NULL,'合成私有岗位','ACTIVE',1,0,'other','other','2026-09-08 03:00:00')");
    jdbc.update("INSERT INTO demand_jd(id,demand_id,title,status,current_version_no,version,create_id,source_owner_department_id,create_time) VALUES ('unrelated','other-demand','Infrastructure','ACTIVE',1,0,'hr','dept','2026-09-08 04:00:00')");
    jdbc.update("UPDATE demand_jd SET code='QA_OPS' WHERE id='bound'");
    jdbc.update("INSERT INTO identity_department VALUES ('dept','合成部门')");
    jdbc.update("INSERT INTO identity_user_account(id,display_name,work_email,normalized_email,home_department_id,status,version,registered_at) VALUES ('hr','合成管理员🚀','qa-admin@example.invalid','qa-admin@example.invalid','dept','ACTIVE',0,'2026-09-08 02:00:00'),('other','Other','other@example.invalid','other@example.invalid','dept','ACTIVE',0,'2026-09-08 01:00:00')");

    var configuration = new MybatisConfiguration();
    configuration.setEnvironment(new Environment("unicode-search", new JdbcTransactionFactory(), source));
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.addMapper(DemandCatalogMapper.class);
    configuration.addMapper(IdentityAdministrationMapper.class);
    try (var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(connection)) {
      var jds = session.getMapper(DemandCatalogMapper.class);
      assertThat(jds.findJds("合成", null, null, "hr", false, Set.of(), Set.of(), null, null, 50))
          .extracting(JdEntity::getId).containsExactly("bound", "source");
      var first = jds.findJds("合成", null, null, "hr", false, Set.of(), Set.of(), null, null, 1);
      assertThat(first).extracting(JdEntity::getId).containsExactly("bound");
      assertThat(jds.findJds("合成", null, null, "hr", false, Set.of(), Set.of(), first.getFirst().getCreateTime(), first.getFirst().getId(), 1))
          .extracting(JdEntity::getId).containsExactly("source");
      assertThat(jds.findJds("🚀", null, null, "hr", false, Set.of(), Set.of(), null, null, 50))
          .extracting(JdEntity::getId).containsExactly("source");
      assertThat(jds.findJds("QA_OPS", null, null, "hr", false, Set.of(), Set.of(), null, null, 50))
          .extracting(JdEntity::getId).containsExactly("bound");
      assertThat(jds.findJds("qa_ops", null, null, "hr", false, Set.of(), Set.of(), null, null, 50)).isEmpty();
      assertThat(jds.findJds("没有命中", null, null, "hr", false, Set.of(), Set.of(), null, null, 50)).isEmpty();

      var users = session.getMapper(IdentityAdministrationMapper.class);
      assertThat(users.findUsers("合成", null, 50)).extracting(AdminUserRecord::getId).containsExactly("hr");
      assertThat(users.findUsers("🚀", null, 50)).extracting(AdminUserRecord::getId).containsExactly("hr");
      assertThat(users.findUsers("qa-admin", null, 50)).extracting(AdminUserRecord::getId).containsExactly("hr");
      assertThat(users.findUsers("QA-ADMIN", null, 50)).isEmpty();
      assertThat(users.findUsers("没有命中", null, 50)).isEmpty();
    }
  }
}
