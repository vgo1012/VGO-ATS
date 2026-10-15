package com.ezaigc.hr.bootstrap;
import static org.assertj.core.api.Assertions.assertThat;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** 只在显式测试端点创建随机临时库；业务库不切库、不执行任何 DDL/DML。 */
@EnabledIfEnvironmentVariable(named="HR_EXTERNAL_MYSQL_IT", matches="true")
class ProgressiveFollowUpExternalMySqlIT {
  @Test void upgradesHistoricalStepsAndPersistsFourthRoundAfterRerun() throws Exception {
    withDatabase(connection -> {
      var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
      jdbc.execute("CREATE TABLE recruitment_application (id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY)");
      jdbc.execute("CREATE TABLE recruitment_application_workflow_step (id CHAR(26) PRIMARY KEY, application_id CHAR(26), step_definition_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, sequence_no INT NOT NULL, UNIQUE KEY uk_recruitment_application_step(application_id,step_definition_id))");
      jdbc.execute("CREATE TABLE recruitment_workflow_path_change_record (id INT PRIMARY KEY, change_type VARCHAR(16), CONSTRAINT ck_recruitment_path_change_type CHECK(change_type IN ('SELECTION','AMENDMENT')))");
      jdbc.execute("CREATE TABLE demand_jd_version(id INT PRIMARY KEY)");
      jdbc.execute("CREATE TABLE interview_interview(id INT PRIMARY KEY, round_no INT NOT NULL, CONSTRAINT ck_interview_round CHECK(round_no BETWEEN 0 AND 3))");
      jdbc.update("INSERT INTO recruitment_application(id) VALUES (?)","A".repeat(26));
      jdbc.update("INSERT INTO recruitment_application_workflow_step VALUES ('s1',?,'definition',1)","A".repeat(26));
      jdbc.update("INSERT INTO interview_interview VALUES (1,3)");
      // 模拟部分升级，仅新建了应用模式列；后续升级必须接续且保留数据。
      jdbc.execute("ALTER TABLE recruitment_application ADD progressive_follow_up BOOLEAN NOT NULL DEFAULT FALSE");
      execute(connection,upgrade());execute(connection,upgrade());
      assertThat(jdbc.queryForObject("SELECT progressive_follow_up FROM recruitment_application",Boolean.class)).isFalse();
      assertThat(jdbc.queryForObject("SELECT round_no FROM interview_interview WHERE id=1",Integer.class)).isEqualTo(3);
      assertThat(jdbc.update("INSERT INTO interview_interview VALUES (2,4)")).isEqualTo(1);
      assertThat(jdbc.update("INSERT INTO recruitment_application_workflow_step(id,application_id,step_definition_id,sequence_no,round_no) VALUES ('s2',?,'definition',2,4)","A".repeat(26))).isEqualTo(1);
      assertThat(jdbc.update("INSERT INTO recruitment_application_workflow_step(id,application_id,step_definition_id,sequence_no,round_no) VALUES ('s3',?,NULL,3,5)","A".repeat(26))).isEqualTo(1);
      assertThat(jdbc.update("INSERT INTO recruitment_follow_up_plan(id,application_id,starts_at,ends_at,channel,note,status) VALUES (?,?,'2026-09-09 10:00:00','2026-09-09 10:30:00','PHONE','双方确认','SCHEDULED')","P".repeat(26),"A".repeat(26))).isEqualTo(1);
      assertThat(jdbc.update("UPDATE recruitment_follow_up_plan SET starts_at='2026-09-09 11:00:00',ends_at='2026-09-09 11:30:00',version=version+1 WHERE version=0")).isEqualTo(1);
      assertThat(jdbc.update("UPDATE recruitment_follow_up_plan SET status='CANCELLED' WHERE version=0")).isZero();
      execute(connection,upgrade());
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recruitment_follow_up_plan",Integer.class)).isEqualTo(1);
    });
  }
  @Test void baselineAndUpgradeRerunHaveSameFinalContracts() throws Exception {
    withDatabase(connection -> { execute(connection,sqlRoot().resolve("hr_ai_complete_schema.sql"));execute(connection,upgrade());execute(connection,upgrade()); });
  }
  private static Path upgrade(){return sqlRoot().resolve("../../bootstrap/src/test/resources/db/legacy-upgrades").normalize().resolve("hr_ai_upgrade_20260909_02__progressive_follow_up.sql");}
  private static Path sqlRoot(){
    Path current=Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while(current!=null&&!Files.isDirectory(current.resolve("docs/sql")))current=current.getParent();
    if(current==null)throw new IllegalStateException("SQL source root not found");return current.resolve("docs/sql");
  }
  private static void execute(Connection connection,Path script){ScriptUtils.executeSqlScript(connection,new EncodedResource(new FileSystemResource(script),StandardCharsets.UTF_8));}
  private static void withDatabase(DatabaseWork work)throws Exception{
    String source=required("HR_DB_URL"),user=required("HR_DB_USER"),password=required("HR_DB_PASSWORD");
    URI endpoint=URI.create(source.substring("jdbc:".length()));
    if(!"mysql".equals(endpoint.getScheme())||endpoint.getRawUserInfo()!=null)throw new IllegalArgumentException("Expected explicit MySQL test endpoint");
    String schema="hr_follow_up_it_"+UUID.randomUUID().toString().replace("-","");
    String isolated="jdbc:mysql://"+endpoint.getRawAuthority()+"/"+schema+(endpoint.getRawQuery()==null?"":"?"+endpoint.getRawQuery());
    try(Connection admin=DriverManager.getConnection(source,user,password);Statement databases=admin.createStatement()){
      String original=admin.getCatalog();databases.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");
      try(Connection connection=DriverManager.getConnection(isolated,user,password)){
        assertThat(connection.getCatalog()).isEqualTo(schema);work.run(connection);
      }finally{
        if(!schema.matches("hr_follow_up_it_[0-9a-f]{32}"))throw new IllegalStateException("Invalid temporary schema");
        databases.execute("DROP DATABASE `"+schema+"`");assertThat(admin.getCatalog()).isEqualTo(original);
      }
    }
  }
  private static String required(String name){String value=System.getenv(name);assertThat(value).as("显式测试环境变量 %s",name).isNotBlank();return value;}
  @FunctionalInterface private interface DatabaseWork{void run(Connection connection)throws Exception;}
}
