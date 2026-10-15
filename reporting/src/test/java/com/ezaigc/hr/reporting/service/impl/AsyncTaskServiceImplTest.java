package com.ezaigc.hr.reporting.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.reporting.domain.entity.AsyncTaskEntity;
import com.ezaigc.hr.reporting.mapper.AsyncTaskMapper;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;

class AsyncTaskServiceImplTest {

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void businessIdentityLookupUsesCompleteBoundedCurrentReadWithoutActorFilter() {
    AsyncTaskMapper mapper = mock(AsyncTaskMapper.class);
    AsyncTaskServiceImpl service = new AsyncTaskServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    AsyncTaskEntity entity = new AsyncTaskEntity();
    entity.setId(TASK_ID);
    entity.setStatus("FAILED");
    entity.setRequestedByUserId("original-requester");
    when(mapper.selectList(any())).thenReturn(List.of(entity));

    var results = service.findTasksByResourceIdempotencyForUpdate(
        "RESUME_INTAKE_ADOPT", "RESUME_VERSION", "resume-1", "intake-adopt:resume-1:p-1");

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().requestedByUserId()).isEqualTo("original-requester");
    ArgumentCaptor<LambdaQueryWrapper<AsyncTaskEntity>> query = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectList(query.capture());
    assertThat(query.getValue().getSqlSegment())
        .contains("task_type", "resource_type", "resource_id", "idempotency_key", "LIMIT 2 FOR UPDATE")
        .doesNotContain("requested_by_user_id");
    assertThat(query.getValue().getParamNameValuePairs().values()).containsExactlyInAnyOrder(
        "RESUME_INTAKE_ADOPT", "RESUME_VERSION", "resume-1", "intake-adopt:resume-1:p-1");
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void currentTaskSnapshotComesFromLockedEntityRead() {
    AsyncTaskMapper mapper = mock(AsyncTaskMapper.class);
    AsyncTaskServiceImpl service = new AsyncTaskServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    AsyncTaskEntity entity = new AsyncTaskEntity();
    entity.setId(TASK_ID);
    entity.setStatus("CANCELLING");
    entity.setAttemptCount(2);
    entity.setMaxAttempts(3);
    entity.setProgressPercent(15);
    entity.setVersion(7);
    when(mapper.selectOne(any(), eq(true))).thenReturn(entity);

    var result = service.findTaskForUpdate(TASK_ID);

    assertThat(result.id()).isEqualTo(TASK_ID);
    assertThat(result.status()).isEqualTo("CANCELLING");
    assertThat(result.attemptCount()).isEqualTo(2);
    assertThat(result.version()).isEqualTo(7);
    ArgumentCaptor<LambdaQueryWrapper<AsyncTaskEntity>> query = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectOne(query.capture(), eq(true));
    assertThat(query.getValue().getSqlSegment()).contains("id", "FOR UPDATE");
    assertThat(query.getValue().getParamNameValuePairs().values()).contains(TASK_ID);
  }

  @Test
  void missingCurrentTaskSnapshotReturnsNull() {
    AsyncTaskMapper mapper = mock(AsyncTaskMapper.class);
    AsyncTaskServiceImpl service = new AsyncTaskServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    assertThat(service.findTaskForUpdate(TASK_ID)).isNull();
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void filtersResourceBeforeLimitingRecentTasks() {
    AsyncTaskMapper mapper = mock(AsyncTaskMapper.class);
    AsyncTaskServiceImpl service = new AsyncTaskServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
      IPage<AsyncTaskEntity> page = invocation.getArgument(0);
      return page.setRecords(List.of());
    });
    service.findRecentTasks(null, "RESUME_PARSE", null, "RESUME_VERSION", TASK_ID, null, null, 1);
    ArgumentCaptor<LambdaQueryWrapper<AsyncTaskEntity>> query = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectPage(any(), query.capture());
    assertThat(query.getValue().getSqlSegment()).contains("resource_type", "resource_id", "task_type", "ORDER BY create_time DESC,id DESC");
    assertThat(query.getValue().getParamNameValuePairs().values()).contains("RESUME_VERSION", TASK_ID, "RESUME_PARSE");
  }

  private static final String TASK_ID = "01KTASK0000000000000000000";
  private static final String ACTOR_ID = "00000000000000000000000000";
  private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");

  @BeforeAll
  static void initializeTableMetadata() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "test"), AsyncTaskEntity.class);
  }

  @Test
  void asyncTaskPersistsLeaseDeadlineForCrashRecovery() {
    assertThat(ReflectionUtils.findField(AsyncTaskEntity.class, "leaseUntil")).isNotNull();
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void failureUpdateAtomicallyRequiresRunningStateAndActiveAttemptLease() {
    AsyncTaskMapper mapper = mock(AsyncTaskMapper.class);
    AsyncTaskServiceImpl service = new AsyncTaskServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    ArgumentCaptor<LambdaUpdateWrapper<AsyncTaskEntity>> wrapperCaptor =
        ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
    when(mapper.update(isNull(), wrapperCaptor.capture())).thenReturn(1);

    int updated =
        service.markFailed(TASK_ID, 2, "TEMPORARY_FAILURE", "暂时失败", true, NOW, ACTOR_ID);

    assertThat(updated).isEqualTo(1);
    LambdaUpdateWrapper<AsyncTaskEntity> wrapper = wrapperCaptor.getValue();
    assertThat(wrapper.getSqlSegment()).contains("id", "status", "attempt_count", "lease_until");
    assertThat(wrapper.getParamNameValuePairs().values())
        .contains(TASK_ID, "RUNNING", 2, NOW)
        .doesNotContain("CANCELLING");
    verify(mapper).update(isNull(), same(wrapper));
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void recentTaskQueryUsesBoundedParameterizedPage() {
    AsyncTaskMapper mapper = mock(AsyncTaskMapper.class);
    AsyncTaskServiceImpl service = new AsyncTaskServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    when(mapper.selectPage(any(), any()))
        .thenAnswer(
            invocation -> {
              IPage<AsyncTaskEntity> page = invocation.getArgument(0);
              return page.setRecords(List.of());
            });

    assertThat(service.findRecentTasks(null, null, null, null, null, Integer.MAX_VALUE))
        .isEmpty();

    ArgumentCaptor<IPage<AsyncTaskEntity>> pageCaptor =
        ArgumentCaptor.forClass((Class) IPage.class);
    verify(mapper).selectPage(pageCaptor.capture(), any());
    assertThat(pageCaptor.getValue().getSize()).isEqualTo(200);
    assertThat(pageCaptor.getValue().searchCount()).isFalse();
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void idempotencyWinnerUsesALockingCurrentReadAfterInsertCompetition() {
    AsyncTaskMapper mapper = mock(AsyncTaskMapper.class);
    AsyncTaskServiceImpl service = new AsyncTaskServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    ArgumentCaptor<LambdaQueryWrapper<AsyncTaskEntity>> query =
        ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);

    service.findTaskByIdempotencyForUpdate(ACTOR_ID, "same-request");

    verify(mapper).selectOne(query.capture(), eq(true));
    assertThat(query.getValue().getSqlSegment())
        .contains("requested_by_user_id", "idempotency_key", "FOR UPDATE");
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void idempotencyWinnerFingerprintUsesALockingCurrentRead() {
    AsyncTaskMapper mapper = mock(AsyncTaskMapper.class);
    AsyncTaskServiceImpl service = new AsyncTaskServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    ArgumentCaptor<LambdaQueryWrapper<AsyncTaskEntity>> query =
        ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);

    service.findEntityByIdForUpdate("task-id");

    verify(mapper).selectOne(query.capture(), eq(true));
    assertThat(query.getValue().getSqlSegment()).contains("id", "FOR UPDATE");
  }
}
