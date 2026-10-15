package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.Resume;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.ResumePersistenceService;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeVersionReferenceApplicationManagerTest {
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final ResumeVersionPersistenceService versions = mock(ResumeVersionPersistenceService.class);
  private final ResumePersistenceService resumes = mock(ResumePersistenceService.class);
  private final CandidatePersistenceService candidates = mock(CandidatePersistenceService.class);
  private final CandidateReadAccess access = mock(CandidateReadAccess.class);
  private final ResumeVersionReferenceApplicationManager manager = new ResumeVersionReferenceApplicationManager();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "versions", versions);
    var readAccess = new ResumeVersionReadAccess();
    ReflectionTestUtils.setField(readAccess, "resumes", resumes);
    ReflectionTestUtils.setField(readAccess, "candidates", candidates);
    ReflectionTestUtils.setField(readAccess, "access", access);
    ReflectionTestUtils.setField(manager, "readAccess", readAccess);
  }

  @Test
  void historicalApplicationGrantUsesActualCandidateAndNeverExpandsToOtherVersions() {
    var old = version(1, 1, "AVAILABLE");
    var current = version(2, 1, "AVAILABLE");
    var other = version(3, 2, "AVAILABLE");
    var deletedPerson = version(4, 3, "AVAILABLE");
    var unavailable = version(5, 1, "DELETED");
    var orphan = version(6, 4, "AVAILABLE");
    when(versions.listByIds(any())).thenReturn(List.of(current, old, other, deletedPerson, unavailable, orphan));
    when(resumes.listByIds(any())).thenReturn(List.of(resume(1, 1), resume(2, 2), resume(3, 3)));
    var first = candidate(1, "ACTIVE");
    var second = candidate(2, "ACTIVE");
    when(candidates.listByIds(any())).thenReturn(List.of(first, second, candidate(3, "DELETED")));
    when(access.evaluate(any())).thenReturn(new CandidateReadDecision(Set.of(id("C", 1)), Set.of(),
        Map.of(id("C", 1), Set.of(old.getId(), other.getId()))));
    var requested = List.of(other.getId(), old.getId(), current.getId(), old.getId(), deletedPerson.getId(), unavailable.getId(), orphan.getId(), id("V", 9));

    var result = manager.references(requested);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getId()).isEqualTo(old.getId());
    assertThat(result.getFirst().getVersionNo()).isEqualTo(1);
    assertThat(result.getFirst().getOriginalFileName()).isEqualTo("历史简历-1.pdf");
    verify(access).evaluate(List.of(first, second));
    verify(versions).listByIds(requested.stream().distinct().toList());
    verify(authorization).currentActor();
  }

  @Test
  void fullResumePermissionPreservesRequestOrderAndDeduplicates() {
    when(versions.listByIds(any())).thenReturn(List.of(version(1, 1, "AVAILABLE"), version(2, 1, "AVAILABLE")));
    when(resumes.listByIds(any())).thenReturn(List.of(resume(1, 1)));
    var person = candidate(1, "ACTIVE");
    when(candidates.listByIds(any())).thenReturn(List.of(person));
    when(access.evaluate(any())).thenReturn(new CandidateReadDecision(Set.of(id("C", 1)), Set.of(id("C", 1)), Map.of()));
    assertThat(manager.references(List.of(id("V", 2), id("V", 1), id("V", 2))))
        .extracting(value -> value.getId()).containsExactly(id("V", 2), id("V", 1));
    verify(resumes).listByIds(List.of(id("R", 1)));
    verify(candidates).listByIds(List.of(id("C", 1)));
    verify(access).evaluate(List.of(person));
  }

  @Test
  void acceptsOneHundredVersionsWithOneBatchAndOneAuthorizationEvaluation() {
    var rows = IntStream.rangeClosed(1, 100).mapToObj(number -> version(number, 1, "AVAILABLE")).toList();
    var requested = rows.stream().map(ResumeVersion::getId).toList();
    when(versions.listByIds(requested)).thenReturn(rows);
    when(resumes.listByIds(List.of(id("R", 1)))).thenReturn(List.of(resume(1, 1)));
    var person = candidate(1, "ACTIVE");
    when(candidates.listByIds(List.of(id("C", 1)))).thenReturn(List.of(person));
    when(access.evaluate(List.of(person))).thenReturn(new CandidateReadDecision(Set.of(id("C", 1)), Set.of(id("C", 1)), Map.of()));
    assertThat(manager.references(requested)).extracting(value -> value.getId()).containsExactlyElementsOf(requested);
    verify(versions).listByIds(requested);
    verify(resumes).listByIds(List.of(id("R", 1)));
    verify(candidates).listByIds(List.of(id("C", 1)));
    verify(access).evaluate(List.of(person));
  }

  @Test
  void rejectsOversizedMalformedAndNullInputWithoutQueryAndAcceptsEmpty() {
    assertThatThrownBy(() -> manager.references(Collections.nCopies(101, id("V", 1))))
        .isInstanceOfSatisfying(PlatformException.class, ex -> assertThat(ex.code()).isEqualTo("TOO_MANY_RESUME_VERSIONS"));
    assertThatThrownBy(() -> manager.references(List.of("short")))
        .isInstanceOfSatisfying(PlatformException.class, ex -> assertThat(ex.code()).isEqualTo("IDENTIFIER_INVALID"));
    assertThatThrownBy(() -> manager.references(null))
        .isInstanceOfSatisfying(PlatformException.class, ex -> assertThat(ex.code()).isEqualTo("TOO_MANY_RESUME_VERSIONS"));
    assertThat(manager.references(List.of())).isEmpty();
    verifyNoInteractions(versions, resumes, candidates, access);
  }

  @Test
  void missingSessionRejectsBeforePersistence() {
    when(authorization.currentActor()).thenThrow(PlatformExceptions.forbidden("FORBIDDEN", "无权读取"));
    assertThatThrownBy(() -> manager.references(List.of(id("V", 1))))
        .isInstanceOfSatisfying(PlatformException.class, ex -> assertThat(ex.code()).isEqualTo("FORBIDDEN"));
    verifyNoInteractions(versions, resumes, candidates, access);
  }

  @Test
  void missingVersionReturnsEmptyWithoutLoadingAssociations() {
    when(versions.listByIds(any())).thenReturn(List.of());
    assertThat(manager.references(List.of(id("V", 1)))).isEmpty();
    verifyNoInteractions(resumes, candidates, access);
  }

  private static String id(String prefix, int number) { return prefix + String.format("%025d", number); }
  private static ResumeVersion version(int number, int resume, String status) {
    var value = new ResumeVersion();
    value.setId(id("V", number)); value.setResumeId(id("R", resume)); value.setStatus(status);
    value.setVersionNo(number); value.setOriginalFileName("历史简历-" + number + ".pdf");
    return value;
  }
  private static Resume resume(int number, int candidate) {
    var value = new Resume(); value.setId(id("R", number)); value.setCandidateId(id("C", candidate)); return value;
  }
  private static Candidate candidate(int number, String status) {
    var value = new Candidate(); value.setId(id("C", number)); value.setStatus(status); return value;
  }
}
