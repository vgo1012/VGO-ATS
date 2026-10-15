package com.ezaigc.hr.bootstrap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
@EnabledIfEnvironmentVariable(named="HR_EXTERNAL_MYSQL_IT",matches="true")
class MailSourceSchemaExternalMySqlIT {
  @Test
  void emptyBaselineAndHistoricalPartialUpgradeAreRerunnable() throws Exception {
    withDatabase(connection -> {
      execute(connection,sqlRoot().resolve("hr_ai_complete_schema.sql"));
      var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
      jdbc.update("INSERT INTO demand_jd(id,title,status,create_id) VALUES('jd','Example','DRAFT','owner')");
      jdbc.update("INSERT INTO demand_jd_version(id,jd_id,version_no,title,raw_text,status,source_type) VALUES('jdv','jd',1,'Example','source','DRAFT','MANUAL')");
      jdbc.update("INSERT INTO demand_mail_source_message(id,jd_version_id,source_message_id,message_hash,payload_hash,sender_email,recipient_email,subject,plain_body,delivery_type,create_id) VALUES('mail','jdv','<one@test>','hash','payload','sender@test','hr@test','subject','body','MAIL_SIMULATION','owner')");
      execute(connection,upgrade()); execute(connection,upgrade());
      assertThat(jdbc.queryForObject("SELECT plain_body FROM demand_mail_source_message WHERE id='mail'",String.class)).isEqualTo("body");
      assertThatThrownBy(() -> jdbc.update("INSERT INTO demand_mail_source_message(id,jd_version_id,source_message_id,message_hash,payload_hash,sender_email,recipient_email,subject,plain_body,delivery_type,create_id) VALUES('duplicate','jdv','<one@test>','hash','changed','sender@test','hr@test','subject','body','MAIL_SIMULATION','owner')")).isInstanceOf(DataIntegrityViolationException.class);
      jdbc.execute("DROP TABLE demand_jd_resume_source");
      execute(connection,upgrade()); execute(connection,upgrade());
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demand_mail_source_message",Integer.class)).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demand_jd_resume_source",Integer.class)).isZero();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema=DATABASE() AND table_name='demand_jd_resume_source' AND constraint_name='uk_jd_resume_source_attachment'",Integer.class)).isEqualTo(1);
    });
  }
  private static void execute(Connection connection, Path script) {
    ScriptUtils.executeSqlScript(connection,
        new EncodedResource(new FileSystemResource(script), StandardCharsets.UTF_8));
  }

  private static Path upgrade() {
    return sqlRoot()
        .resolve("../../bootstrap/src/test/resources/db/legacy-upgrades")
        .normalize()
        .resolve("hr_ai_upgrade_20260907_06__mail_resume_sources.sql");
  }

  private static Path sqlRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (current != null && !Files.isDirectory(current.resolve("docs/sql"))) current = current.getParent();
    if (current == null) throw new IllegalStateException("SQL source root not found");
    return current.resolve("docs/sql");
  }

  private static void withDatabase(DatabaseCheck check) throws Exception {
    String schema = "hr_mail_source_it_" + UUID.randomUUID().toString().replace("-", "");
    try (Connection connection = DriverManager.getConnection(System.getenv("HR_DB_URL"),
        System.getenv("HR_DB_USER"), System.getenv("HR_DB_PASSWORD"));
        var statement = connection.createStatement()) {
      String previous = connection.getCatalog();
      statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
      try {
        connection.setCatalog(schema);
        check.run(connection);
      } finally {
        connection.setCatalog(previous);
        statement.execute("DROP DATABASE `" + schema + "`");
      }
    }
  }

  @FunctionalInterface
  private interface DatabaseCheck { void run(Connection connection) throws Exception; }
}
