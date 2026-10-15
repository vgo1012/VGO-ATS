package com.ezaigc.hr.talent.application.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ezaigc.hr.talent.domain.dto.WorkCalendarCalculationDTO;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarDayEntity;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarRevisionEntity;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarYearEntity;
import com.ezaigc.hr.talent.service.WorkCalendarPersistenceService;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class WorkCalendarCalculationManagerTest {
  private final WorkCalendarPersistenceService persistence = mock(WorkCalendarPersistenceService.class);
  private final WorkCalendarCalculationManager manager = new WorkCalendarCalculationManager();

  @BeforeEach void setup() {
    ReflectionTestUtils.setField(manager, "calendars", persistence);
  }

  @Test void excludesEntryDayAndSettlesFridayOnSaturday() {
    calendar(2026);
    WorkCalendarCalculationDTO result = manager.calculate(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18), false);
    assertEquals(4, result.getElapsedWorkdays());
    assertEquals(List.of(LocalDate.of(2026,9,15), LocalDate.of(2026,9,16),
        LocalDate.of(2026,9,17), LocalDate.of(2026,9,18)), result.getThresholdDates());
    assertTrue(result.isSettlementComplete());
    assertTrue(result.getMissingYears().isEmpty());
  }

  @Test void holidayAndWeekendMakeupAreExplicitOverrides() {
    List<WorkCalendarDayEntity> days = calendar(2026);
    days.stream().filter(day -> !day.getCalendarDate().isBefore(LocalDate.of(2026,9,15))
        && !day.getCalendarDate().isAfter(LocalDate.of(2026,9,18)))
        .forEach(day -> day.setDayType("HOLIDAY"));
    days.stream().filter(day -> day.getCalendarDate().equals(LocalDate.of(2026,9,20)))
        .forEach(day -> day.setDayType("WORKDAY"));
    WorkCalendarCalculationDTO result = manager.calculate(LocalDate.of(2026,9,14), LocalDate.of(2026,9,20), false);
    assertEquals(1, result.getElapsedWorkdays());
    assertEquals(LocalDate.of(2026,9,20), result.getThresholdDates().getFirst());
  }

  @Test void sameDayCannotAdvanceEvenWhenItIsAWorkday() {
    calendar(2026);
    assertEquals(0, manager.calculate(LocalDate.of(2026,9,14), LocalDate.of(2026,9,14), false).getElapsedWorkdays());
  }

  @Test void missingFutureCalendarDoesNotBlockKnownSettlement() {
    calendar(2026);
    WorkCalendarCalculationDTO result = manager.calculate(LocalDate.of(2026,12,30), LocalDate.of(2026,12,31), false);
    assertEquals(1, result.getElapsedWorkdays());
    assertTrue(result.isSettlementComplete());
    assertEquals(List.of(2027), result.getMissingYears());
    assertEquals(List.of(LocalDate.of(2026,12,31)), result.getThresholdDates());
  }

  @Test void missingElapsedYearFreezesWholeSettlementAndDoesNotInventThresholds() {
    calendar(2026);
    calendar(2028);
    WorkCalendarCalculationDTO result = manager.calculate(LocalDate.of(2026,12,31), LocalDate.of(2028,1,3), false);
    assertFalse(result.isSettlementComplete());
    assertTrue(result.getMissingYears().contains(2027));
    assertTrue(result.getThresholdDates().isEmpty());
  }

  @Test void leapYearRequiresAll366DistinctDates() {
    List<WorkCalendarDayEntity> days = template(2028);
    assertEquals(366, days.size());
    assertDoesNotThrow(() -> WorkCalendarCalculationManager.validateCoverage(2028, days));
    days.removeLast();
    assertThrows(RuntimeException.class, () -> WorkCalendarCalculationManager.validateCoverage(2028, days));
  }

  @Test void locksRequireAnExistingBusinessTransactionAndFollowYearOrder() {
    assertThrows(IllegalStateException.class, () -> manager.calculate(LocalDate.of(2026,12,31), LocalDate.of(2027,1,4), true));
    WorkCalendarYearEntity first = pointer(2026);
    WorkCalendarYearEntity second = pointer(2027);
    when(persistence.lockYear(2026)).thenReturn(first);
    when(persistence.lockYear(2027)).thenReturn(second);
    published(2026, Instant.parse("2025-12-01T00:00:00Z"));
    published(2027, Instant.parse("2026-12-01T00:00:00Z"));
    when(persistence.days("r2026")).thenReturn(template(2026));
    when(persistence.days("r2027")).thenReturn(template(2027));
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      manager.calculate(LocalDate.of(2026,12,30), LocalDate.of(2027,1,4), true);
      var order = inOrder(persistence);
      order.verify(persistence).ensureYear(2026);
      order.verify(persistence).lockYear(2026);
      order.verify(persistence).getById("r2026");
      order.verify(persistence).days("r2026");
      order.verify(persistence).ensureYear(2027);
      order.verify(persistence).lockYear(2027);
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
  }

  @Test void previewOverrideDoesNotChangePublishedPointer() {
    calendar(2026);
    List<WorkCalendarDayEntity> draft = template(2026);
    draft.forEach(day -> day.setDayType("RESTDAY"));
    when(persistence.days("draft")).thenReturn(draft);
    WorkCalendarCalculationDTO result = manager.calculateWithOverride(
        LocalDate.of(2026,9,14), LocalDate.of(2026,9,18), false, 2026, "draft");
    assertEquals(0, result.getElapsedWorkdays());
    assertEquals("draft", result.getRevisionVector().get(2026));
    verify(persistence, never()).publishPointer(anyInt(), anyString());
  }

  @Test void settledRevisionTimestampIsReadAfterPointerLockAndLatestAcrossYearsWins() {
    calendar(2026);calendar(2027);
    when(persistence.lockYear(2026)).thenReturn(pointer(2026));
    when(persistence.lockYear(2027)).thenReturn(pointer(2027));
    Instant recent=Instant.parse("2027-01-04T02:00:00Z");
    published(2026,Instant.parse("2026-12-30T00:00:00Z"));published(2027,recent);
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      var result=manager.calculate(LocalDate.of(2026,12,30),LocalDate.of(2027,1,4),true);
      assertEquals(recent,result.getLatestPublishedAt());
      var order=inOrder(persistence);
      order.verify(persistence).lockYear(2026);
      order.verify(persistence).getById("r2026");
      order.verify(persistence).lockYear(2027);
      order.verify(persistence).getById("r2027");
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
  }

  @Test void publicationOfFutureOnlyYearDoesNotPostponeKnownCurrentYearSettlement() {
    calendar(2026);calendar(2027);
    Instant currentPublished=Instant.parse("2025-12-01T00:00:00Z");
    published(2026,currentPublished);
    published(2027,Instant.parse("2027-01-01T02:00:00Z"));
    var result=manager.calculate(LocalDate.of(2026,12,30),LocalDate.of(2026,12,31),false);
    assertEquals(1,result.getElapsedWorkdays());
    assertEquals(4,result.getThresholdDates().size());
    assertEquals(currentPublished,result.getLatestPublishedAt());
    verify(persistence,never()).getById("r2027");
  }

  @Test void publishedPointerWithoutPublicationMetadataFailsClosed() {
    calendar(2026);
    when(persistence.getById("r2026")).thenReturn(null);
    assertThrows(RuntimeException.class,()->manager.calculate(
        LocalDate.of(2026,9,14),LocalDate.of(2026,9,18),false));
  }

  private void published(int year,Instant at) {
    WorkCalendarRevisionEntity revision=new WorkCalendarRevisionEntity();
    revision.setId("r"+year);revision.setCalendarYear(year);revision.setStatus("PUBLISHED");
    revision.setPublishedAt(at);when(persistence.getById("r"+year)).thenReturn(revision);
  }

  private List<WorkCalendarDayEntity> calendar(int year) {
    when(persistence.findYear(year)).thenReturn(pointer(year));
    published(year, Instant.parse("2025-12-01T00:00:00Z"));
    List<WorkCalendarDayEntity> days = template(year);
    when(persistence.days("r" + year)).thenReturn(days);
    return days;
  }

  static WorkCalendarYearEntity pointer(int year) {
    WorkCalendarYearEntity pointer = new WorkCalendarYearEntity();
    pointer.setCalendarYear(year);
    pointer.setPublishedRevisionId("r" + year);
    return pointer;
  }

  static List<WorkCalendarDayEntity> template(int year) {
    List<WorkCalendarDayEntity> days = new ArrayList<>();
    LocalDate date = LocalDate.of(year,1,1);
    while (date.getYear() == year) {
      WorkCalendarDayEntity day = new WorkCalendarDayEntity();
      day.setCalendarDate(date);
      day.setDayType(date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY ? "RESTDAY" : "WORKDAY");
      days.add(day);
      date = date.plusDays(1);
    }
    return days;
  }
}
