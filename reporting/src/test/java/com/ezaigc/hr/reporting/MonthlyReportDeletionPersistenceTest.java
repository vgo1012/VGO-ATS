package com.ezaigc.hr.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportEntity;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportFileEntity;
import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportStatus;
import com.ezaigc.hr.reporting.monthlyreport.mapper.MonthlyReportMapper;
import com.ezaigc.hr.reporting.monthlyreport.mapper.MonthlyReportFileMapper;
import com.ezaigc.hr.reporting.monthlyreport.service.impl.MonthlyReportServiceImpl;
import com.ezaigc.hr.reporting.monthlyreport.service.impl.MonthlyReportFileServiceImpl;
import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.mapping.BoundSql;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** Verifies MyBatis SQL generation and pagination inputs without claiming database execution. */
class MonthlyReportDeletionPersistenceTest {
  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void cleanupUsesBoundedExpiryAndIdKeysetWithoutSkippingEqualExpiryRows() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    configuration.addMapper(MonthlyReportFileMapper.class);
    MonthlyReportFileMapper mapper = mock(MonthlyReportFileMapper.class);
    MonthlyReportFileServiceImpl service = new MonthlyReportFileServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    when(mapper.selectPage(any(Page.class), any())).thenAnswer(call -> call.getArgument(0));
    Instant now = Instant.parse("2026-09-14T00:00:00Z");
    Instant expiry = now.minusSeconds(1);
    service.expiredAfter(now, expiry, "cursor-id", 20);
    ArgumentCaptor<Page<MonthlyReportFileEntity>> page = ArgumentCaptor.forClass((Class) Page.class);
    ArgumentCaptor<LambdaQueryWrapper<MonthlyReportFileEntity>> query = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectPage(page.capture(), query.capture());
    assertThat(page.getValue().getSize()).isEqualTo(20);
    assertThat(page.getValue().searchCount()).isFalse();
    BoundSql bound = configuration.getMappedStatement(MonthlyReportFileMapper.class.getName() + ".selectList")
        .getBoundSql(Map.of("ew", query.getValue()));
    assertThat(bound.getSql().replaceAll("\\s+", " "))
        .contains("deleted = ?", "expires_at <= ?", "expires_at > ? OR (expires_at = ? AND id > ?)",
            "ORDER BY expires_at ASC,id ASC");
    assertThat(query.getValue().getParamNameValuePairs()).containsValues(false, now, expiry, "cursor-id");
  }

  @Test
  void deletionUpdateBindsNullSnapshotAndRemarksInsteadOfOmittingThem() {
    MybatisConfiguration configuration = configuration();
    MonthlyReportEntity report = new MonthlyReportEntity();
    report.setId("R".repeat(26));
    report.setStatus(MonthlyReportStatus.DELETED.getCode());
    report.setSnapshotJson(null);
    report.setManagementRemarks(null);

    BoundSql bound = configuration.getMappedStatement(MonthlyReportMapper.class.getName() + ".updateById")
        .getBoundSql(Map.of("et", report));

    assertThat(bound.getSql().replaceAll("\\s+", " "))
        .containsPattern("snapshot_json\\s*=\\s*\\?")
        .containsPattern("management_remarks\\s*=\\s*\\?");
    assertThat(bound.getParameterMappings()).extracting(mapping -> mapping.getProperty())
        .contains("et.snapshotJson", "et.managementRemarks");
    assertThat(configuration.newMetaObject(bound.getParameterObject()).getValue("et.snapshotJson")).isNull();
    assertThat(configuration.newMetaObject(bound.getParameterObject()).getValue("et.managementRemarks")).isNull();
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void recentHistoryExcludesDeletedInTheQueryPassedToPagination() {
    MybatisConfiguration configuration = configuration();
    MonthlyReportMapper mapper = mock(MonthlyReportMapper.class);
    MonthlyReportServiceImpl service = new MonthlyReportServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    when(mapper.selectPage(any(Page.class), any())).thenAnswer(call -> call.getArgument(0));
    Instant cursorTime = Instant.parse("2026-09-14T00:00:00Z");

    assertThat(service.recent("2026-08", cursorTime, "C".repeat(26), 101)).isEmpty();

    ArgumentCaptor<Page<MonthlyReportEntity>> page = ArgumentCaptor.forClass((Class) Page.class);
    ArgumentCaptor<LambdaQueryWrapper<MonthlyReportEntity>> query = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectPage(page.capture(), query.capture());
    assertThat(page.getValue().getSize()).isEqualTo(101);
    assertThat(page.getValue().searchCount()).isFalse();
    BoundSql bound = configuration.getMappedStatement(MonthlyReportMapper.class.getName() + ".selectList")
        .getBoundSql(Map.of("ew", query.getValue()));
    assertThat(bound.getSql().replaceAll("\\s+", " "))
        .containsPattern("WHERE.*status <> \\?.*ORDER BY")
        .contains("report_month = ?", "create_time < ?", "id < ?")
        .doesNotContain("DELETED");
    assertThat(query.getValue().getParamNameValuePairs())
        .containsValues("2026-08", "DELETED", cursorTime, "C".repeat(26));
  }

  private static MybatisConfiguration configuration() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    configuration.addMapper(MonthlyReportMapper.class);
    return configuration;
  }
}
