package com.ezaigc.hr.recruiting.interview.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.application.assembler.InterviewAssembler;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.interview.domain.entity.Participant;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ParticipantPersistenceService;
import com.ezaigc.hr.talent.api.CandidateDirectoryService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InterviewInvitationScopeTest {
  private final InterviewApplicationManager manager=new InterviewApplicationManager();
  private final InterviewPersistenceService interviews=mock(InterviewPersistenceService.class);
  private final ParticipantPersistenceService participants=mock(ParticipantPersistenceService.class);
  private final AuthorizationService authorization=mock(AuthorizationService.class);
  private final Interview interview=new Interview();
  @BeforeEach void setup() {
    var actor=new CurrentActorSnapshot("interviewer","面试官","qa@example.invalid","department",
        Set.of("department"),Set.of(),Set.of(),Set.of("INTERVIEWER"),
        Set.of("interview.feedback.read-authorized","interview.feedback.submit-own"),false,false);
    when(authorization.currentActor()).thenReturn(actor);
    // 通用部门范围会认为可读，面试用例必须优先执行受邀限制。
    when(authorization.canAccess(any(),anyString(),any())).thenReturn(true);
    when(authorization.accessibleObjectIds(any(),anyString(),any())).thenReturn(Set.of("interview"));
    interview.setId("interview"); interview.setScheduledStartAt(Instant.parse("2026-09-08T00:00:00Z"));
    when(interviews.findInterview("interview")).thenReturn(interview);
    when(interviews.findInterviews(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(), any(), anyInt()))
        .thenReturn(List.of(interview));
    ReflectionTestUtils.setField(manager,"authorization",authorization);
    ReflectionTestUtils.setField(manager, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(manager,"persistence",interviews);
    ReflectionTestUtils.setField(manager,"participantPersistenceService",participants);
    ReflectionTestUtils.setField(manager,"cursorCodec",new SignedCursorCodec("invitation-scope-test-signing-key-32"));
  }
  @Test void sameDepartmentWithoutInvitationCannotReadDetailOrAppearInList() {
    assertThatThrownBy(()->manager.get("interview")).hasMessageContaining("无权");
    assertThat(manager.list(null,null,null,null,null,null,null,null,10).getItems()).isEmpty();
  }
  @Test void currentInvitationAllowsBothDetailAndList() {
    var participant=new Participant("interview","interviewer","INTERVIEWER","PENDING");
    when(participants.findParticipants("interview")).thenReturn(List.of(participant));
    when(participants.findParticipantsByInterviewIds(any())).thenReturn(List.of(participant));
    assertThat(manager.get("interview").getInterview()).isSameAs(interview);
    assertThat(manager.list(null,null,null,null,null,null,null,null,10).getItems()).containsExactly(interview);
  }
  @Test void invitationOnlyResponsesDoNotExposeRecruiterInitialComment() {
    var participant=new Participant("interview","interviewer","INTERVIEWER","PENDING");
    when(participants.findParticipants("interview")).thenReturn(List.of(participant));
    when(participants.findParticipantsByInterviewIds(any())).thenReturn(List.of(participant));
    interview.setInitialComment("招聘处理人的排期面评"); interview.setJdVersionId("version");
    ReflectionTestUtils.setField(manager,"assembler",new InterviewAssembler());
    ReflectionTestUtils.setField(manager,"demands",mock(DemandQueryService.class));
    ReflectionTestUtils.setField(manager,"candidates",mock(CandidateDirectoryService.class));
    assertThat(manager.getResponse("interview").getInterview().getInitialComment()).isNull();
    var page = manager.listResponses(null,null,null,null,null,null,null,null,null,null,10,null,null);
    assertThat(page.getItems()).hasSize(1);
    assertThat(page.getItems().getFirst().getInitialComment()).isNull();
    assertThat(interview.getInitialComment()).isEqualTo("招聘处理人的排期面评");
  }

}
