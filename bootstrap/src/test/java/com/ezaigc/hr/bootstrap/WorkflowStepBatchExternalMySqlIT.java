package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.ezaigc.hr.recruiting.demand.mapper.WorkflowStepMapper;
import com.ezaigc.hr.recruiting.demand.service.impl.DemandWorkflowStepPersistenceServiceImpl;
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
class WorkflowStepBatchExternalMySqlIT {

  @Test
  void oneRealQueryFiltersTemplatesAndPreservesStableStepOrder() throws Exception {
    try (Connection connection =
        DriverManager.getConnection(
            requiredEnvironment("HR_DB_URL"),
            requiredEnvironment("HR_DB_USER"),
            requiredEnvironment("HR_DB_PASSWORD"))) {
      var dataSource = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(dataSource);
      // 连接私有临时表遮蔽业务同名表；只准备批量查询所需的实体映射列。
      jdbc.execute(
          "CREATE TEMPORARY TABLE demand_workflow_step_definition ("
              + "id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,"
              + "template_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,"
              + "step_code VARCHAR(48),sequence_no INT,candidate_optional BOOLEAN,"
              + "default_follow_up_channel VARCHAR(24),ext TEXT,remark VARCHAR(255),"
              + "create_id CHAR(26),create_name VARCHAR(64),create_time TIMESTAMP(6),"
              + "update_id CHAR(26),update_name VARCHAR(64),update_time TIMESTAMP(6),"
              + "version INT NOT NULL DEFAULT 0) ENGINE=InnoDB");
      String firstTemplate = "A".repeat(26);
      String secondTemplate = "B".repeat(26);
      String excludedTemplate = "C".repeat(26);
      insert(jdbc, "Z".repeat(26), secondTemplate, "OFFER", 2);
      insert(jdbc, "D".repeat(26), firstTemplate, "DECISION", 2);
      insert(jdbc, "C".repeat(26), firstTemplate, "INTERVIEW_ROUND_1", 2);
      insert(jdbc, "E".repeat(26), excludedTemplate, "CLIENT_SUBMISSION", 1);
      insert(jdbc, "Y".repeat(26), secondTemplate, "DECISION", 1);

      var statements = new PreparedStatements();
      var configuration = new MybatisConfiguration();
      configuration.setMapUnderscoreToCamelCase(true);
      configuration.setEnvironment(
          new Environment("workflow-step-batch", new JdbcTransactionFactory(), dataSource));
      configuration.addInterceptor(statements);
      configuration.addMapper(WorkflowStepMapper.class);
      try (var session =
          new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(connection)) {
        var service = new DemandWorkflowStepPersistenceServiceImpl();
        ReflectionTestUtils.setField(
            service, "baseMapper", session.getMapper(WorkflowStepMapper.class));

        assertThat(service.findWorkflowStepsByTemplateIds(List.of(secondTemplate, firstTemplate)))
            .extracting(
                step ->
                    step.getTemplateVersionId() + ":" + step.getSequenceNo() + ":" + step.getId())
            .containsExactly(
                firstTemplate + ":2:" + "C".repeat(26),
                firstTemplate + ":2:" + "D".repeat(26),
                secondTemplate + ":1:" + "Y".repeat(26),
                secondTemplate + ":2:" + "Z".repeat(26));
        assertThat(statements.sql).hasSize(1);
        assertThat(statements.sql.getFirst())
            .contains(
                "FROM demand_workflow_step_definition",
                "template_version_id IN",
                "ORDER BY template_version_id ASC,sequence_no ASC,id ASC");

        session.clearCache();
        statements.sql.clear();
        assertThat(service.findWorkflowStepsByTemplateIds(List.of())).isEmpty();
        assertThat(statements.sql).isEmpty();
      }
    }
  }

  private static void insert(
      JdbcTemplate jdbc, String id, String templateId, String stepCode, int sequenceNo) {
    jdbc.update(
        "INSERT INTO demand_workflow_step_definition"
            + "(id,template_version_id,step_code,sequence_no,candidate_optional,create_time,update_time)"
            + " VALUES(?,?,?,?,FALSE,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
        id,
        templateId,
        stepCode,
        sequenceNo);
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    assertThat(value).as("必须显式配置测试环境变量 %s", name).isNotBlank();
    return value;
  }

  /** 统计真正准备的 JDBC 语句，排除 MyBatis 本地缓存命中伪装成批量读取。 */
  @Intercepts(
      @Signature(
          type = StatementHandler.class,
          method = "prepare",
          args = {Connection.class, Integer.class}))
  private static final class PreparedStatements implements Interceptor {
    private final List<String> sql = new ArrayList<>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
      sql.add(((StatementHandler) invocation.getTarget()).getBoundSql().getSql());
      return invocation.proceed();
    }
  }
}
