package com.ezaigc.hr.recruiting.interview.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.interview.application.assembler.InterviewAssembler;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewJobOption;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InvitedJobOptionsTest {
  private final InterviewApplicationManager manager=new InterviewApplicationManager();
  private final AuthorizationService authorization=mock(AuthorizationService.class);
  private final InterviewPersistenceService persistence=mock(InterviewPersistenceService.class);
  private final CurrentActorSnapshot actor=mock(CurrentActorSnapshot.class);
  @BeforeEach void setup() {
    when(actor.getUserId()).thenReturn("interviewer");
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager,"authorization",authorization);
    ReflectionTestUtils.setField(manager, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(manager,"persistence",persistence);
    ReflectionTestUtils.setField(manager,"assembler",new InterviewAssembler());
    ReflectionTestUtils.setField(manager,"cursorCodec",new SignedCursorCodec("invited-job-options-cursor-signing-key"));
  }
  @Test void permissionIsRequiredBeforeReadingAssignedJobs() {
    doThrow(PlatformExceptions.forbidden("DENIED","无招聘处理权限"))
        .when(authorization).requirePermission("recruitment.manage");
    assertThatThrownBy(()->manager.invitedJobOptions(null,null,null,50)).hasMessageContaining("无招聘处理权限");
    verifyNoInteractions(persistence);
  }
  @Test void searchesOnlyCurrentActorsAssignmentsAndBindsCursorToActorQueryAndJob() {
    when(persistence.findInvitedJobOptions(anyString(),any(),any(),any(),any(),anyInt()))
        .thenReturn(List.of(option("A"),option("B")));
    var page=manager.invitedJobOptions("  Linux  ","job",null,1);
    assertThat(page.getItems()).hasSize(1);
    assertThat(page.getItems().getFirst().getTitle()).isEqualTo("Linux岗位");
    assertThat(page.isHasMore()).isTrue();
    verify(persistence).findInvitedJobOptions("interviewer","Linux","job",null,null,2);
    manager.invitedJobOptions("Linux","job",page.getNextCursor(),1);
    verify(persistence).findInvitedJobOptions("interviewer","Linux","job",Instant.EPOCH,"A".repeat(26),2);
    assertThatThrownBy(()->manager.invitedJobOptions("other","job",page.getNextCursor(),1)).hasMessageContaining("游标");
    assertThatThrownBy(()->manager.invitedJobOptions("Linux","other",page.getNextCursor(),1)).hasMessageContaining("游标");
    when(actor.getUserId()).thenReturn("another-interviewer");
    assertThatThrownBy(()->manager.invitedJobOptions("Linux","job",page.getNextCursor(),1)).hasMessageContaining("游标");
  }
  private InterviewJobOption option(String id) {
    var value=new InterviewJobOption(); value.setJdId("job"); value.setJdVersionId(id.repeat(26));
    value.setJdVersionNo(1); value.setTitle("Linux岗位"); value.setSortTime(Instant.EPOCH);return value;
  }
}
