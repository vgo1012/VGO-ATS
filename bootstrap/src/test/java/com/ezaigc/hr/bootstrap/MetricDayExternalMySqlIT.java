package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.reporting.domain.dto.MetricDayDTO;
import com.ezaigc.hr.reporting.mapper.MetricMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
class MetricDayExternalMySqlIT {
  @Test
  void dailyBucketsAgreeWithShanghaiRangeAndInstantMapping() throws Exception {
    try (Connection connection = DriverManager.getConnection(System.getenv("HR_DB_URL"),
        System.getenv("HR_DB_USER"), System.getenv("HR_DB_PASSWORD"))) {
      // 临时表仅在本连接可见，不修改同名正式业务表。
      try (var statement = connection.createStatement()) {
        statement.execute("CREATE TEMPORARY TABLE reporting_metric_event (event_type VARCHAR(40),"
            + " occurred_at DATETIME(6),value_decimal DECIMAL(10,2),owner_user_id VARCHAR(26),"
            + " department_id VARCHAR(26),jd_id VARCHAR(26),jd_version_id VARCHAR(26))");
      }
      try (var insert = connection.prepareStatement("INSERT INTO reporting_metric_event"
          + " (event_type,occurred_at,value_decimal,owner_user_id) VALUES ('RESUME_INGESTED',?,1,'owner')")) {
        for (String time : List.of("2026-09-07T09:00:00Z", "2026-09-07T15:59:59Z", "2026-09-07T16:00:00Z")) {
          insert.setTimestamp(1, Timestamp.from(Instant.parse(time)));
          insert.executeUpdate();
        }
      }
      var configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(),
          new SingleConnectionDataSource(connection, true)));
      configuration.setMapUnderscoreToCamelCase(true);
      configuration.addMapper(MetricMapper.class);
      try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(connection)) {
        var mapper = session.getMapper(MetricMapper.class);
        var rows = mapper.daily(Instant.parse("2026-09-06T16:00:00Z"),
            Instant.parse("2026-09-08T16:00:00Z"), null, null, "owner", false, "owner", List.of(), List.of());
        assertThat(rows).extracting(MetricDayDTO::getDate)
            .containsExactly(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 8));
        assertThat(rows).extracting(MetricDayDTO::getEventCount).containsExactly(2L, 1L);
        assertThat(mapper.daily(Instant.parse("2026-09-06T16:00:00Z"),
            Instant.parse("2026-09-07T16:00:00Z"), null, null, "owner", false, "owner", List.of(), List.of()))
            .singleElement().satisfies(row -> {
              assertThat(row.getDate()).isEqualTo(LocalDate.of(2026, 9, 7));
              assertThat(row.getEventCount()).isEqualTo(2);
            });
        assertThat(mapper.daily(Instant.parse("2026-09-06T16:00:00Z"),
            Instant.parse("2026-09-08T16:00:00Z"), null, null, null, false, "other", List.of(), List.of())).isEmpty();
      }
    }
  }
}
