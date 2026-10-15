package com.ezaigc.hr.talent.application.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.application.support.WorkCalendarLifecycleImpactSupport;
import com.ezaigc.hr.talent.domain.dto.WorkCalendarDayDTO;
import com.ezaigc.hr.talent.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarDayEntity;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarRevisionEntity;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarYearEntity;
import com.ezaigc.hr.talent.service.TalentIdempotencyPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import com.ezaigc.hr.talent.service.WorkCalendarPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class WorkCalendarSaveManagerTest {
  private final WorkCalendarApplicationManager manager = new WorkCalendarApplicationManager();
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final WorkCalendarPersistenceService calendars = mock(WorkCalendarPersistenceService.class);
  private final WorkCalendarLifecycleImpactSupport lifecycle = mock(WorkCalendarLifecycleImpactSupport.class);
  private final TalentIdempotencyPersistenceService idempotencies = mock(TalentIdempotencyPersistenceService.class);
  private final TalentIntakePersistenceService intake = mock(TalentIntakePersistenceService.class);
  private final TalentProcessEventPersistenceService events = mock(TalentProcessEventPersistenceService.class);
  private final WorkCalendarYearEntity pointer = new WorkCalendarYearEntity();
  private final CurrentActorSnapshot actor = new CurrentActorSnapshot();
  private final Map<String, WorkCalendarRevisionEntity> revisions = new HashMap<>();
  private final Map<String, List<WorkCalendarDayEntity>> dayRows = new HashMap<>();
  private final Map<String, IdempotencyRecord> keys = new HashMap<>();

  @BeforeEach void setup() {
    actor.setUserId("admin"); actor.setDisplayName("管理员"); actor.setSuperAdmin(true);
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "calendars", calendars);
    ReflectionTestUtils.setField(manager, "lifecycle", lifecycle);
    ReflectionTestUtils.setField(manager, "idempotencies", idempotencies);
    ReflectionTestUtils.setField(manager, "intake", intake);
    ReflectionTestUtils.setField(manager, "events", events);
    ReflectionTestUtils.setField(manager, "json", new ObjectMapper());
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(Instant.parse("2026-09-15T02:00:00Z"), ZoneOffset.UTC));
    AtomicInteger sequence = new AtomicInteger();
    ReflectionTestUtils.setField(manager, "ids", (PlatformIdGenerator) () -> "id" + sequence.incrementAndGet());
    when(calendars.lockYear(anyInt())).thenReturn(pointer);
    when(calendars.findYear(anyInt())).thenReturn(pointer);
    when(calendars.revisions(anyInt())).thenAnswer(call -> new ArrayList<>(revisions.values()));
    when(calendars.getById(anyString())).thenAnswer(call -> revisions.get(call.getArgument(0)));
    when(calendars.days(anyString())).thenAnswer(call -> dayRows.getOrDefault(call.getArgument(0), List.of()));
    when(calendars.save(any(WorkCalendarRevisionEntity.class))).thenAnswer(call -> {
      WorkCalendarRevisionEntity value = call.getArgument(0); revisions.put(value.getId(), value); return true;
    });
    when(calendars.insertDays(anyList())).thenAnswer(call -> {
      List<WorkCalendarDayEntity> values = call.getArgument(0);
      dayRows.put(values.getFirst().getCalendarRevisionId(), values); return values.size();
    });
    when(calendars.publishPointer(anyInt(), anyString())).thenAnswer(call -> {
      pointer.setPublishedRevisionId(call.getArgument(1)); return 1;
    });
    when(calendars.updateById(any(WorkCalendarRevisionEntity.class))).thenReturn(true);
    when(lifecycle.published(anyInt(), anyString(), anyString())).thenReturn("recalculation");
    when(idempotencies.findIdempotency(eq("admin"), anyString())).thenAnswer(call -> keys.get(call.getArgument(1)));
    when(intake.insertIdempotency(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenAnswer(call -> {
          IdempotencyRecord record = new IdempotencyRecord();
          record.setActionCode(call.getArgument(2)); record.setRequestFingerprint(call.getArgument(3));
          record.setTargetId(call.getArgument(4)); keys.put(call.getArgument(1), record); return 1;
        });
  }

  @Test void firstSavePublishesEntireYearAndEnqueuesRecalculationWithoutPreview() {
    var result = manager.save(2026, null, null, days(2026), "官方通知", "初始设置", "first");
    assertEquals("PUBLISHED", result.getStatus());
    assertEquals(365, result.getDays().size());
    assertEquals("recalculation", result.getRecalculationId());
    assertEquals("管理员", result.getPublishedName());
    assertEquals(result.getId(), pointer.getPublishedRevisionId());
    verify(calendars).lockYear(2026);
    verify(lifecycle).published(eq(2026), eq(result.getId()), anyString());
    verify(lifecycle, never()).preview(anyInt(), anyString());
    verify(events).save(argThat(event -> "WORK_CALENDAR_PUBLISHED".equals(event.getEventType())
        && "初始设置".equals(event.getReason())));
  }

  @Test void editCreatesNewPublishedRevisionAndPreservesPreviousDays() {
    var first = manager.save(2026, null, null, days(2026), "通知", "首次", "first");
    List<WorkCalendarDayDTO> changed = days(2026);
    changed.getFirst().setDayType("HOLIDAY"); changed.getFirst().setDescription("元旦");
    var second = manager.save(2026, first.getId(), first.getVersion(), changed, "新通知", "调整", "second");
    assertNotEquals(first.getId(), second.getId());
    assertEquals(2, second.getRevision());
    assertEquals("SUPERSEDED", revisions.get(first.getId()).getStatus());
    assertEquals("WORKDAY", dayRows.get(first.getId()).getFirst().getDayType());
    assertEquals("HOLIDAY", second.getDays().getFirst().getDayType());
    verify(calendars, never()).updateDays(anyString(), anyList(), anyString(), any());
  }

  @Test void replayReturnsOriginalRevisionAndDoesNotRecalculateOrAuditTwice() {
    var first = manager.save(2026, null, null, days(2026), "通知", "首次", "first");
    var replay = manager.save(2026, null, null, days(2026), "通知", "首次", "first");
    assertEquals(first.getId(), replay.getId());
    verify(calendars, times(1)).save(any(WorkCalendarRevisionEntity.class));
    verify(lifecycle, times(1)).published(anyInt(), anyString(), anyString());
    verify(events, times(1)).save(any());
    assertEquals("IDEMPOTENCY_KEY_REUSED", assertThrows(PlatformException.class,
        () -> manager.save(2026, null, null, days(2026), "另一通知", "首次", "first")).code());
  }

  @Test void staleBaselineCannotOverwriteAnotherEditorsSave() {
    var first = manager.save(2026, null, null, days(2026), "通知", "首次", "first");
    assertEquals("WORK_CALENDAR_VERSION_CONFLICT", assertThrows(PlatformException.class,
        () -> manager.save(2026, null, null, days(2026), "通知", "并发首次", "stale-create")).code());
    assertEquals("WORK_CALENDAR_VERSION_CONFLICT", assertThrows(PlatformException.class,
        () -> manager.save(2026, first.getId(), "999", days(2026), "通知", "旧版本", "stale-version")).code());
    var second = manager.save(2026, first.getId(), first.getVersion(), days(2026), "通知", "更新", "second");
    assertEquals("WORK_CALENDAR_VERSION_CONFLICT", assertThrows(PlatformException.class,
        () -> manager.save(2026, first.getId(), first.getVersion(), days(2026), "通知", "并发更新", "stale-edit")).code());
    assertEquals(second.getId(), pointer.getPublishedRevisionId());
    verify(lifecycle, times(2)).published(anyInt(), anyString(), anyString());
  }

  @Test void invalidCoverageTypesDescriptionsAndMetadataNeverPublish() {
    List<WorkCalendarDayDTO> missing = days(2026); missing.removeLast();
    List<WorkCalendarDayDTO> duplicate = days(2026); duplicate.set(1, duplicate.getFirst());
    List<WorkCalendarDayDTO> outside = days(2026); outside.getFirst().setDate(LocalDate.of(2025, 1, 1));
    List<WorkCalendarDayDTO> invalidType = days(2026); invalidType.getFirst().setDayType("OTHER");
    List<WorkCalendarDayDTO> longDescription = days(2026); longDescription.getFirst().setDescription("x".repeat(201));
    for (var invalid : List.of(missing, outside, invalidType, longDescription)) {
      assertEquals("WORK_CALENDAR_DAYS_INVALID", assertThrows(PlatformException.class,
          () -> manager.save(2026, null, null, invalid, "通知", "设置", "invalid")).code());
    }
    assertEquals("WORK_CALENDAR_INCOMPLETE", assertThrows(PlatformException.class,
        () -> manager.save(2026, null, null, duplicate, "通知", "设置", "duplicate")).code());
    assertEquals("WORK_CALENDAR_CONFIRMATION_REQUIRED", assertThrows(PlatformException.class,
        () -> manager.save(2026, null, null, days(2026), " ", "设置", "blank")).code());
    assertEquals("WORK_CALENDAR_CONFIRMATION_REQUIRED", assertThrows(PlatformException.class,
        () -> manager.save(2026, null, null, days(2026), "通知", " ", "blank")).code());
    assertEquals("IDEMPOTENCY_KEY_INVALID", assertThrows(PlatformException.class,
        () -> manager.save(2026, null, null, days(2026), "通知", "设置", null)).code());
    verify(calendars, never()).save(any(WorkCalendarRevisionEntity.class));
    verifyNoInteractions(lifecycle, events);
  }

  @Test void leapYearIsCompleteAndLegacyDraftRemainsUnpublishedAndHiddenFromHistory() {
    WorkCalendarRevisionEntity draft = new WorkCalendarRevisionEntity();
    draft.setId("legacy"); draft.setCalendarYear(2028); draft.setRevision(1); draft.setStatus("DRAFT");
    revisions.put("legacy", draft);
    var saved = manager.save(2028, null, null, days(2028), "通知", "设置", "leap");
    assertEquals(366, saved.getDays().size());
    assertEquals(2, saved.getRevision());
    assertEquals("DRAFT", draft.getStatus());
    assertEquals(1, manager.history(2028).size());
    assertEquals(saved.getId(), manager.history(2028).getFirst().getId());
  }

  @Test void ordinaryAccountCannotSave() {
    actor.setSuperAdmin(false);
    assertEquals("SUPER_ADMIN_REQUIRED", assertThrows(PlatformException.class,
        () -> manager.save(2026, null, null, days(2026), "通知", "设置", "denied")).code());
    verifyNoInteractions(calendars, lifecycle, events, intake, idempotencies);
  }

  private List<WorkCalendarDayDTO> days(int year) {
    return new ArrayList<>(LocalDate.of(year, 1, 1).datesUntil(LocalDate.of(year + 1, 1, 1))
        .map(date -> new WorkCalendarDayDTO(date, "WORKDAY", null)).toList());
  }
}
