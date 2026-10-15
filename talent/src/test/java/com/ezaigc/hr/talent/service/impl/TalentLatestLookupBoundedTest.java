package com.ezaigc.hr.talent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.talent.domain.entity.ExtractionRun;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.entity.IdentityConflict;
import com.ezaigc.hr.talent.mapper.ExtractionRunMapper;
import com.ezaigc.hr.talent.mapper.IdentityConflictMapper;
import com.ezaigc.hr.talent.mapper.ResumeParsingMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TalentLatestLookupBoundedTest {
  @Test
  void latestSuccessfulExtractionReadsOneRowWithoutCountingHistory() {
    var mapper = mock(ExtractionRunMapper.class);
    var service = new ExtractionRunPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    var selected = new ExtractionRun();
    selected.setId("latest-extraction");
    var queried = bounded(mapper, ExtractionRun.class, selected,
        "resume_version_id", "SUCCEEDED", "ORDER BY create_time DESC,id DESC");

    assertThat(service.findLatestSuccessfulExtraction("resume-version")).isEqualTo(selected.getId());
    assertThat(queried).isTrue();
    assertThat(service.findLatestSuccessfulExtraction("resume-version")).isNull();
  }

  @Test
  void openConflictReadsOnlyNewestRowWithoutCountingHistory() {
    var mapper = mock(IdentityConflictMapper.class);
    var service = new IdentityConflictPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    var selected = new IdentityConflict();
    var queried = bounded(mapper, IdentityConflict.class, selected,
        "intake_id", "OPEN", "ORDER BY create_time DESC,id DESC");

    assertThat(service.findOpenConflictForIntake("intake")).isSameAs(selected);
    assertThat(queried).isTrue();
    assertThat(service.findOpenConflictForIntake("intake")).isNull();
  }

  @Test
  void resolvedConflictRetainsResolutionTimeOrderAndEmptyResult() {
    var mapper = mock(IdentityConflictMapper.class);
    var service = new IdentityConflictPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    var selected = new IdentityConflict();
    var queried = bounded(mapper, IdentityConflict.class, selected,
        "intake_id", "RESOLVED", "ORDER BY resolved_at DESC,id DESC");

    assertThat(service.findResolvedConflictForIntake("intake")).isSameAs(selected);
    assertThat(queried).isTrue();
    assertThat(service.findResolvedConflictForIntake("intake")).isNull();
  }

  @Test
  void parseCacheReadsEarliestFactVersionWithoutLoadingEveryCorrection() {
    var mapper = mock(ResumeParsingMapper.class);
    var service = new ResumeParsingPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    var selected = new FactSnapshot();
    var queried = bounded(mapper, FactSnapshot.class, selected,
        "parse_snapshot_id", "parse-snapshot", "ORDER BY snapshot_version ASC");

    assertThat(service.findFactSnapshotByParse("parse-snapshot")).isSameAs(selected);
    assertThat(queried).isTrue();
    assertThat(service.findFactSnapshotByParse("parse-snapshot")).isNull();
  }

  /** 首条历史记录必须在数据库侧限行，避免游标查询仍执行无界 SQL。 */
  private static <T> AtomicBoolean bounded(BaseMapper<T> mapper, Class<T> entityType, T selected,
      String filterColumn, String filterValue, String order) {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "bounded-lookup"), entityType);
    var queried = new AtomicBoolean();
    when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
      IPage<T> page = invocation.getArgument(0);
      LambdaQueryWrapper<T> query = invocation.getArgument(1);
      assertThat(page.getCurrent()).isEqualTo(1);
      assertThat(page.getSize()).isEqualTo(1);
      assertThat(page.searchCount()).isFalse();
      assertThat(query.getSqlSegment()).contains(filterColumn, order);
      assertThat(query.getParamNameValuePairs()).containsValue(filterValue);
      return page.setRecords(queried.getAndSet(true) ? List.of() : List.of(selected));
    });
    return queried;
  }
}
