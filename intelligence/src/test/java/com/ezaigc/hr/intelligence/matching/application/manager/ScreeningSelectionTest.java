package com.ezaigc.hr.intelligence.matching.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.intelligence.matching.domain.dto.ScreeningSelectionQuery;
import com.ezaigc.hr.intelligence.matching.domain.entity.ScreeningCandidateRow;
import com.ezaigc.hr.intelligence.matching.domain.response.ScreeningSelectionCandidateResponse;
import com.ezaigc.hr.intelligence.matching.service.ScreeningAdmissionPersistenceService;
import com.ezaigc.hr.recruiting.demand.api.JdFactQueryService;
import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ScreeningSelectionTest {
  private static final String JD = "J".repeat(26);
  private static final String CANDIDATE = "C".repeat(26);
  private static final Instant NOW = Instant.parse("2026-09-11T01:00:00Z");
  private final ScreeningAdmissionApplicationManager manager = new ScreeningAdmissionApplicationManager();
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final CurrentActorSnapshot actor = new CurrentActorSnapshot();
  private final JdFactQueryService jdFacts = mock(JdFactQueryService.class);
  private final ResumeFactQueryService resumeFacts = mock(ResumeFactQueryService.class);
  private final ScreeningAdmissionPersistenceService admissions = mock(ScreeningAdmissionPersistenceService.class);

  @BeforeEach
  void setUp() {
    actor.setUserId("U".repeat(26));
    actor.setManagedUserIds(Set.of());
    actor.setExplicitlyGrantedDepartmentIds(Set.of());
    when(authorization.currentActor()).thenReturn(actor);
    when(resumeFacts.currentFactBatch(anyList())).thenReturn(id -> {
      var facts = new ResumeFactSnapshotData();
      facts.setCandidateId(CANDIDATE);
      facts.setResumeVersionId(id);
      return facts;
    });
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "jdFacts", jdFacts);
    ReflectionTestUtils.setField(manager, "resumeFacts", resumeFacts);
    ReflectionTestUtils.setField(manager, "admissions", admissions);
    ReflectionTestUtils.setField(manager, "cursorCodec",
        new SignedCursorCodec("screening-selection-test-signing-key-32-chars"));
  }

  @Test
  void selectsAdmittedVersionsDirectlyWithoutRequiringPersonalPoolMembership() {
    when(admissions.findSelectionCandidates(any())).thenReturn(List.of(row(2), row(1)));

    var page = manager.selectionCandidates(JD, " 张 ", null, 20);

    assertThat(page.getItems()).extracting(ScreeningSelectionCandidateResponse::getResumeVersionId)
        .containsExactly(resume(2), resume(1));
    assertThat(page.getItems()).extracting(ScreeningSelectionCandidateResponse::getCandidateId)
        .containsExactly(CANDIDATE, CANDIDATE);
    assertThat(page.getItems().getFirst().getCandidateName()).isEqualTo("张三");
    assertThat(page.getItems().getFirst().getResumeVersionNo()).isEqualTo(2);
    assertThat(page.getItems().getFirst().getOriginalFileName()).isEqualTo("简历2.pdf");
    assertThat(page.getItems().getFirst().getProfessionalScore()).isEqualByComparingTo("80");
    assertThat(page.isHasMore()).isFalse();
    var captured = ArgumentCaptor.forClass(ScreeningSelectionQuery.class);
    verify(admissions).findSelectionCandidates(captured.capture());
    assertThat(captured.getValue().getJdVersionId()).isEqualTo(JD);
    assertThat(captured.getValue().getQuery()).isEqualTo("张");
    assertThat(captured.getValue().getActorId()).isEqualTo(actor.getUserId());
    assertThat(captured.getValue().getLimit()).isEqualTo(21);
    verify(jdFacts).requireCurrentFactsReadAccess(JD);
    verify(resumeFacts).currentFactBatch(List.of(resume(2), resume(1)));
  }

  @Test
  void countUsesTheSameSelectionQueryAndReturnsABoundedContinuation() {
    when(admissions.findSelectionCandidates(any())).thenReturn(List.of(row(2), row(1)));

    var count = manager.countSelectionCandidates(JD, " 张 ", null);

    assertThat(count.getCount()).isEqualTo(2);
    assertThat(count.isHasMore()).isFalse();
    var captured = ArgumentCaptor.forClass(ScreeningSelectionQuery.class);
    verify(admissions).findSelectionCandidates(captured.capture());
    assertThat(captured.getValue().getQuery()).isEqualTo("张");
    assertThat(captured.getValue().getLimit()).isEqualTo(101);
  }

  @Test
  void pagesAtTheLastReturnedAdmissionInsteadOfTheLookaheadOrCandidateId() {
    when(admissions.findSelectionCandidates(any())).thenReturn(List.of(row(3), row(2), row(1)));
    var first = manager.selectionCandidates(JD, null, null, 2);
    assertThat(first.getItems()).hasSize(2);
    assertThat(first.isHasMore()).isTrue();
    when(admissions.findSelectionCandidates(any())).thenAnswer(invocation -> {
      ScreeningSelectionQuery query = invocation.getArgument(0);
      assertThat(query.getBeforeId()).isEqualTo(admission(2));
      assertThat(query.getBeforeTime()).isEqualTo(NOW);
      return List.of(row(1));
    });
    var second = manager.selectionCandidates(JD, null, first.getNextCursor(), 2);
    assertThat(second.getItems()).extracting(ScreeningSelectionCandidateResponse::getResumeVersionId)
        .containsExactly(resume(1));
    assertThat(second.isHasMore()).isFalse();
  }

  @Test
  void cursorCannotCrossJobSearchActorOrPermissionScope() {
    when(admissions.findSelectionCandidates(any())).thenReturn(List.of(row(2), row(1)));
    String cursor = manager.selectionCandidates(JD, null, null, 1).getNextCursor();
    assertInvalidCursor("K".repeat(26), null, cursor);
    assertInvalidCursor(JD, "张", cursor);
    actor.setManagedUserIds(Set.of("M".repeat(26)));
    manager.selectionCandidates(JD, null, cursor, 1);
    actor.setManagedUserIds(Set.of());
    actor.setUserId("Z".repeat(26));
    assertInvalidCursor(JD, null, cursor);
  }

  @Test
  void rejectsUnreadableJobBeforeQueryingEvenWhenThePageWouldBeEmpty() {
    doThrow(PlatformExceptions.forbidden("JD_FORBIDDEN", "不可见"))
        .when(jdFacts).requireCurrentFactsReadAccess(JD);
    assertThatThrownBy(() -> manager.selectionCandidates(JD, null, null, 20))
        .isInstanceOf(PlatformException.class);
    verifyNoInteractions(admissions, resumeFacts);
  }

  @Test
  void rechecksResumePermissionsBeforeExposingCandidateNames() {
    when(admissions.findSelectionCandidates(any())).thenReturn(List.of(row(1)));
    when(resumeFacts.currentFactBatch(anyList()))
        .thenThrow(PlatformExceptions.forbidden("RESUME_FORBIDDEN", "权限已收窄"));
    assertThatThrownBy(() -> manager.selectionCandidates(JD, null, null, 20))
        .isInstanceOf(PlatformException.class);
  }

  @Test
  void rejectsInvalidPageAndQueryBoundsWithoutQuerying() {
    assertThatThrownBy(() -> manager.selectionCandidates(JD, null, null, 101))
        .isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> manager.selectionCandidates(JD, "名".repeat(129), null, 20))
        .isInstanceOf(PlatformException.class);
    verify(admissions, never()).findSelectionCandidates(any());
  }

  private void assertInvalidCursor(String jd, String query, String cursor) {
    assertThatThrownBy(() -> manager.selectionCandidates(jd, query, cursor, 1))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CURSOR_INVALID"));
  }

  private static ScreeningCandidateRow row(int index) {
    var row = new ScreeningCandidateRow();
    row.setCandidateId(CANDIDATE);
    row.setCandidateName("张三");
    row.setResumeVersionId(resume(index));
    row.setResumeVersionNo(index);
    row.setOriginalFileName("简历" + index + ".pdf");
    row.setJdVersionId(JD);
    row.setDefaultMatchRunId("R".repeat(26));
    row.setAdmissionId(admission(index));
    row.setAdmittedAt(NOW);
    row.setProfessionalScore(new BigDecimal("80"));
    row.setReferenceScore(new BigDecimal("85"));
    return row;
  }

  private static String resume(int index) { return String.format("V%025d", index); }
  private static String admission(int index) { return String.format("A%025d", index); }
}
