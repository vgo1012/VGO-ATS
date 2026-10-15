package com.ezaigc.hr.intelligence.search.service.impl;

import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.RESUME_FACTS_APPROVED_INDEX;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.RESUME_FACTS_PROJECTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import com.ezaigc.hr.intelligence.search.domain.entity.ProjectionCheckpoint;
import com.ezaigc.hr.intelligence.search.domain.entity.StateReplayCheckpoint;
import com.ezaigc.hr.intelligence.search.mapper.ProjectionCheckpointMapper;
import com.ezaigc.hr.intelligence.search.mapper.StateReplayCheckpointMapper;
import java.time.Instant;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** 核验实际 MyBatis 条件和当前读 SQL；不代替显式启用的真实 MySQL 集成测试。 */
class SearchIndexApprovalPersistenceTest {
  private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");

  @BeforeAll
  static void metadata() {
    var configuration = new MybatisConfiguration();
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "approval-test"), ProjectionCheckpoint.class);
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "state-test"), StateReplayCheckpoint.class);
  }

  @Test
  void bothCurrentReadsUseTheIndependentApprovalRowAndRequiredLockMode() throws Exception {
    var mapper = mock(ProjectionCheckpointMapper.class);
    var service = service(mapper);
    var target = new SearchIndexTarget("approved-physical", 7);
    when(mapper.approvedCurrent(RESUME_FACTS_APPROVED_INDEX)).thenReturn(target);
    when(mapper.approvedForUpdate(RESUME_FACTS_APPROVED_INDEX)).thenReturn(target);
    assertThat(service.approvedIndex()).isSameAs(target);
    assertThat(service.lockApprovedIndex()).isSameAs(target);
    verify(mapper).approvedCurrent(RESUME_FACTS_APPROVED_INDEX);
    verify(mapper).approvedForUpdate(RESUME_FACTS_APPROVED_INDEX);
    assertThat(ProjectionCheckpointMapper.class.getMethod("approvedCurrent", String.class)
        .getAnnotation(Select.class).value()).singleElement()
        .satisfies(sql -> assertThat(sql).contains("WHERE projection_name=#{name}").endsWith("FOR SHARE"));
    assertThat(ProjectionCheckpointMapper.class.getMethod("approvedForUpdate", String.class)
        .getAnnotation(Select.class).value()).singleElement()
        .satisfies(sql -> assertThat(sql).contains("WHERE projection_name=#{name}").endsWith("FOR UPDATE"));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  @SuppressWarnings({"rawtypes", "unchecked"})
  void approvalCasCannotUpdateAnObservedRowAndReportsALostGeneration(int rows) {
    var mapper = mock(ProjectionCheckpointMapper.class);
    when(mapper.update(isNull(), any())).thenReturn(rows);
    assertThat(service(mapper).approveIndex("approved-physical", 7, "actor", NOW)).isEqualTo(rows == 1);
    ArgumentCaptor<LambdaUpdateWrapper<ProjectionCheckpoint>> capture = ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
    verify(mapper).update(isNull(), capture.capture());
    var update = capture.getValue();
    assertThat(update.getSqlSegment()).contains("projection_name =", "version =");
    var where = update.getParamNameValuePairs().entrySet().stream()
        .filter(entry -> update.getSqlSegment().contains("." + entry.getKey() + "}"))
        .map(entry -> entry.getValue()).toList();
    assertThat(where).containsExactlyInAnyOrder(RESUME_FACTS_APPROVED_INDEX, 7L);
    assertThat(update.getSqlSet()).contains("index_name", "version=version + 1");
    assertThat(update.getParamNameValuePairs().values()).doesNotContain(RESUME_FACTS_PROJECTION);
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, 2147483647L, 2147483648L})
  void generationOverflowOrInvalidGenerationIsRejectedBeforeAnyWrite(long generation) {
    var mapper = mock(ProjectionCheckpointMapper.class);
    assertThatThrownBy(() -> service(mapper).approveIndex("approved-physical", generation, "actor", NOW))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(mapper);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void approvalResetsBothObservedNamespacesWithoutChangingTheApprovalGeneration() {
    var facts = mock(ProjectionCheckpointMapper.class);
    var state = mock(StateReplayCheckpointMapper.class);
    when(facts.update(isNull(), any())).thenReturn(1);
    when(state.update(isNull(), any())).thenReturn(1);
    var stateService = new StateReplayCheckpointServiceImpl();
    ReflectionTestUtils.setField(stateService, "baseMapper", state);
    assertThat(service(facts).resetForApprovedIndex("approved-physical", Instant.EPOCH, "actor", NOW)).isTrue();
    assertThat(stateService.resetForApprovedIndex("approved-physical", "actor", NOW)).isTrue();
    ArgumentCaptor<LambdaUpdateWrapper<ProjectionCheckpoint>> factCapture = ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
    ArgumentCaptor<LambdaUpdateWrapper<StateReplayCheckpoint>> stateCapture = ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
    verify(facts).update(isNull(), factCapture.capture());
    verify(state).update(isNull(), stateCapture.capture());
    for (var update : new LambdaUpdateWrapper<?>[] {factCapture.getValue(), stateCapture.getValue()}) {
      assertThat(update.getSqlSegment()).contains("projection_name =");
      assertThat(update.getParamNameValuePairs().values()).contains(RESUME_FACTS_PROJECTION)
          .doesNotContain(RESUME_FACTS_APPROVED_INDEX);
      assertThat(update.getSqlSet()).contains("index_name", "projected_count", "version=version + 1");
    }
    assertThat(factCapture.getValue().getSqlSet()).contains("last_created_at", "last_snapshot_id");
    assertThat(stateCapture.getValue().getSqlSet()).contains("last_candidate_id", "completed");
  }

  private static ProjectionCheckpointServiceImpl service(ProjectionCheckpointMapper mapper) {
    var service = new ProjectionCheckpointServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    return service;
  }
}
