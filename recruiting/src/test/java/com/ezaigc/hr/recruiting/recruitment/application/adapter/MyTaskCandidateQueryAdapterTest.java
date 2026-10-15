package com.ezaigc.hr.recruiting.recruitment.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MyTaskCandidateQueryAdapterTest {
  private final MyTaskCandidateQueryAdapter adapter = new MyTaskCandidateQueryAdapter();
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final ApplicationPersistenceService applications = mock(ApplicationPersistenceService.class);

  @BeforeEach
  void setup() {
    var actor = mock(CurrentActorSnapshot.class);
    when(actor.getUserId()).thenReturn("actor");
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(adapter, "authorization", authorization);
    ReflectionTestUtils.setField(adapter, "applications", applications);
  }

  @Test
  void returnsDistinctCandidatesWithCurrentUsersActiveTasks() {
    when(applications.findMyTaskCandidateIds("actor", List.of("a", "b"), "jd-selected"))
        .thenReturn(List.of("b", "b"));

    assertThat(adapter.candidateIds(List.of("a", "b"), "jd-selected")).containsExactly("b");
    verify(authorization).requirePermission("talent.read");
    verify(authorization).requirePermission("recruitment.manage");
  }

  @Test
  void emptyInputStillChecksPermissionsAndSkipsDatabase() {
    assertThat(adapter.candidateIds(List.of(), null)).isEmpty();
    verify(authorization).requirePermission("talent.read");
    verify(authorization).requirePermission("recruitment.manage");
    verifyNoInteractions(applications);
  }
}
