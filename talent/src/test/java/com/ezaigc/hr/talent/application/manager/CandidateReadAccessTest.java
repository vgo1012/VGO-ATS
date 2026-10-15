package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.api.CandidateApplicationAccessQuery;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateReadAccessTest {

  @Test
  void companyScopeReadsEveryPrivateCandidateAndManagerReadsManagedOwners() {
    var authorization = mock(AuthorizationService.class);
    var library = mock(CandidateLibraryQueryService.class);
    var applications = mock(CandidateApplicationAccessQuery.class);
    var actor = new CurrentActorSnapshot(
        "actor", "负责人", "actor@example.invalid", "department", Set.of(), Set.of("other-owner"),
        Set.of(), Set.of("SUPER_ADMIN"), Set.of("talent.read"), true, true);
    when(authorization.currentActor()).thenReturn(actor);
    var own = candidate("own", "actor");
    var shared = candidate("shared", "other-owner");
    var collaborating = candidate("collaborating", "other-owner");
    var privateCandidate = candidate("private", "other-owner");
    var rows = List.of(own, shared, collaborating, privateCandidate);
    when(library.eligibleCandidateIds(rows.stream().map(Candidate::getId).toList()))
        .thenReturn(Set.of("shared"));
    when(applications.accessibleResumeVersionIds(
            rows.stream().map(Candidate::getId).toList(), "actor"))
        .thenReturn(Map.of("collaborating", Set.of("application-resume")));
    when(library.eligibleCandidateIds(List.of("private"))).thenReturn(Set.of());
    when(applications.accessibleResumeVersionIds(List.of("private"), "actor"))
        .thenReturn(Map.of());

    var access = new CandidateReadAccess();
    ReflectionTestUtils.setField(access, "authorization", authorization);
    ReflectionTestUtils.setField(access, "library", library);
    ReflectionTestUtils.setField(access, "applications", applications);
    var decision = access.evaluate(rows);

    assertThat(decision.getReadableCandidateIds())
        .containsExactlyInAnyOrder("own", "shared", "collaborating", "private");
    assertThat(decision.canReadAllResumes("own")).isTrue();
    assertThat(decision.canReadAllResumes("shared")).isTrue();
    assertThat(decision.canReadAllResumes("collaborating")).isTrue();
    assertThat(decision.canReadResume("collaborating", "application-resume")).isTrue();
    assertThat(decision.canReadResume("collaborating", "unrelated-resume")).isTrue();
    assertThat(decision.canReadAllResumes("private")).isTrue();

    actor.setCompanyScope(false);
    actor.setSuperAdmin(false);
    var managedDecision = access.evaluate(rows);
    assertThat(managedDecision.canReadAllResumes("private")).isTrue();
  }

  @Test
  void recruiterCannotReadAnotherUsersPrivateCandidateThroughDepartmentMembership() {
    var authorization = mock(AuthorizationService.class);
    var library = mock(CandidateLibraryQueryService.class);
    var applications = mock(CandidateApplicationAccessQuery.class);
    var actor = new CurrentActorSnapshot(
        "actor", "招聘人员", "actor@example.invalid", "department", Set.of("supported"), Set.of(),
        Set.of("department"), Set.of("RECRUITER"), Set.of("talent.read"), false, false);
    var privateCandidate = candidate("private", "other-owner");
    when(authorization.currentActor()).thenReturn(actor);
    when(library.eligibleCandidateIds(List.of("private"))).thenReturn(Set.of());
    when(applications.accessibleResumeVersionIds(List.of("private"), "actor")).thenReturn(Map.of());
    var access = new CandidateReadAccess();
    ReflectionTestUtils.setField(access, "authorization", authorization);
    ReflectionTestUtils.setField(access, "library", library);
    ReflectionTestUtils.setField(access, "applications", applications);

    assertThatThrownBy(() -> access.requireCandidate(privateCandidate))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CANDIDATE_ACCESS_FORBIDDEN"));
  }

  @Test
  void authenticatedUserWithoutBusinessPermissionsReadsSharedCandidateButNotPrivateCandidate() {
    var authorization = mock(AuthorizationService.class);
    var library = mock(CandidateLibraryQueryService.class);
    var applications = mock(CandidateApplicationAccessQuery.class);
    var actor = new CurrentActorSnapshot(
        "actor", "面试官", "actor@example.invalid", "department", Set.of(), Set.of("other-owner"),
        Set.of(), Set.of("INTERVIEWER"), Set.of(), true, false);
    var shared = candidate("shared", "other-owner");
    var privateCandidate = candidate("private", "other-owner");
    when(authorization.currentActor()).thenReturn(actor);
    when(library.eligibleCandidateIds(List.of("shared", "private"))).thenReturn(Set.of("shared"));
    var access = new CandidateReadAccess();
    ReflectionTestUtils.setField(access, "authorization", authorization);
    ReflectionTestUtils.setField(access, "library", library);
    ReflectionTestUtils.setField(access, "applications", applications);

    var decision = access.evaluate(List.of(shared, privateCandidate));

    assertThat(decision.canReadAllResumes("shared")).isTrue();
    assertThat(decision.canReadCandidate("private")).isFalse();
    verifyNoInteractions(applications);
  }

  private static Candidate candidate(String id, String ownerUserId) {
    var candidate = new Candidate();
    candidate.setId(id);
    candidate.setOwnerUserId(ownerUserId);
    candidate.setOwnerDepartmentId("department");
    candidate.setStatus("ACTIVE");
    return candidate;
  }
}
