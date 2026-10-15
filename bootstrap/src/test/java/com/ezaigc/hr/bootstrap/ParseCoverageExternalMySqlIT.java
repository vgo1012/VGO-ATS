package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.reporting.mapper.MetricMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
class ParseCoverageExternalMySqlIT {
  @Test
  void repeatedParsingDoesNotInflateCoverageAndScopeAndDateStillApply() throws Exception {
    try (Connection connection = DriverManager.getConnection(System.getenv("HR_DB_URL"),
        System.getenv("HR_DB_USER"), System.getenv("HR_DB_PASSWORD"))) {
      // 只建连接私有临时表；关闭连接自动移除，不改动同名业务表。
      try (var statement = connection.createStatement()) {
        statement.execute("CREATE TEMPORARY TABLE reporting_metric_event (event_type VARCHAR(40),"
            + "source_id VARCHAR(26),occurred_at DATETIME(6),owner_user_id VARCHAR(26),"
            + "department_id VARCHAR(26),jd_id VARCHAR(26),jd_version_id VARCHAR(26))");
        statement.execute("CREATE TEMPORARY TABLE talent_resume_parse_snapshot (id VARCHAR(26),resume_version_id VARCHAR(26))");
        statement.execute("INSERT INTO reporting_metric_event VALUES"
            + "('RESUME_INGESTED','r1','2026-09-07 10:00:00','owner','dept','jd','v1'),"
            + "('RESUME_INGESTED','r2','2026-09-07 11:00:00','owner','dept','jd','v2'),"
            + "('RESUME_INGESTED','r3','2026-09-07 12:00:00','other','other-dept','jd2','v3'),"
            + "('RESUME_INGESTED','old','2026-09-06 23:59:59','owner','dept','jd','v1'),"
            + "('RESUME_INGESTED','future','2026-09-08 00:00:00','owner','dept','jd','v1')");
        statement.execute("INSERT INTO talent_resume_parse_snapshot VALUES"
            + "('s1','r1'),('s2','r1'),('s3','r1'),('s4','r3'),('s5','old'),('s6','future')");
      }
      var configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(),
          new SingleConnectionDataSource(connection, true)));
      configuration.setMapUnderscoreToCamelCase(true);
      configuration.addMapper(MetricMapper.class);
      try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(connection)) {
        var mapper = session.getMapper(MetricMapper.class);
        var from = Instant.parse("2026-09-06T16:00:00Z");
        var to = Instant.parse("2026-09-07T16:00:00Z");
        var own = mapper.parseCoverage(from, to, null, null, null, false, "owner", List.of(), List.of());
        assertThat(own.getResumeVersionCount()).isEqualTo(2);
        assertThat(own.getParsedResumeVersionCount()).isEqualTo(1);
        var all = mapper.parseCoverage(from, to, null, null, null, true, "owner", List.of(), List.of());
        assertThat(all.getResumeVersionCount()).isEqualTo(3);
        assertThat(all.getParsedResumeVersionCount()).isEqualTo(2);
        assertThat(mapper.parseCoverage(from, to, null, null, "other", false, "owner", List.of(), List.of()).getResumeVersionCount()).isZero();
        assertThat(mapper.parseCoverage(from, to, null, null, null, false, "manager", List.of("owner"), List.of()).getResumeVersionCount()).isEqualTo(2);
        assertThat(mapper.parseCoverage(from, to, null, null, null, false, "hr", List.of(), List.of("dept")).getParsedResumeVersionCount()).isEqualTo(1);
        assertThat(mapper.parseCoverage(from, to, "jd", "v2", null, true, "owner", List.of(), List.of()).getParsedResumeVersionCount()).isZero();
        assertThat(mapper.parseCoverage(from, to, "missing", null, null, true, "owner", List.of(), List.of()).getResumeVersionCount()).isZero();
      }
    }
  }
}
