package com.ezaigc.hr.talent.application.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ezaigc.hr.talent.domain.dto.WorkCalendarCalculationDTO;
import com.ezaigc.hr.talent.config.ResumeLifecycleProperties;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycle;
import com.ezaigc.hr.talent.service.ResumeLifecyclePersistenceService;
import com.ezaigc.hr.talent.service.ResumeLifecycleRuntimePersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeLifecycleCalendarImpactManagerTest {
  private final ResumeLifecycleCalendarImpactManager manager = new ResumeLifecycleCalendarImpactManager();
  private final ResumeLifecyclePersistenceService cycles = mock(ResumeLifecyclePersistenceService.class);
  private final ResumeLifecycleRuntimePersistenceService runtime = mock(ResumeLifecycleRuntimePersistenceService.class);
  private final WorkCalendarCalculationManager calendar = mock(WorkCalendarCalculationManager.class);
  private final ResumeLifecycleProperties properties = new ResumeLifecycleProperties();

  @BeforeEach void setup() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "calendar-impact"),
        ResumeLifecycle.class);
    ReflectionTestUtils.setField(manager,"cycles",cycles);
    ReflectionTestUtils.setField(manager,"runtime",runtime);
    ReflectionTestUtils.setField(manager,"calendar",calendar);
    ReflectionTestUtils.setField(manager,"properties",properties);
    properties.setEnabled(true);
    ReflectionTestUtils.setField(manager,"clock",Clock.fixed(Instant.parse("2026-09-18T17:30:00Z"),ZoneOffset.UTC));
  }

  @Test void previewCountsUpgradesDowngradesSharingAndMissingCalendarSeparately() {
    ResumeLifecycle first=row("1","PENDING_SCREENING","NORMAL");
    ResumeLifecycle second=row("2","PENDING_DISPOSAL","RED");
    ResumeLifecycle third=row("3","PENDING_DISPOSAL","YELLOW");
    when(cycles.list(any(Wrapper.class))).thenReturn(List.of(first,second,third),List.of());
    when(calendar.calculateWithOverride(any(),any(),eq(false),eq(2026),eq("draft")))
        .thenReturn(calculation(4,true,List.of()),calculation(0,true,List.of()),calculation(2,false,List.of(2027)));
    var result=manager.preview(2026,"draft");
    assertEquals(1,result.getUpgradeCount());assertEquals(1,result.getDowngradeCount());assertEquals(1,result.getShareCount());
    assertEquals(List.of(2027),result.getMissingYears());
    verify(calendar,times(3)).calculateWithOverride(any(),eq(LocalDate.of(2026,9,18)),eq(false),eq(2026),eq("draft"));
    verifyNoInteractions(runtime);
    verify(cycles,never()).updateById(any(ResumeLifecycle.class));
  }

  @Test void changedCycleVersionInvalidatesImpactFingerprint() {
    ResumeLifecycle row=row("1","PENDING_SCREENING","NORMAL");
    when(cycles.list(any(Wrapper.class))).thenReturn(List.of(row),List.of(),List.of(row),List.of());
    when(calendar.calculateWithOverride(any(),any(),eq(false),eq(2026),eq("draft"))).thenReturn(calculation(1,true,List.of()));
    String first=manager.preview(2026,"draft").getFingerprint();
    row.setVersion(1);
    assertNotEquals(first,manager.preview(2026,"draft").getFingerprint());
  }

  @Test void publicationOnlyMarksDurableRecalculationAndDoesNotActivateOrLockResumes() {
    assertEquals("calendar:2026:draft",manager.published(2026,"draft","fingerprint"));
    verify(runtime).ensure(null);
    verify(runtime).calendarChanged();
    verify(runtime,never()).activate(any());
    verifyNoInteractions(cycles,calendar);
  }

  @Test void disabledFeatureLeavesCalendarsUsableWithoutCalculatingOrQueuingResumeChanges() {
    properties.setEnabled(false);
    var result=manager.preview(2026,"draft");
    assertEquals(0,result.getUpgradeCount());assertEquals(0,result.getDowngradeCount());
    assertEquals(0,result.getShareCount());assertEquals(List.of(),result.getMissingYears());
    assertEquals(result.getFingerprint(),manager.preview(2026,"draft").getFingerprint());
    assertEquals("calendar:2026:draft",manager.published(2026,"draft",result.getFingerprint()));
    verifyNoInteractions(cycles,runtime,calendar);
  }

  private static ResumeLifecycle row(String id,String phase,String warning) {
    ResumeLifecycle row=new ResumeLifecycle();row.setId(id);row.setPhase(phase);row.setWarningLevel(warning);
    row.setEnteredAt(Instant.parse("2026-09-14T02:00:00Z"));return row;
  }

  private static WorkCalendarCalculationDTO calculation(int days,boolean complete,List<Integer> missing) {
    return new WorkCalendarCalculationDTO(days,Map.of(2026,"draft"),missing,List.of(),complete);
  }
}
