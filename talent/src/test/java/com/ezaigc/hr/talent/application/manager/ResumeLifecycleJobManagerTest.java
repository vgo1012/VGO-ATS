package com.ezaigc.hr.talent.application.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ezaigc.hr.talent.config.ResumeLifecycleProperties;
import com.ezaigc.hr.talent.domain.dto.ResumeLifecycleReadyDTO;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycle;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycleRun;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycleRuntime;
import com.ezaigc.hr.talent.service.ResumeLifecyclePersistenceService;
import com.ezaigc.hr.talent.service.ResumeLifecycleRunPersistenceService;
import com.ezaigc.hr.talent.service.ResumeLifecycleRuntimePersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeLifecycleJobManagerTest {
  private final ResumeLifecycleJobManager job = new ResumeLifecycleJobManager();
  private final ResumeLifecyclePersistenceService cycles = mock(ResumeLifecyclePersistenceService.class);
  private final ResumeLifecycleRuntimePersistenceService runtime = mock(ResumeLifecycleRuntimePersistenceService.class);
  private final ResumeLifecycleRunPersistenceService runs = mock(ResumeLifecycleRunPersistenceService.class);
  private final ResumeLifecycleApplicationManager manager = mock(ResumeLifecycleApplicationManager.class);
  private final ResumeLifecycleProperties properties = new ResumeLifecycleProperties();
  private final ResumeLifecycleRuntime state = new ResumeLifecycleRuntime();
  private final Instant now=Instant.parse("2026-09-18T17:30:00Z");

  @BeforeEach void setup() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"lifecycle-run"), ResumeLifecycleRun.class);
    ReflectionTestUtils.setField(job,"cycles",cycles);
    ReflectionTestUtils.setField(job,"runtime",runtime);
    ReflectionTestUtils.setField(job,"runs",runs);
    ReflectionTestUtils.setField(job,"manager",manager);
    ReflectionTestUtils.setField(job,"properties",properties);
    ReflectionTestUtils.setField(job,"ids",(PlatformIdGenerator)()->"id");
    ReflectionTestUtils.setField(job,"clock",Clock.fixed(now,ZoneOffset.UTC));
    properties.setEnabled(true);
    state.setActivatedAt(Instant.parse("2026-09-14T02:00:00Z"));
    when(runtime.getById("RESUME_LIFECYCLE")).thenReturn(state);
    when(runtime.claim(anyString(),any(),any())).thenReturn(1);
    when(cycles.ready(anyString(),anyInt())).thenReturn(List.of());
    when(cycles.page(any(Page.class),any(Wrapper.class))).thenReturn(page(List.of()));
  }

  @Test void disabledFeatureDoesNotCreateRuntimeOrRunRecords() {
    properties.setEnabled(false);job.execute();
    verifyNoInteractions(runtime,runs,cycles,manager);
  }

  @Test void heldLeasePreventsAnyCandidateWork() {
    when(runtime.claim(anyString(),any(),any())).thenReturn(0);job.execute();
    verifyNoInteractions(cycles,manager,runs);
  }

  @Test void partialFailureDoesNotRollbackSuccessOrMarkBoundaryComplete() {
    ResumeLifecycle one=row("1"),two=row("2"),three=row("3");
    when(cycles.page(any(Page.class),any(Wrapper.class))).thenReturn(page(List.of(one,two,three)),page(List.of()));
    when(manager.advance("1",LocalDate.of(2026,9,18),false)).thenReturn("SHARED");
    when(manager.advance("2",LocalDate.of(2026,9,18),false)).thenThrow(new IllegalStateException("failure"));
    when(manager.advance("3",LocalDate.of(2026,9,18),false)).thenReturn("BLOCKED");
    job.execute();
    ArgumentCaptor<ResumeLifecycleRun> captor=ArgumentCaptor.forClass(ResumeLifecycleRun.class);
    verify(runs,atLeastOnce()).updateById(captor.capture());
    ResumeLifecycleRun run=captor.getValue();
    assertEquals("PARTIAL",run.getStatus());assertEquals(3,run.getScannedCount());
    assertEquals(1,run.getSharedCount());assertEquals(1,run.getBlockedCount());assertEquals(1,run.getFailedCount());
    verify(runtime,never()).complete(anyString(),anyLong(),any(),any());
    verify(runtime).release("id",now);
  }

  @Test void publishedCalendarAfterSettlementRunsOnlyCorrection() {
    state.setLastSettlementDate(LocalDate.of(2026,9,18));state.setCalendarGeneration(1);state.setCompletedGeneration(0);
    when(cycles.page(any(Page.class),any(Wrapper.class))).thenReturn(page(List.of(row("1"))),page(List.of()));
    when(manager.advance("1",LocalDate.of(2026,9,18),true)).thenReturn("UPDATED");
    job.execute();
    verify(manager).advance("1",LocalDate.of(2026,9,18),true);
    verify(runtime).complete("id",1,LocalDate.of(2026,9,18),now);
  }

  @Test void completedBoundaryIsNotRepeatedWithoutCalendarChange() {
    state.setLastSettlementDate(LocalDate.of(2026,9,18));job.execute();
    verifyNoInteractions(cycles,manager);
    verify(runs).getOne(any(Wrapper.class));
    verify(runs,never()).save(any(ResumeLifecycleRun.class));
    verify(runs,never()).updateById(any(ResumeLifecycleRun.class));
    verify(runtime).release("id",now);
  }

  @Test void preOneAmRestartUsesPreviousArrivedBoundary() {
    Instant beforeOne=Instant.parse("2026-09-18T16:30:00Z");
    ReflectionTestUtils.setField(job,"clock",Clock.fixed(beforeOne,ZoneOffset.UTC));
    when(cycles.page(any(Page.class),any(Wrapper.class))).thenReturn(page(List.of(row("1"))),page(List.of()));
    when(manager.advance(anyString(),any(),eq(false))).thenReturn("UPDATED");
    job.execute();
    verify(manager).advance("1",LocalDate.of(2026,9,17),false);
  }

  @Test void legacyReadinessIsClampedToActivationButNewReadinessIsPreserved() {
    ResumeLifecycleReadyDTO old=ready("old",Instant.parse("2026-01-01T00:00:00Z"));
    ResumeLifecycleReadyDTO recent=ready("new",Instant.parse("2026-09-17T00:00:00Z"));
    when(cycles.ready(anyString(),anyInt())).thenReturn(List.of(old,recent),List.of());
    job.execute();
    verify(manager).initialize("old","old",state.getActivatedAt());
    verify(manager).initialize("new","new",recent.getEnteredAt());
  }

  @Test void losingLeaseStopsBeforeMutatingNextCycleAndReleasesOwnership() {
    when(runtime.claim(anyString(),any(),any())).thenReturn(1,0);
    when(cycles.page(any(Page.class),any(Wrapper.class))).thenReturn(page(List.of(row("1"))),page(List.of()));
    assertDoesNotThrow(job::execute);
    verifyNoInteractions(manager);
    verify(runtime).release("id",now);
    verify(runtime,never()).complete(anyString(),anyLong(),any(),any());
  }

  @Test void requestedHistoricalRetryUsesOriginalBoundaryWithoutCompletingTodaysCheckpoint() {
    ResumeLifecycleRun retry=new ResumeLifecycleRun();retry.setId("retry");
    retry.setStatus("RETRY_PENDING");retry.setSettlementDate(LocalDate.of(2026,9,16));
    retry.setFinishedAt(now.minusSeconds(3600));retry.setErrorCode("ITEM_FAILED");
    when(runs.getOne(any(Wrapper.class))).thenReturn(retry);
    when(cycles.page(any(Page.class),any(Wrapper.class))).thenReturn(page(List.of(row("1"))),page(List.of()));
    when(manager.advance("1",LocalDate.of(2026,9,16),false)).thenReturn("SUCCEEDED");
    doAnswer(invocation->{
      ResumeLifecycleRun value=invocation.getArgument(0);
      if ("RUNNING".equals(value.getStatus())) {
        assertNull(value.getFinishedAt());assertNull(value.getErrorCode());
      }
      return true;
    }).when(runs).updateById(any(ResumeLifecycleRun.class));
    job.execute();
    verify(manager).advance("1",LocalDate.of(2026,9,16),false);
    verify(runtime,never()).complete(anyString(),anyLong(),any(),any());
    assertEquals("SUCCEEDED",retry.getStatus());
  }

  @Test void mixedSuccessfulAndCalendarBlockedItemsArePartialRatherThanAllBlocked() {
    when(cycles.page(any(Page.class),any(Wrapper.class))).thenReturn(page(List.of(row("1"),row("2"))),page(List.of()));
    when(manager.advance("1",LocalDate.of(2026,9,18),false)).thenReturn("SUCCEEDED");
    when(manager.advance("2",LocalDate.of(2026,9,18),false)).thenReturn("BLOCKED");
    job.execute();
    ArgumentCaptor<ResumeLifecycleRun> captor=ArgumentCaptor.forClass(ResumeLifecycleRun.class);
    verify(runs,atLeastOnce()).updateById(captor.capture());
    assertEquals("PARTIAL",captor.getValue().getStatus());
    assertEquals("CALENDAR_MISSING",captor.getValue().getErrorCode());
  }

  @Test void infrastructureFailureRecordsInterruptionOnlyWhileLeaseStillOwned() {
    state.setLeaseOwner("id");state.setLeaseUntil(now.plusSeconds(300));
    when(cycles.ready(anyString(),anyInt())).thenThrow(new IllegalStateException("database failure"));
    assertDoesNotThrow(job::execute);
    ArgumentCaptor<ResumeLifecycleRun> captor=ArgumentCaptor.forClass(ResumeLifecycleRun.class);
    verify(runs,atLeastOnce()).updateById(captor.capture());
    assertEquals("PARTIAL",captor.getValue().getStatus());
    assertEquals("RUN_INTERRUPTED",captor.getValue().getErrorCode());
    assertNotNull(captor.getValue().getFinishedAt());
    verify(runtime,never()).complete(anyString(),anyLong(),any(),any());
  }

  @Test void retryAfterCalendarPublicationCannotUpgradeBeforeNextNormalBoundary() {
    ResumeLifecycleRun retry=new ResumeLifecycleRun();retry.setId("retry");
    retry.setStatus("RETRY_PENDING");retry.setSettlementDate(LocalDate.of(2026,9,18));
    retry.setCalendarGeneration(0);
    state.setCalendarGeneration(1);
    // 原运行部分失败，所以最新结算边界尚未标记完成，仍须拦住新日历带来的升级。
    state.setLastSettlementDate(LocalDate.of(2026,9,17));
    when(runs.getOne(any(Wrapper.class))).thenReturn(retry);
    when(cycles.page(any(Page.class),any(Wrapper.class))).thenReturn(page(List.of(row("1"))),page(List.of()));
    when(manager.advance("1",LocalDate.of(2026,9,18),true)).thenReturn("DEFERRED");
    job.execute();
    verify(manager).advance("1",LocalDate.of(2026,9,18),true);
    verify(manager,never()).advance(anyString(),any(),eq(false));
    assertEquals(0,retry.getSharedCount());
  }

  @Test void retryOfFailedCorrectionRetainsCorrectionModeEvenWhenCalendarGenerationMatches() {
    ResumeLifecycleRun retry=new ResumeLifecycleRun();retry.setId("retry");
    retry.setStatus("RETRY_PENDING");retry.setSettlementDate(LocalDate.of(2026,9,18));
    retry.setCalendarGeneration(1);
    state.setCalendarGeneration(1);state.setCompletedGeneration(1);
    state.setLastSettlementDate(LocalDate.of(2026,9,18));
    when(runs.getOne(any(Wrapper.class))).thenReturn(retry);
    when(cycles.page(any(Page.class),any(Wrapper.class))).thenReturn(page(List.of(row("1"))),page(List.of()));
    when(manager.advance("1",LocalDate.of(2026,9,18),true)).thenReturn("DEFERRED");
    job.execute();
    // 显式重试不能被已完成代次的自动跳过逻辑吞掉，也不能切换为升级模式。
    verify(manager).advance("1",LocalDate.of(2026,9,18),true);
    assertEquals(0,retry.getSharedCount());
  }

  private static Page<ResumeLifecycle> page(List<ResumeLifecycle> rows) {
    Page<ResumeLifecycle> page=new Page<>();page.setRecords(rows);return page;
  }

  private static ResumeLifecycle row(String id) {
    ResumeLifecycle row=new ResumeLifecycle();row.setId(id);return row;
  }

  private static ResumeLifecycleReadyDTO ready(String id,Instant entered) {
    ResumeLifecycleReadyDTO ready=new ResumeLifecycleReadyDTO();ready.setCandidateId(id);
    ready.setResumeVersionId(id);ready.setEnteredAt(entered);return ready;
  }
}
