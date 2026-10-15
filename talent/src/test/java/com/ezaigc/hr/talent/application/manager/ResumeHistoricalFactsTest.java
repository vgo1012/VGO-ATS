package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.entity.ResumeResourceEntity;
import com.ezaigc.hr.talent.service.EducationFactPersistenceService;
import com.ezaigc.hr.talent.service.ProjectFactPersistenceService;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import com.ezaigc.hr.talent.service.SimpleFactPersistenceService;
import com.ezaigc.hr.talent.service.SkillFactPersistenceService;
import com.ezaigc.hr.talent.service.WorkFactPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeHistoricalFactsTest {
  private static final String VERSION = "V".repeat(26);
  private static final String SNAPSHOT = "S".repeat(26);
  private final ResumeParsingApplicationManager manager = new ResumeParsingApplicationManager();
  private final ResumeParsingPersistenceService persistence = mock(ResumeParsingPersistenceService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(manager, "library", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "workFactPersistenceService", mock(WorkFactPersistenceService.class));
    ReflectionTestUtils.setField(manager, "projectFactPersistenceService", mock(ProjectFactPersistenceService.class));
    ReflectionTestUtils.setField(manager, "educationFactPersistenceService", mock(EducationFactPersistenceService.class));
    ReflectionTestUtils.setField(manager, "skillFactPersistenceService", mock(SkillFactPersistenceService.class));
    ReflectionTestUtils.setField(manager, "simpleFactPersistenceService", mock(SimpleFactPersistenceService.class));
    when(persistence.findResumeResource(VERSION)).thenReturn(new ResumeResourceEntity(
        VERSION, "synthetic.pdf", "application/pdf", "sha256", "qa/object", "C".repeat(26), "O".repeat(26), "D".repeat(26)));
  }

  @Test
  void readsFrozenSnapshotAfterCurrentFactsHaveChanged() {
    FactSnapshot old = snapshot(VERSION);
    old.setSnapshotVersion(1);
    old.setCurrentMarker(null);
    when(persistence.findFactSnapshot(SNAPSHOT)).thenReturn(old);
    var result = manager.historicalFacts(VERSION, SNAPSHOT);
    assertThat(result.getSnapshot().getId()).isEqualTo(SNAPSHOT);
    assertThat(result.getSnapshot().getSnapshotVersion()).isEqualTo(1);
    verify(persistence, never()).findCurrentFactSnapshot(any());
  }

  @Test
  void rejectsCrossResumeSnapshot() {
    when(persistence.findFactSnapshot(SNAPSHOT)).thenReturn(snapshot("X".repeat(26)));
    assertThatThrownBy(() -> manager.historicalFacts(VERSION, SNAPSHOT))
        .isInstanceOf(PlatformException.class).hasMessage("该简历版本的事实快照不存在");
  }

  @Test
  void rejectsMissingSnapshotWithoutFallingBackToCurrentFacts() {
    assertThatThrownBy(() -> manager.historicalFacts(VERSION, SNAPSHOT))
        .isInstanceOf(PlatformException.class).hasMessage("该简历版本的事实快照不存在");
    verify(persistence, never()).findCurrentFactSnapshot(any());
  }

  @Test
  void authorizesResumeBeforeReadingHistoricalSnapshot() {
    var forbidden = new PlatformException(HttpStatus.FORBIDDEN, "OBJECT_SCOPE_FORBIDDEN", "无权访问", false);
    doThrow(forbidden).when(authorization).requireAccess(eq("talent.read"), any());
    assertThatThrownBy(() -> manager.historicalFacts(VERSION, SNAPSHOT)).isSameAs(forbidden);
    verify(persistence, never()).findFactSnapshot(any());
  }

  private static FactSnapshot snapshot(String version) {
    FactSnapshot value = new FactSnapshot();
    value.setId(SNAPSHOT);
    value.setResumeVersionId(version);
    return value;
  }
}
