package com.ezaigc.hr.talent.application.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.application.support.WorkCalendarLifecycleImpactSupport;
import com.ezaigc.hr.talent.domain.dto.WorkCalendarImpactDTO;
import com.ezaigc.hr.talent.domain.response.WorkCalendarRevisionResponse;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class WorkCalendarApplicationManagerTest {
  private final WorkCalendarApplicationManager manager = new WorkCalendarApplicationManager();
  private final AuthorizationService auth = mock(AuthorizationService.class);
  private final WorkCalendarPersistenceService persistence = mock(WorkCalendarPersistenceService.class);
  private final WorkCalendarLifecycleImpactSupport lifecycle = mock(WorkCalendarLifecycleImpactSupport.class);
  private final TalentIdempotencyPersistenceService idempotencies = mock(TalentIdempotencyPersistenceService.class);
  private final TalentIntakePersistenceService intake = mock(TalentIntakePersistenceService.class);
  private final TalentProcessEventPersistenceService events = mock(TalentProcessEventPersistenceService.class);
  private final WorkCalendarYearEntity pointer = new WorkCalendarYearEntity();
  private final WorkCalendarRevisionEntity draft = new WorkCalendarRevisionEntity();
  private CurrentActorSnapshot actor;

  @BeforeEach void setup() {
    actor = new CurrentActorSnapshot();
    actor.setUserId("admin"); actor.setDisplayName("管理员"); actor.setSuperAdmin(true);
    when(auth.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager, "authorization", auth);
    ReflectionTestUtils.setField(manager, "calendars", persistence);
    ReflectionTestUtils.setField(manager, "lifecycle", lifecycle);
    ReflectionTestUtils.setField(manager, "idempotencies", idempotencies);
    ReflectionTestUtils.setField(manager, "intake", intake);
    ReflectionTestUtils.setField(manager, "events", events);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(Instant.parse("2026-09-14T02:00:00Z"), ZoneOffset.UTC));
    AtomicInteger sequence = new AtomicInteger();
    ReflectionTestUtils.setField(manager, "ids", (PlatformIdGenerator) () -> "id" + sequence.incrementAndGet());
    ReflectionTestUtils.setField(manager, "json", new ObjectMapper());
    pointer.setCalendarYear(2026);
    when(persistence.lockYear(2026)).thenReturn(pointer);
    draft.setId("draft");draft.setCalendarYear(2026);draft.setRevision(1);draft.setStatus("DRAFT");
    when(persistence.getById("draft")).thenReturn(draft);
    when(persistence.days("draft")).thenReturn(WorkCalendarCalculationManagerTest.template(2026));
    when(intake.insertIdempotency(anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(1);
    when(lifecycle.preview(2026,"draft")).thenReturn(new WorkCalendarImpactDTO("stable", 2, 1, 1, List.of()));
    when(persistence.updateById(any(WorkCalendarRevisionEntity.class))).thenReturn(true);
    doAnswer(invocation -> {
      draft.setPreviewId(invocation.getArgument(1));
      draft.setPreviewFingerprint(invocation.getArgument(2));
      draft.setPreviewPublishedId(invocation.getArgument(3));
      draft.setPreviewExpiresAt(invocation.getArgument(4));
      return 1;
    }).when(persistence).setPreview(anyString(), anyString(), anyString(), nullable(String.class), any());
  }

  @Test void ordinaryUserReadsOnlyPublishedDays() {
    actor.setSuperAdmin(false);
    pointer.setPublishedRevisionId("published");
    when(persistence.findYear(2026)).thenReturn(pointer);
    WorkCalendarRevisionEntity published = new WorkCalendarRevisionEntity();
    published.setId("published"); published.setCalendarYear(2026); published.setStatus("PUBLISHED");
    when(persistence.getById("published")).thenReturn(published);
    when(persistence.days("published")).thenReturn(WorkCalendarCalculationManagerTest.template(2026));
    var result = manager.published(2026);
    assertEquals("published", result.getRevisionId());
    assertEquals(365, result.getDays().size());
    verify(persistence, never()).revisions(anyInt());
    verify(persistence, never()).days("draft");
  }

  @Test void unpublishedCalendarIsExplicitlyEmptyAndNeverReadsDrafts() {
    actor.setSuperAdmin(false);
    when(persistence.findYear(2026)).thenReturn(pointer);
    var result = manager.published(2026);
    assertNull(result.getRevisionId());
    assertTrue(result.getDays().isEmpty());
    verify(persistence, never()).revisions(anyInt());
    verify(persistence, never()).days(anyString());
  }

  @Test void readRejectsDraftPointerAndInvalidYear() {
    pointer.setPublishedRevisionId("draft");
    when(persistence.findYear(2026)).thenReturn(pointer);
    assertThrows(RuntimeException.class, () -> manager.published(2026));
    assertThrows(RuntimeException.class, () -> manager.published(1999));
    verify(persistence, never()).findYear(1999);
    verify(persistence, never()).days(anyString());
  }

  @Test void unauthenticatedReadFailsBeforeAccessingCalendar() {
    when(auth.currentActor()).thenThrow(new IllegalStateException("unauthenticated"));
    assertThrows(IllegalStateException.class, () -> manager.published(2026));
    verify(persistence, never()).findYear(anyInt());
  }

  @Test void allAdminEndpointsRejectOrdinaryAndLeadershipAccountsBeforeReadingData() {
    actor.setSuperAdmin(false);
    assertThrows(RuntimeException.class, () -> manager.year(2026));
    assertThrows(RuntimeException.class, () -> manager.createDraft(2026,"create"));
    assertThrows(RuntimeException.class, () -> manager.updateDays(2026,"draft","0",List.of(LocalDate.of(2026,1,1)),"RESTDAY",null));
    assertThrows(RuntimeException.class, () -> manager.preview(2026,"draft","0"));
    assertThrows(RuntimeException.class, () -> publish("publish"));
    assertThrows(RuntimeException.class, () -> manager.history(2026));
    verify(persistence, never()).findYear(anyInt());
    verify(persistence, never()).ensureYear(anyInt());
  }

  @Test void creatingLeapYearDraftGeneratesCompleteUnpublishedTemplate() {
    WorkCalendarYearEntity leap = new WorkCalendarYearEntity();leap.setCalendarYear(2028);
    when(persistence.lockYear(2028)).thenReturn(leap);
    when(persistence.revisions(2028)).thenReturn(List.of());
    var result = manager.createDraft(2028,"create");
    ArgumentCaptor<List<WorkCalendarDayEntity>> captor = ArgumentCaptor.captor();
    verify(persistence).insertDays(captor.capture());
    assertEquals(366,captor.getValue().size());
    assertEquals("DRAFT",result.getStatus());
    verify(persistence,never()).publishPointer(anyInt(),anyString());
    verifyNoInteractions(lifecycle);
  }

  @Test void duplicateAndOutOfYearDatesAndStaleVersionAreRejected() {
    LocalDate date = LocalDate.of(2026,1,1);
    assertThrows(RuntimeException.class, () -> manager.updateDays(2026,"draft","0",List.of(date,date),"RESTDAY",null));
    assertThrows(RuntimeException.class, () -> manager.updateDays(2026,"draft","0",List.of(date.minusDays(1)),"RESTDAY",null));
    assertThrows(RuntimeException.class, () -> manager.updateDays(2026,"draft","1",List.of(date),"RESTDAY",null));
    verify(persistence,never()).updateDays(anyString(),anyList(),anyString(),any());
  }

  @Test void holidaysCanBeSavedWithoutPublishingAndInvalidatePreview() {
    List<LocalDate> dates = List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));
    when(persistence.updateDays("draft", dates, "HOLIDAY", "国庆节")).thenReturn(2);
    var result = manager.updateDays(2026, "draft", "0", dates, "HOLIDAY", "国庆节");
    assertEquals("DRAFT", result.getStatus());
    verify(persistence).updateDays("draft", dates, "HOLIDAY", "国庆节");
    verify(persistence).clearPreview("draft");
    verify(events).save(any());
    verify(persistence, never()).publishPointer(anyInt(), anyString());
  }

  @Test void invalidDayTypeCannotBeSaved() {
    assertThrows(RuntimeException.class, () -> manager.updateDays(2026, "draft", "0",
        List.of(LocalDate.of(2026, 1, 1)), "UNKNOWN", null));
    verify(persistence, never()).updateDays(anyString(), anyList(), anyString(), any());
  }

  @Test void publishedDatesCannotBeEdited() {
    draft.setStatus("PUBLISHED");
    assertThrows(RuntimeException.class, () -> manager.updateDays(2026,"draft","0",List.of(LocalDate.of(2026,1,1)),"RESTDAY",null));
    verify(persistence,never()).updateDays(anyString(),anyList(),anyString(),any());
  }

  @Test void businessChangesInvalidatePreviewAndDoNotSwitchPublishedPointer() {
    manager.preview(2026,"draft","0");
    when(lifecycle.preview(2026,"draft")).thenReturn(new WorkCalendarImpactDTO("changed",3,1,2,List.of()));
    assertThrows(RuntimeException.class, () -> publish("publish"));
    verify(persistence,never()).publishPointer(anyInt(),anyString());
    verify(lifecycle,never()).published(anyInt(),anyString(),anyString());
  }

  @Test void successfulPublishRequiresConfirmationAndEnqueuesRecalculationWithoutModifyingDays() {
    var preview = manager.preview(2026,"draft","0");
    when(lifecycle.published(eq(2026),eq("draft"),anyString())).thenReturn("calendar-1");
    var result = publish("publish");
    assertEquals("PUBLISHED",result.getStatus());
    assertEquals("calendar-1",result.getRecalculationId());
    assertEquals("管理员",result.getPublishedName());
    assertEquals("发布",result.getPublishReason());
    assertTrue(preview.isSharedIrreversible());
    verify(persistence).publishPointer(2026,"draft");
    verify(persistence,never()).updateDays(anyString(),anyList(),anyString(),any());
    verify(events).save(any());
  }

  @Test void replayedPublicationReturnsOriginalVersionWithoutAnotherCallback() {
    draft.setStatus("PUBLISHED");
    IdempotencyRecord existing = new IdempotencyRecord();
    existing.setActionCode("WORK_CALENDAR_PUBLISH");existing.setTargetId("draft");
    existing.setRequestFingerprint(Sha256Utils.fingerprintObjects(31,2026,"draft","0",null,null,"官方通知","发布",true));
    when(idempotencies.findIdempotency("admin","publish")).thenReturn(existing);
    assertEquals("PUBLISHED",publish("publish").getStatus());
    verifyNoInteractions(lifecycle);
    verify(persistence,never()).publishPointer(anyInt(),anyString());
  }

  @Test void expiredPreviewCannotPublish() {
    manager.preview(2026,"draft","0");
    draft.setPreviewExpiresAt(Instant.parse("2026-09-14T01:59:59Z"));
    assertThrows(RuntimeException.class, () -> publish("publish"));
    verify(persistence,never()).publishPointer(anyInt(),anyString());
  }

  private WorkCalendarRevisionResponse publish(String key) {
    return manager.publish(2026,"draft","0",draft.getPreviewId(),null,"官方通知","发布",true,key);
  }
}
