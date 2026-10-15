package com.ezaigc.hr.recruiting.recruitment.application.manager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ApplicationOperationalScopeTest {
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final ApplicationPersistenceService persistence = mock(ApplicationPersistenceService.class);
  private final RecruitmentApplicationManager manager = new RecruitmentApplicationManager();

  @BeforeEach
  void setUp() {
    var actor = new CurrentActorSnapshot(
        "manager", "招聘负责人", "manager@example.invalid", "department",
        Set.of(), Set.of("member"), Set.of(), Set.of("RECRUITMENT_MANAGER"),
        Set.of("recruitment.manage"), false, false);
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "cursorCodec", mock(CursorCodec.class));
    ReflectionTestUtils.setField(manager, "applicationCollaboratorPersistenceService",
        mock(ApplicationCollaboratorPersistenceService.class));
  }

  @Test
  void operationalRecruitmentListDefaultsToTheManagersOwnRecords() {
    when(persistence.findApplications(null, null, "manager", "IN_PROCESS", null, "ALL",
        null, null, null, null, null, 200)).thenReturn(List.of());

    manager.list(null, null, "IN_PROCESS", null, null, null, 20, null, null);

    verify(persistence).findApplications(null, null, "manager", "IN_PROCESS", null, "ALL",
        null, null, null, null, null, 200);
  }

  @Test
  void onboardedListKeepsLeadershipScope() {
    when(persistence.findApplications(null, null, null, "ONBOARDED", null, "ALL",
        null, null, null, null, null, 200)).thenReturn(List.of());

    manager.list(null, null, "ONBOARDED", null, null, null, 20, null, null);

    verify(persistence).findApplications(null, null, null, "ONBOARDED", null, "ALL",
        null, null, null, null, null, 200);
  }
}
