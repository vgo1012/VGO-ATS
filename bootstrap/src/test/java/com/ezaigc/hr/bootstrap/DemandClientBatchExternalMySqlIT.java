package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.recruiting.demand.application.adapter.DemandQueryAdapter;
import com.ezaigc.hr.recruiting.demand.mapper.JdEntityMapper;
import com.ezaigc.hr.recruiting.demand.mapper.JdVersionMapper;
import com.ezaigc.hr.recruiting.demand.service.impl.JdEntityPersistenceServiceImpl;
import com.ezaigc.hr.recruiting.demand.service.impl.JdVersionPersistenceServiceImpl;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;

@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
class DemandClientBatchExternalMySqlIT {

  @Test
  void twoRealQueriesPreserveVersionClientsAndRejectMissingReferences() throws Exception {
    try (Connection connection = DriverManager.getConnection(requiredEnvironment("HR_DB_URL"),
        requiredEnvironment("HR_DB_USER"), requiredEnvironment("HR_DB_PASSWORD"))) {
      var dataSource = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(dataSource);
      // 连接私有临时表遮蔽业务同名表；只准备批量窄查询所需列，连接关闭后自动删除。
      jdbc.execute("CREATE TEMPORARY TABLE demand_jd (id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,"
          + "client_id CHAR(26)) ENGINE=InnoDB");
      jdbc.execute("CREATE TEMPORARY TABLE demand_jd_version (id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,"
          + "jd_id CHAR(26),recruitment_type VARCHAR(32),client_id CHAR(26)) ENGINE=InnoDB");
      jdbc.update("INSERT INTO demand_jd VALUES('jd','current-client')");
      jdbc.update("INSERT INTO demand_jd_version VALUES('legacy','jd',NULL,'legacy-ignored')");
      jdbc.update("INSERT INTO demand_jd_version VALUES('external','jd','EXTERNAL','historical-client')");
      jdbc.update("INSERT INTO demand_jd_version VALUES('internal','jd','INTERNAL',NULL)");
      var ids = new ArrayList<>(List.of("legacy", "external", "internal"));
      for (int index = 0; index < 197; index++) {
        String id = "version-" + index;
        ids.add(id);
        jdbc.update("INSERT INTO demand_jd_version VALUES(?,'jd','EXTERNAL','batch-client')", id);
      }
      jdbc.update("INSERT INTO demand_jd_version VALUES('orphan','missing-jd','EXTERNAL','old-client')");

      var statements = new PreparedStatements();
      var configuration = new MybatisConfiguration();
      configuration.setMapUnderscoreToCamelCase(true);
      configuration.setEnvironment(new Environment("demand-client-batch", new JdbcTransactionFactory(), dataSource));
      configuration.addInterceptor(statements);
      configuration.addMapper(JdVersionMapper.class);
      configuration.addMapper(JdEntityMapper.class);
      try (var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(connection)) {
        var versions = new JdVersionPersistenceServiceImpl();
        ReflectionTestUtils.setField(versions, "baseMapper", session.getMapper(JdVersionMapper.class));
        var jds = new JdEntityPersistenceServiceImpl();
        ReflectionTestUtils.setField(jds, "baseMapper", session.getMapper(JdEntityMapper.class));
        var adapter = new DemandQueryAdapter();
        ReflectionTestUtils.setField(adapter, "jdVersionPersistenceService", versions);
        ReflectionTestUtils.setField(adapter, "jdEntityPersistenceService", jds);

        var clients = adapter.clientIdsForVersions(ids);
        assertThat(clients).hasSize(200)
            .containsEntry("legacy", "current-client")
            .containsEntry("external", "historical-client")
            .containsEntry("internal", null)
            .containsEntry("version-196", "batch-client");
        assertThat(statements.sql).hasSize(2);
        assertThat(statements.sql.getFirst()).contains("FROM demand_jd_version", " IN ");
        assertThat(statements.sql.getLast()).contains("FROM demand_jd", " IN ");
        assertThatThrownBy(() -> clients.put("external", "overwritten"))
            .isInstanceOf(UnsupportedOperationException.class);

        session.clearCache();
        statements.sql.clear();
        assertThat(adapter.clientIdsForVersions(List.of("external", "external")))
            .containsOnlyKeys("external").containsEntry("external", "historical-client");
        assertThat(statements.sql).hasSize(2);

        session.clearCache();
        statements.sql.clear();
        assertThatThrownBy(() -> adapter.clientIdsForVersions(List.of("external", "missing-version")))
            .isInstanceOfSatisfying(PlatformException.class,
                failure -> assertThat(failure.code()).isEqualTo("JD_VERSION_NOT_FOUND"));
        assertThat(statements.sql).hasSize(1);

        session.clearCache();
        statements.sql.clear();
        assertThatThrownBy(() -> adapter.clientIdsForVersions(List.of("orphan")))
            .isInstanceOfSatisfying(PlatformException.class,
                failure -> assertThat(failure.code()).isEqualTo("JD_NOT_FOUND"));
        assertThat(statements.sql).hasSize(2);

        statements.sql.clear();
        assertThat(adapter.clientIdsForVersions(List.of())).isEmpty();
        var tooMany = new ArrayList<>(ids);
        tooMany.add("orphan");
        assertThatThrownBy(() -> adapter.clientIdsForVersions(tooMany))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(statements.sql).isEmpty();
      }
    }
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) throw new IllegalStateException("Missing test environment: " + name);
    return value;
  }

  /** 统计真正准备的 JDBC 语句，排除 MyBatis 本地缓存命中伪装成批量读取。 */
  @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
  private static final class PreparedStatements implements Interceptor {
    private final List<String> sql = new ArrayList<>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
      sql.add(((StatementHandler) invocation.getTarget()).getBoundSql().getSql());
      return invocation.proceed();
    }
  }
}
