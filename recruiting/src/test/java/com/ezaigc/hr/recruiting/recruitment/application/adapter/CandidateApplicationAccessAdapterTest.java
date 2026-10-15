package com.ezaigc.hr.recruiting.recruitment.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.ApplicationCollaborator;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateApplicationAccessAdapterTest {

  @Test
  void returnsOnlyOwnedOrCollaboratingApplicationsWithTheirBoundResumeVersions() {
    var applications = mock(ApplicationPersistenceService.class);
    var collaborators = mock(ApplicationCollaboratorPersistenceService.class);
    var owned = application("owned-app", "owned-candidate", "actor", "owned-resume");
    var collaborating = application("collab-app", "collab-candidate", "other", "collab-resume");
    var unrelated = application("other-app", "private-candidate", "other", "private-resume");
    var rows = List.of(owned, collaborating, unrelated);
    when(applications.findForCandidates(List.of("owned-candidate", "collab-candidate", "private-candidate")))
        .thenReturn(rows);
    var collaborator = new ApplicationCollaborator();
    collaborator.setApplicationId("collab-app");
    collaborator.setUserId("actor");
    when(collaborators.findCollaboratorsByApplicationIds(anyCollection()))
        .thenAnswer(invocation -> {
          Collection<String> ids = invocation.getArgument(0);
          return ids.contains(collaborator.getApplicationId()) ? List.of(collaborator) : List.of();
        });
    var adapter = new CandidateApplicationAccessAdapter();
    ReflectionTestUtils.setField(adapter, "applications", applications);
    ReflectionTestUtils.setField(adapter, "collaborators", collaborators);

    var result = adapter.accessibleResumeVersionIds(
        List.of("owned-candidate", "collab-candidate", "private-candidate"), "actor");

    assertThat(result)
        .containsEntry("owned-candidate", Set.of("owned-resume"))
        .containsEntry("collab-candidate", Set.of("collab-resume"))
        .doesNotContainKey("private-candidate");
    verify(applications)
        .findForCandidates(List.of("owned-candidate", "collab-candidate", "private-candidate"));
    verify(collaborators)
        .findCollaboratorsByApplicationIds(List.of("collab-app", "other-app"));
  }

  @Test
  void ownedApplicationsExposeTheirBoundVersionsWithoutReadingCollaborators() {
    var applications = mock(ApplicationPersistenceService.class);
    var collaborators = mock(ApplicationCollaboratorPersistenceService.class);
    when(applications.findForCandidates(List.of("candidate"))).thenReturn(List.of(
        application("owned-a", "candidate", "actor", "resume-a"),
        application("owned-b", "candidate", "actor", "resume-b")));

    var result = adapter(applications, collaborators)
        .accessibleResumeVersionIds(List.of("candidate"), "actor");

    assertThat(result).containsOnlyKeys("candidate");
    assertThat(result.get("candidate")).containsExactlyInAnyOrder("resume-a", "resume-b");
    assertThatThrownBy(() -> result.put("other", Set.of("unbound")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> result.get("candidate").add("unbound"))
        .isInstanceOf(UnsupportedOperationException.class);
    verify(applications).findForCandidates(List.of("candidate"));
    verifyNoInteractions(collaborators);
  }

  @Test
  void owningOneApplicationDoesNotSkipForeignVersionsForTheSameCandidate() {
    var applications = mock(ApplicationPersistenceService.class);
    var collaborators = mock(ApplicationCollaboratorPersistenceService.class);
    when(applications.findForCandidates(List.of("candidate"))).thenReturn(List.of(
        application("owned", "candidate", "actor", "owned-version"),
        application("collab", "candidate", "other", "collab-version"),
        application("private", "candidate", "other", "private-version")));
    var relations = List.of(collaborator("collab", "actor"), collaborator("private", "other-user"));
    when(collaborators.findCollaboratorsByApplicationIds(anyCollection()))
        .thenAnswer(invocation -> {
          Collection<String> ids = invocation.getArgument(0);
          return relations.stream().filter(row -> ids.contains(row.getApplicationId())).toList();
        });

    var result = adapter(applications, collaborators)
        .accessibleResumeVersionIds(List.of("candidate"), "actor");

    assertThat(result).containsOnlyKeys("candidate");
    assertThat(result.get("candidate"))
        .containsExactlyInAnyOrder("owned-version", "collab-version");
    verify(collaborators).findCollaboratorsByApplicationIds(List.of("collab", "private"));
  }

  @Test
  void emptyAndOversizedInputsShortCircuitAndMissingApplicationsSkipCollaborators() {
    var applications = mock(ApplicationPersistenceService.class);
    var collaborators = mock(ApplicationCollaboratorPersistenceService.class);
    var adapter = adapter(applications, collaborators);
    assertThat(adapter.accessibleResumeVersionIds(null, "actor")).isEmpty();
    assertThat(adapter.accessibleResumeVersionIds(List.of(), "actor")).isEmpty();
    var oversized = IntStream.range(0, 1001).mapToObj(i -> "candidate-" + i).toList();
    assertThatThrownBy(() -> adapter.accessibleResumeVersionIds(oversized, "actor"))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("候选人批量查询最多1000项");
    verifyNoInteractions(applications, collaborators);
    var maximum = oversized.subList(0, 1000);
    when(applications.findForCandidates(maximum)).thenReturn(List.of());

    assertThat(adapter.accessibleResumeVersionIds(maximum, "actor")).isEmpty();

    verify(applications).findForCandidates(maximum);
    verifyNoInteractions(collaborators);
  }

  @Test
  void foreignCollaboratorAccessIsReloadedPerActorAndAfterRevocation() {
    var applications = mock(ApplicationPersistenceService.class);
    var collaborators = mock(ApplicationCollaboratorPersistenceService.class);
    when(applications.findForCandidates(List.of("candidate"))).thenReturn(List.of(
        application("foreign", "candidate", "third-owner", "bound-version")));
    when(collaborators.findCollaboratorsByApplicationIds(List.of("foreign")))
        .thenReturn(List.of(collaborator("foreign", "actor")),
            List.of(collaborator("foreign", "actor")), List.of());
    var adapter = adapter(applications, collaborators);

    assertThat(adapter.accessibleResumeVersionIds(List.of("candidate"), "actor"))
        .containsEntry("candidate", Set.of("bound-version"));
    assertThat(adapter.accessibleResumeVersionIds(List.of("candidate"), "other-actor")).isEmpty();
    assertThat(adapter.accessibleResumeVersionIds(List.of("candidate"), "actor")).isEmpty();

    verify(applications, times(3)).findForCandidates(List.of("candidate"));
    verify(collaborators, times(3)).findCollaboratorsByApplicationIds(List.of("foreign"));
  }

  @Test
  void foreignCollaboratorFailurePropagatesWithoutReturningPartialOwnedAccess() {
    var applications = mock(ApplicationPersistenceService.class);
    var collaborators = mock(ApplicationCollaboratorPersistenceService.class);
    when(applications.findForCandidates(List.of("candidate"))).thenReturn(List.of(
        application("owned", "candidate", "actor", "owned-version"),
        application("foreign", "candidate", "other", "foreign-version")));
    var failure = new IllegalStateException("synthetic collaborator lookup failure");
    doThrow(failure).when(collaborators).findCollaboratorsByApplicationIds(anyCollection());

    assertThatThrownBy(() -> adapter(applications, collaborators)
        .accessibleResumeVersionIds(List.of("candidate"), "actor")).isSameAs(failure);

    verify(applications).findForCandidates(List.of("candidate"));
    verify(collaborators).findCollaboratorsByApplicationIds(anyCollection());
  }

  private static CandidateApplicationAccessAdapter adapter(
      ApplicationPersistenceService applications, ApplicationCollaboratorPersistenceService collaborators) {
    var adapter = new CandidateApplicationAccessAdapter();
    ReflectionTestUtils.setField(adapter, "applications", applications);
    ReflectionTestUtils.setField(adapter, "collaborators", collaborators);
    return adapter;
  }

  private static ApplicationCollaborator collaborator(String applicationId, String userId) {
    var collaborator = new ApplicationCollaborator();
    collaborator.setApplicationId(applicationId);
    collaborator.setUserId(userId);
    return collaborator;
  }

  private static Application application(
      String id, String candidateId, String ownerUserId, String resumeVersionId) {
    var application = new Application();
    application.setId(id);
    application.setCandidateId(candidateId);
    application.setOwnerUserId(ownerUserId);
    application.setResumeVersionId(resumeVersionId);
    return application;
  }
}
