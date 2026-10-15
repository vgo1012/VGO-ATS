package com.ezaigc.hr.recruiting.activity.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.activity.domain.dto.ActivityRecordQuery;
import com.ezaigc.hr.recruiting.activity.domain.dto.ActivityRecordRowDTO;
import com.ezaigc.hr.recruiting.activity.domain.response.ActivityFactResponse;
import com.ezaigc.hr.recruiting.activity.service.ActivityOriginPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ParticipantPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.application.manager.RecruitmentApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ActivityRecordManagerTest {
  private final ActivityRecordManager manager = new ActivityRecordManager();
  private final ActivityOriginPersistenceService origins = mock(ActivityOriginPersistenceService.class);
  private final RecruitmentApplicationManager applications = mock(RecruitmentApplicationManager.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final CurrentActorSnapshot actor = mock(CurrentActorSnapshot.class);

  @BeforeEach void setup() {
    var app = new Application(); app.setId("application"); app.setCandidateId("candidate");
    when(applications.requireFollowUpApplication(anyString())).thenReturn(app);
    when(authorization.currentActor()).thenReturn(actor);
    when(actor.getUserId()).thenReturn("actor");
    ReflectionTestUtils.setField(manager, "origins", origins);
    ReflectionTestUtils.setField(manager, "applications", applications);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(manager, "interviews", mock(InterviewPersistenceService.class));
    ReflectionTestUtils.setField(manager, "participants", mock(ParticipantPersistenceService.class));
    ReflectionTestUtils.setField(manager, "objectMapper", new ObjectMapper());
    ReflectionTestUtils.setField(manager, "cursorCodec", new SignedCursorCodec("activity-records-pagination-test-secret-32"));
  }

  @Test void keysetContinuesEqualTimesAndBindsFilterAndActor() {
    when(origins.findRecords(any())).thenAnswer(call -> {
      ActivityRecordQuery query = call.getArgument(0);
      assertThat(query.getLimit()).isEqualTo(100);
      return List.of("3", "2", "1").stream().map(id -> id.repeat(26))
          .filter(id -> query.getCursorId() == null || id.compareTo(query.getCursorId()) < 0)
          .map(id -> row(id, "COMMUNICATION", null)).toList();
    });
    var first = manager.records("application", null, null, 2);
    assertThat(first.getItems()).hasSize(2);
    assertThat(first.getItems().getFirst().getTitle()).isEqualTo("实际沟通");
    assertThat(first.isHasMore()).isTrue();
    assertThat(manager.records("application", null, first.getNextCursor(), 2).getItems()).hasSize(1);
    assertThatThrownBy(() -> manager.records("application", "PENDING", first.getNextCursor(), 2))
        .isInstanceOf(PlatformException.class);
    when(actor.getUserId()).thenReturn("another-actor");
    assertThatThrownBy(() -> manager.records("application", null, first.getNextCursor(), 2))
        .isInstanceOf(PlatformException.class);
    verify(applications, atLeastOnce()).requireFollowUpApplication("application");
  }

  @Test void unauthorizedInterviewAndCompanyRestrictionFactsNeverReachResponse() {
    when(origins.findRecords(any())).thenReturn(List.of(
        row("3".repeat(26), "INTERVIEW_FEEDBACK", "interview"),
        row("2".repeat(26), "RESTRICTION", null),
        row("1".repeat(26), "COMMUNICATION", null)));
    var page = manager.records("application", null, null, 20);
    assertThat(page.getItems()).extracting(value -> value.getId()).containsExactly("1".repeat(26));
    assertThat(page.getItems().getFirst().getComment()).isEqualTo("沟通评论");
    assertThat(page.isHasMore()).isFalse();
  }

  @Test void actualCommentWinsOverNewerAutomaticStageReason() {
    var stage = new ActivityFactResponse(); stage.setSourceType("STAGE"); stage.setComment("已进入面试");
    var interview = new ActivityFactResponse(); interview.setSourceType("INTERVIEW_SCHEDULE"); interview.setComment("候选人确认线上沟通");
    assertThat(ActivityRecordManager.comment(List.of(stage, interview))).isEqualTo("候选人确认线上沟通");
    interview.setComment(null);
    assertThat(ActivityRecordManager.comment(List.of(stage, interview))).isNull();
    assertThat(ActivityRecordManager.comment(List.of(stage))).isEqualTo("已进入面试");
  }

  @Test void sharedHistoryIncludesInterviewAndRestrictionFactsWithoutExtraPermissions() {
    var library = mock(CandidateLibraryQueryService.class);
    ReflectionTestUtils.setField(manager, "candidateLibrary", library);
    when(library.eligibleCandidateIds(List.of("candidate"))).thenReturn(Set.of("candidate"));
    when(origins.findRecords(any())).thenReturn(List.of(
        row("3".repeat(26), "INTERVIEW_FEEDBACK", "interview"),
        row("2".repeat(26), "RESTRICTION", null)));
    var page = manager.records("application", null, null, 20);
    assertThat(page.getItems()).extracting(value -> value.getId())
        .containsExactly("3".repeat(26), "2".repeat(26));
    when(library.eligibleCandidateIds(List.of("candidate"))).thenReturn(Set.of());
    assertThat(manager.records("application", null, null, 20).getItems()).isEmpty();
  }

  @Test void databaseEpochRetainsMicrosecondsWithoutRelabelingLocalTimeAsUtc() {
    var expected = Instant.parse("2026-09-14T07:14:56.271154Z");
    var record = row("1".repeat(26), "COMMUNICATION", null);
    record.setOccurredAt(expected);
    record.setFactsJson(record.getFactsJson().replace("\"1970-01-01T00:00:00Z\"", "1789370096.271154"));
    when(origins.findRecords(any())).thenReturn(List.of(record));

    var result = manager.records("application", null, null, 20).getItems().getFirst();
    assertThat(result.getFacts().getFirst().getOccurredAt()).isEqualTo(expected);
    assertThat(result.getFacts().getFirst().getOccurredAt()).isEqualTo(result.getOccurredAt());
  }

  private static ActivityRecordRowDTO row(String id, String source, String interviewId) {
    var row = new ActivityRecordRowDTO(); row.setId(id); row.setType("PENDING"); row.setTitle("实际沟通");
    row.setOccurredAt(Instant.EPOCH);
    row.setFactsJson("[{\"sourceType\":\"" + source + "\",\"sourceId\":\"" + id
        + "\",\"comment\":\"沟通评论\",\"occurredAt\":\"1970-01-01T00:00:00Z\",\"interviewId\":"
        + (interviewId == null ? "null" : "\"" + interviewId + "\"") + ",\"details\":{}}]");
    return row;
  }
}
