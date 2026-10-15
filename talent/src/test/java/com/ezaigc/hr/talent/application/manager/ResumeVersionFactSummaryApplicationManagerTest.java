package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.Resume;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.domain.response.FactBundleResponse;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.ResumePersistenceService;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeVersionFactSummaryApplicationManagerTest {
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final ResumeVersionPersistenceService versions = mock(ResumeVersionPersistenceService.class);
  private final ResumePersistenceService resumes = mock(ResumePersistenceService.class);
  private final CandidatePersistenceService candidates = mock(CandidatePersistenceService.class);
  private final CandidateReadAccess access = mock(CandidateReadAccess.class);
  private final ResumeListFactApplicationManager facts = mock(ResumeListFactApplicationManager.class);
  private final ResumeVersionFactSummaryApplicationManager manager = new ResumeVersionFactSummaryApplicationManager();

  @BeforeEach
  void wireDependencies() {
    ReflectionTestUtils.setField(manager,"authorization",authorization);
    ReflectionTestUtils.setField(manager,"versions",versions);
    var readAccess = new ResumeVersionReadAccess();
    ReflectionTestUtils.setField(readAccess,"resumes",resumes);
    ReflectionTestUtils.setField(readAccess,"candidates",candidates);
    ReflectionTestUtils.setField(readAccess,"access",access);
    ReflectionTestUtils.setField(manager,"readAccess",readAccess);
    ReflectionTestUtils.setField(manager,"facts",facts);
  }

  @Test
  void authorizesActualHistoricalVersionAndDoesNotSubstituteCurrentVersion() {
    var old = version("v-old000000000000000000000", "resume-a", "AVAILABLE");
    var current = version("v-new000000000000000000000", "resume-a", "AVAILABLE");
    var other = version("v-other0000000000000000000", "resume-b", "AVAILABLE");
    var deleted = version("v-deleted00000000000000000", "resume-c", "AVAILABLE");
    var unavailable = version("v-unavailable0000000000000", "resume-a", "DELETED");
    when(versions.listByIds(any())).thenReturn(List.of(current, old, other, deleted, unavailable));
    when(resumes.listByIds(any())).thenReturn(List.of(resume("resume-a", "c-a"),resume("resume-b", "c-b"),resume("resume-c", "c-c")));
    var a = person("c-a", "ACTIVE");
    var b = person("c-b", "ACTIVE");
    when(candidates.listByIds(any())).thenReturn(List.of(a,b,person("c-c", "DELETED")));
    // 仅被授权申请中的旧版本；其他候选人不能借同一个版本 ID 获得权限。
    when(access.evaluate(any())).thenReturn(new CandidateReadDecision(Set.of("c-a"),Set.of(),Map.of("c-a",Set.of("v-old000000000000000000000","v-other0000000000000000000"))));
    var bundle = new FactBundleResponse(null,List.of(),List.of(),List.of(),List.of(),List.of());
    when(facts.summaries(List.of("v-old000000000000000000000"))).thenReturn(Map.of("v-old000000000000000000000",bundle));

    var result = manager.summaries(List.of("v-other0000000000000000000","v-old000000000000000000000","v-new000000000000000000000","v-old000000000000000000000","v-missing00000000000000000","v-deleted00000000000000000","v-unavailable0000000000000"));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getResumeVersionId()).isEqualTo("v-old000000000000000000000");
    assertThat(result.getFirst().getFacts()).isSameAs(bundle);
    verify(facts).summaries(List.of("v-old000000000000000000000"));
    verify(access).evaluate(List.of(a,b));
    verify(authorization).currentActor();
  }

  @Test
  void keepsRequestedOrderAndRepresentsAuthorizedUnparsedVersionWithoutFacts() {
    when(versions.listByIds(any())).thenReturn(List.of(version("v-a00000000000000000000000","resume","AVAILABLE"),version("v-b00000000000000000000000","resume","AVAILABLE")));
    when(resumes.listByIds(any())).thenReturn(List.of(resume("resume","candidate")));
    when(candidates.listByIds(any())).thenReturn(List.of(person("candidate","ACTIVE")));
    when(access.evaluate(any())).thenReturn(new CandidateReadDecision(Set.of("candidate"),Set.of("candidate"),Map.of()));
    when(facts.summaries(any())).thenReturn(Map.of());
    var result = manager.summaries(List.of("v-b00000000000000000000000","v-a00000000000000000000000","v-b00000000000000000000000"));
    assertThat(result).extracting("resumeVersionId").containsExactly("v-b00000000000000000000000","v-a00000000000000000000000");
    assertThat(result).allSatisfy(row -> assertThat(row.getFacts()).isNull());
    verify(facts).summaries(List.of("v-b00000000000000000000000","v-a00000000000000000000000"));
  }

  @Test
  void rejectsOversizedAndMalformedInputBeforePersistenceAndAcceptsEmptyInput() {
    assertThatThrownBy(() -> manager.summaries(Collections.nCopies(101,"version0000000000000000000"))).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> manager.summaries(List.of(" "))).isInstanceOf(RuntimeException.class);
    assertThat(manager.summaries(List.of())).isEmpty();
    verifyNoInteractions(versions,resumes,candidates,access,facts);
  }

  @Test
  void rejectsMissingSessionBeforeLookingUpVersions() {
    when(authorization.currentActor()).thenThrow(PlatformExceptions.forbidden("FORBIDDEN","无权读取"));
    assertThatThrownBy(() -> manager.summaries(List.of("version0000000000000000000"))).isInstanceOf(RuntimeException.class);
    verifyNoInteractions(versions,resumes,candidates,access,facts);
  }

  @Test
  void missingResumeAssociationCannotAuthorizeOrLoadFacts() {
    when(versions.listByIds(any())).thenReturn(List.of(version("version0000000000000000000","missing","AVAILABLE")));
    when(resumes.listByIds(any())).thenReturn(List.of());
    assertThat(manager.summaries(List.of("version0000000000000000000"))).isEmpty();
    verifyNoInteractions(candidates,access,facts);
  }

  private static ResumeVersion version(String id,String resumeId,String status) {
    var value = new ResumeVersion(); value.setId(id); value.setResumeId(resumeId); value.setStatus(status); return value;
  }
  private static Resume resume(String id,String candidateId) {
    var value = new Resume(); value.setId(id); value.setCandidateId(candidateId); return value;
  }
  private static Candidate person(String id,String status) {
    var value = new Candidate(); value.setId(id); value.setStatus(status); return value;
  }
}
