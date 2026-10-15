package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.ezaigc.hr.talent.mapper.ResumeLifecycleMapper;
import com.ezaigc.hr.talent.mapper.WorkCalendarMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** 显式外部 MySQL 开关下只操作随机隔离数据库；不启动容器，也不修改现有业务库。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
class ResumeLifecycleExternalMySqlIT {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void holidayUpgradePreservesHistoryAndSupportsRerunAndMissingConstraint(boolean baseline) throws Exception {
    withDatabase((connection, ignored) -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      if (baseline) script(connection, "hr_ai_complete_schema.sql");
      else {
        jdbc.execute("CREATE TABLE talent_candidate(id CHAR(26) PRIMARY KEY) ENGINE=InnoDB");
        script(connection, "hr_ai_upgrade_20260914_07__work_calendar.sql");
        script(connection, "hr_ai_upgrade_20260914_08__resume_lifecycle.sql");
      }
      seed(jdbc);
      var history = jdbc.queryForMap("SELECT * FROM talent_work_calendar_day WHERE id='day-history'");
      for (int run = 0; run < 2; run++) {
        script(connection, "hr_ai_upgrade_20260915_01__work_calendar_holiday.sql");
      }
      assertThat(jdbc.queryForMap("SELECT * FROM talent_work_calendar_day WHERE id='day-history'"))
          .isEqualTo(history);
      jdbc.update("UPDATE talent_work_calendar_day SET day_type='HOLIDAY' WHERE id='day-history'");
      script(connection, "hr_ai_upgrade_20260915_01__work_calendar_holiday.sql");
      assertThat(jdbc.queryForObject("SELECT day_type FROM talent_work_calendar_day WHERE id='day-history'",
          String.class)).isEqualTo("HOLIDAY");
      jdbc.execute("ALTER TABLE talent_work_calendar_day DROP CHECK ck_work_calendar_day_type");
      script(connection, "hr_ai_upgrade_20260915_01__work_calendar_holiday.sql");
      assertThatThrownBy(() -> jdbc.update("UPDATE talent_work_calendar_day SET day_type='UNKNOWN' WHERE id='day-history'"))
          .rootCause().isInstanceOfSatisfying(SQLException.class,
              error -> assertThat(error.getErrorCode()).isEqualTo(3819));
    });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void baselineAndStandaloneUpgradesPreservePublishedCalendarAndCycleHistory(boolean baseline) throws Exception {
    withDatabase((connection, ignored) -> {
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      if (baseline) script(connection, "hr_ai_complete_schema.sql");
      else {
        // 新表升级依赖既有候选人基线，此处仅保留升级前置表。
        jdbc.execute("CREATE TABLE talent_candidate(id CHAR(26) PRIMARY KEY) ENGINE=InnoDB");
        script(connection, "hr_ai_upgrade_20260914_07__work_calendar.sql");
      }
      // 模拟07完成、08尚未开始，并验证两次全量重跑。
      script(connection, "hr_ai_upgrade_20260914_07__work_calendar.sql");
      script(connection, "hr_ai_upgrade_20260914_08__resume_lifecycle.sql");
      seed(jdbc);
      var calendar = jdbc.queryForMap("SELECT * FROM talent_work_calendar_revision WHERE id='calendar-history'");
      var day = jdbc.queryForMap("SELECT * FROM talent_work_calendar_day WHERE id='day-history'");
      var cycle = jdbc.queryForMap("SELECT * FROM talent_resume_lifecycle WHERE id='cycle-history'");
      for (int run = 0; run < 2; run++) {
        script(connection, "hr_ai_upgrade_20260914_07__work_calendar.sql");
        script(connection, "hr_ai_upgrade_20260914_08__resume_lifecycle.sql");
      }
      assertThat(jdbc.queryForMap("SELECT * FROM talent_work_calendar_revision WHERE id='calendar-history'"))
          .isEqualTo(calendar);
      assertThat(jdbc.queryForMap("SELECT * FROM talent_work_calendar_day WHERE id='day-history'"))
          .isEqualTo(day);
      assertThat(jdbc.queryForMap("SELECT * FROM talent_resume_lifecycle WHERE id='cycle-history'"))
          .isEqualTo(cycle);
      assertThat(jdbc.queryForObject("SELECT published_revision_id FROM talent_work_calendar_year WHERE calendar_year=2026",
          String.class)).isEqualTo("calendar-history");
      assertThatThrownBy(() -> jdbc.update("INSERT INTO talent_resume_lifecycle "
          + "(id,candidate_id,resume_version_id,cycle_no,active_marker,entered_at,phase,warning_level) "
          + "VALUES ('duplicate-active','candidate','resume',2,'Y',UTC_TIMESTAMP(),'PENDING_SCREENING','NORMAL')"))
          .isInstanceOf(DataIntegrityViolationException.class);
      jdbc.update("UPDATE talent_resume_lifecycle SET active_marker=NULL,phase='CLOSED' WHERE id='cycle-history'");
      jdbc.update("INSERT INTO talent_resume_lifecycle "
          + "(id,candidate_id,resume_version_id,cycle_no,active_marker,entered_at,phase,warning_level) "
          + "VALUES ('returned-cycle','candidate','resume',2,'Y',UTC_TIMESTAMP(),'PENDING_SCREENING','NORMAL')");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM talent_resume_lifecycle WHERE resume_version_id='resume'",
          Integer.class)).isEqualTo(2);
    });
  }

  @Test
  void realMapperLocksSerializeTheCurrentCycleAndReadPublishedCalendar() throws Exception {
    withDatabase((connection, isolatedUrl) -> {
      var source = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(source);
      jdbc.execute("CREATE TABLE talent_candidate(id CHAR(26) PRIMARY KEY) ENGINE=InnoDB");
      script(connection, "hr_ai_upgrade_20260914_07__work_calendar.sql");
      script(connection, "hr_ai_upgrade_20260914_08__resume_lifecycle.sql");
      seed(jdbc);
      var configuration = new MybatisConfiguration();
      configuration.setMapUnderscoreToCamelCase(true);
      configuration.setEnvironment(new Environment("lifecycle-it", new JdbcTransactionFactory(), source));
      configuration.addMapper(ResumeLifecycleMapper.class);
      configuration.addMapper(WorkCalendarMapper.class);
      var factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
      try (Connection competitor = DriverManager.getConnection(isolatedUrl,
          required("HR_DB_USER"), required("HR_DB_PASSWORD"))) {
        connection.setAutoCommit(false);
        competitor.setAutoCommit(false);
        try (Statement settings = competitor.createStatement()) {
          settings.execute("SET SESSION innodb_lock_wait_timeout=1");
        }
        try (var first = factory.openSession(connection); var second = factory.openSession(competitor)) {
          var locked = first.getMapper(ResumeLifecycleMapper.class).lockCurrent("resume");
          assertThat(locked.getWarningLevel()).isEqualTo("RED");
          assertThat(locked.getElapsedWorkdays()).isEqualTo(3);
          assertThat(first.getMapper(WorkCalendarMapper.class).findYear(2026).getPublishedRevisionId())
              .isEqualTo("calendar-history");
          assertThatThrownBy(() -> second.getMapper(ResumeLifecycleMapper.class).lockCurrent("resume"))
              .isInstanceOf(PersistenceException.class).hasMessageContaining("Lock wait timeout");
          second.rollback(true);
          locked.setPhase("SHARED"); locked.setVersion(locked.getVersion() + 1);
          first.getMapper(ResumeLifecycleMapper.class).updateById(locked);
          first.commit(true);
          var current = second.getMapper(ResumeLifecycleMapper.class).lockCurrent("resume");
          assertThat(current.getPhase()).isEqualTo("SHARED");
          assertThat(current.getVersion()).isEqualTo(8);
          second.rollback(true);
        }
      }
    });
  }

  private static void seed(JdbcTemplate jdbc) {
    jdbc.update("INSERT INTO talent_work_calendar_revision "
        + "(id,calendar_year,revision,status,source_note,version) VALUES "
        + "('calendar-history',2026,1,'PUBLISHED','已确认的历史日历',7)");
    jdbc.update("INSERT INTO talent_work_calendar_year (calendar_year,published_revision_id) VALUES (2026,'calendar-history')");
    jdbc.update("INSERT INTO talent_work_calendar_day "
        + "(id,calendar_revision_id,calendar_date,day_type,description) VALUES "
        + "('day-history','calendar-history','2026-09-20','WORKDAY','调休补班')");
    jdbc.update("INSERT INTO talent_resume_lifecycle "
        + "(id,candidate_id,resume_version_id,cycle_no,active_marker,entered_at,phase,warning_level,"
        + "elapsed_workdays,calendar_revisions,version) VALUES ('cycle-history','candidate','resume',1,'Y',"
        + "'2026-09-14 02:00:00','PENDING_DISPOSAL','RED',3,'{\"2026\":\"calendar-history\"}',7)");
  }

  private static void withDatabase(DatabaseWork work) throws Exception {
    String sourceUrl = required("HR_DB_URL");
    URI endpoint = URI.create(sourceUrl.substring("jdbc:".length()));
    if (!"mysql".equals(endpoint.getScheme()) || endpoint.getRawUserInfo() != null)
      throw new IllegalArgumentException("External tests require a plain MySQL JDBC endpoint");
    String schema = "hr_lifecycle_it_" + UUID.randomUUID().toString().replace("-", "");
    String isolatedUrl = "jdbc:mysql://" + endpoint.getRawAuthority() + "/" + schema
        + (endpoint.getRawQuery() == null ? "" : "?" + endpoint.getRawQuery());
    try (Connection administration = DriverManager.getConnection(sourceUrl, required("HR_DB_USER"), required("HR_DB_PASSWORD"));
        Statement command = administration.createStatement()) {
      command.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
      try (Connection isolated = DriverManager.getConnection(isolatedUrl, required("HR_DB_USER"), required("HR_DB_PASSWORD"))) {
        assertThat(isolated.getCatalog()).isEqualTo(schema);
        assertThat(isolated.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
        assertThat(isolated.getMetaData().getDatabaseMajorVersion()).isGreaterThanOrEqualTo(8);
        work.run(isolated, isolatedUrl);
      } finally {
        command.execute("DROP DATABASE `" + schema + "`");
      }
    }
  }

  private static void script(Connection connection, String filename) {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null && !Files.isRegularFile(current.resolve("docs/sql/hr_ai_complete_schema.sql")))
      current = current.getParent();
    if (current == null) throw new IllegalStateException("无法定位仓库根目录");
    ScriptUtils.executeSqlScript(connection, new EncodedResource(
        new FileSystemResource(current.resolve("docs/sql").resolve(filename)), StandardCharsets.UTF_8));
  }

  private static String required(String name) {
    String value = System.getenv(name);
    assertThat(value).as("必须显式配置测试环境变量 %s", name).isNotBlank();
    return value;
  }

  @FunctionalInterface
  private interface DatabaseWork { void run(Connection connection, String isolatedUrl) throws Exception; }
}
