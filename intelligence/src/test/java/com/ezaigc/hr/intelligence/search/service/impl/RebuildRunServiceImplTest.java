package com.ezaigc.hr.intelligence.search.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.intelligence.search.domain.entity.RebuildRun;
import com.ezaigc.hr.intelligence.search.enums.SearchRebuildStatus;
import com.ezaigc.hr.intelligence.search.mapper.RebuildRunMapper;
import java.time.Instant;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class RebuildRunServiceImplTest {
  private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");

  @BeforeAll
  static void initializeTableMetadata() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), RebuildRun.class);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void approvedCompletionRequiresTheExactTaskAttemptTargetAndRunningBusinessState() {
    RebuildRunMapper mapper = mock(RebuildRunMapper.class);
    RebuildRunServiceImpl service = new RebuildRunServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    when(mapper.update(isNull(), any())).thenReturn(1);
    assertThat(service.finishApproved("run-1", "task-1", "resume-facts-vrun-1-attempt-2", 3, "actor-1", NOW)).isTrue();
    ArgumentCaptor<LambdaUpdateWrapper<RebuildRun>> updates = ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
    verify(mapper).update(isNull(), updates.capture());
    var update = updates.getValue();
    assertThat(update.getSqlSegment()).contains("id =", "task_id =", "target_index =", "status =");
    var where = update.getParamNameValuePairs().entrySet().stream()
        .filter(entry -> update.getSqlSegment().contains("." + entry.getKey() + "}"))
        .map(entry -> entry.getValue()).toList();
    assertThat(where).containsExactlyInAnyOrder("run-1", "task-1", "resume-facts-vrun-1-attempt-2", "RUNNING");
    assertThat(update.getParamNameValuePairs().values()).contains("SUCCEEDED", 3L, NOW);
    assertThat(update.getSqlSet()).contains("status", "finished_at", "error_code", "version=version + 1");
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void newAttemptChangesTargetAndClearsOldOutcomeWithoutReopeningTerminalRuns() {
    RebuildRunMapper mapper = mock(RebuildRunMapper.class);
    RebuildRunServiceImpl service = new RebuildRunServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    when(mapper.update(isNull(), any())).thenReturn(1);

    assertThat(service.start("run-1", "resume-facts-vrun-1-attempt-2", "actor-1", NOW)).isTrue();

    ArgumentCaptor<LambdaUpdateWrapper<RebuildRun>> updates = ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
    verify(mapper).update(isNull(), updates.capture());
    var update = updates.getValue();
    assertThat(update.getSqlSegment()).contains("id =", "target_index <>", "status IN");
    assertThat(update.getSqlSet()).contains("target_index", "projected_count", "error_code", "finished_at",
        "started_at", "update_time", "update_id", "version=version + 1");
    assertThat(update.getParamNameValuePairs().values())
        .contains("run-1", "resume-facts-vrun-1-attempt-2", "QUEUED", "FAILED", "RUNNING", 0, null, "actor-1", NOW)
        .doesNotContain("SUCCEEDED", "CANCELLED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUCCEEDED", "FAILED", "CANCELLED"})
  @SuppressWarnings({"rawtypes", "unchecked"})
  void cancellationCanFinishBeforeStartButOtherOutcomesRequireRunning(String outcome) {
    RebuildRunMapper mapper = mock(RebuildRunMapper.class);
    RebuildRunServiceImpl service = new RebuildRunServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    when(mapper.update(isNull(), any())).thenReturn(1);

    assertThat(service.finish("run-1", SearchRebuildStatus.fromCode(outcome), 0, null, "actor-1", NOW)).isTrue();

    ArgumentCaptor<LambdaUpdateWrapper<RebuildRun>> updates = ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
    verify(mapper).update(isNull(), updates.capture());
    var update = updates.getValue();
    String predicate = update.getSqlSegment();
    var whereValues = update.getParamNameValuePairs().entrySet().stream()
        .filter(entry -> predicate.contains("." + entry.getKey() + "}"))
        .map(entry -> entry.getValue()).toList();
    if (outcome.equals("CANCELLED")) {
      assertThat(whereValues).containsExactlyInAnyOrder("run-1", "QUEUED", "FAILED", "RUNNING");
    } else {
      assertThat(whereValues).containsExactlyInAnyOrder("run-1", "RUNNING");
    }
  }
}
