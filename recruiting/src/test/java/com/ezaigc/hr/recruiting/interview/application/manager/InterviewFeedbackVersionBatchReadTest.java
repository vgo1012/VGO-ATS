package com.ezaigc.hr.recruiting.interview.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.interview.domain.entity.Feedback;
import com.ezaigc.hr.recruiting.interview.domain.entity.FeedbackVersion;
import com.ezaigc.hr.recruiting.interview.domain.entity.FeedbackView;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.mapper.FeedbackVersionMapper;
import com.ezaigc.hr.recruiting.interview.service.InterviewFeedbackPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.NotificationIntentPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ParticipantPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ScheduleHistoryPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.impl.FeedbackVersionPersistenceServiceImpl;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class InterviewFeedbackVersionBatchReadTest {
  private static final String ACTOR = "A".repeat(26);
  private static final String INTERVIEW = "I".repeat(26);
  private final InterviewApplicationManager manager = new InterviewApplicationManager();
  private final InterviewPersistenceService interviews = mock(InterviewPersistenceService.class);
  private final InterviewFeedbackPersistenceService feedback = mock(InterviewFeedbackPersistenceService.class);
  private final FeedbackVersionMapper mapper = mock(FeedbackVersionMapper.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);

  @BeforeEach
  void setUp() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), FeedbackVersion.class);
    FeedbackVersionPersistenceServiceImpl versions = new FeedbackVersionPersistenceServiceImpl();
    ReflectionTestUtils.setField(versions, "baseMapper", mapper);
    ReflectionTestUtils.setField(manager, "feedbackVersionPersistenceService", versions);
    ReflectionTestUtils.setField(manager, "interviewFeedbackPersistenceService", feedback);
    ReflectionTestUtils.setField(manager, "persistence", interviews);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(manager, "cursorCodec", new SignedCursorCodec("interview-feedback-page-test-key-32-bytes"));
    ReflectionTestUtils.setField(manager, "participantPersistenceService", mock(ParticipantPersistenceService.class));
    ReflectionTestUtils.setField(manager, "scheduleHistoryPersistenceService", mock(ScheduleHistoryPersistenceService.class));
    ReflectionTestUtils.setField(manager, "notificationIntentPersistenceService", mock(NotificationIntentPersistenceService.class));
    CurrentActorSnapshot actor = mock(CurrentActorSnapshot.class);
    when(actor.getUserId()).thenReturn(ACTOR);
    when(authorization.currentActor()).thenReturn(actor);
    when(authorization.canAccess(any(), anyString(), any())).thenReturn(true);
    Interview interview = new Interview();
    interview.setId(INTERVIEW);
    when(interviews.findInterview(INTERVIEW)).thenReturn(interview);
  }

  @ParameterizedTest
  @ValueSource(ints = {50, 100})
  void visibleFeedbackRetainsOrderAndCurrentVersionsAcrossBoundedQueries(int limit) {
    List<Feedback> rows = new ArrayList<>();
    Map<String, FeedbackVersion> versions = new LinkedHashMap<>();
    for (int index = 0; index < 403; index++) {
      String id = String.format("%026d", index);
      boolean draft = index < 3;
      Feedback value = new Feedback(id, INTERVIEW, index == 0 ? ACTOR : "B" + id.substring(1),
          "INTERNAL", draft ? "DRAFT" : "SUBMITTED", 1 + index % 4);
      value.setCreateTime(Instant.EPOCH);
      rows.add(value);
      versions.put(id, new FeedbackVersion("V" + id.substring(1), id, value.getCurrentVersionNo(),
          "PASS", "面评 " + index, null, Instant.EPOCH, "保存"));
    }
    List<Feedback> visible = rows.reversed().stream().filter(value -> value.getAuthorUserId().equals(ACTOR)
        || value.getStatus().equals("SUBMITTED")).toList();
    when(feedback.findVisibleFeedback(anyString(), anyString(), nullable(Instant.class), nullable(String.class), anyInt()))
        .thenAnswer(call -> {
          String beforeId = call.getArgument(3);
          int fetchLimit = call.getArgument(4);
          assertThat(fetchLimit).isEqualTo(limit + 1);
          return visible.stream().filter(value -> beforeId == null || value.getId().compareTo(beforeId) < 0)
              .limit(fetchLimit).toList();
        });
    when(mapper.selectOne(any())).thenAnswer(call -> {
      LambdaQueryWrapper<FeedbackVersion> query = call.getArgument(0);
      query.getSqlSegment();
      return query.getParamNameValuePairs().values().stream().filter(versions::containsKey)
          .map(versions::get).findFirst().orElse(null);
    });
    List<Set<String>> batches = new ArrayList<>();
    when(mapper.selectList(any())).thenAnswer(call -> {
      LambdaQueryWrapper<FeedbackVersion> query = call.getArgument(0);
      query.getSqlSegment();
      Set<String> requested = query.getParamNameValuePairs().values().stream()
          .filter(versions::containsKey).map(String.class::cast).collect(Collectors.toSet());
      batches.add(requested);
      return requested.stream().map(versions::get).toList().reversed();
    });

    List<FeedbackView> actual = new ArrayList<>();
    String cursor = null;
    do {
      CursorPage<FeedbackView> page = manager.feedback(INTERVIEW, cursor, limit);
      assertThat(page.getItems()).hasSizeLessThanOrEqualTo(limit);
      actual.addAll(page.getItems());
      cursor = page.getNextCursor();
      assertThat(page.isHasMore()).isEqualTo(cursor != null);
    } while (cursor != null);
    assertThat(actual).extracting(FeedbackView::getFeedback).containsExactlyElementsOf(visible);
    assertThat(actual).allSatisfy(view -> assertThat(view.getCurrentVersion()).isSameAs(versions.get(view.getFeedback().getId())));
    verify(mapper, never()).selectOne(any());
    verify(mapper, times((visible.size() + limit - 1) / limit)).selectList(any());
    assertThat(batches).allSatisfy(batch -> assertThat(batch).hasSizeLessThanOrEqualTo(limit));
    assertThat(batches.stream().flatMap(Set::stream).toList()).containsExactlyInAnyOrderElementsOf(
        visible.stream().map(Feedback::getId).toList());
  }

  @ParameterizedTest
  @ValueSource(ints = {50, 100})
  void noVisibleFeedbackDoesNotTriggerVersionRead(int limit) {
    assertThat(manager.feedback(INTERVIEW, null, limit).getItems()).isEmpty();
    verifyNoInteractions(mapper);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void deniedInterviewDoesNotReadFeedbackOrVersions(boolean details) {
    when(authorization.canAccess(any(), anyString(), any())).thenReturn(false);
    assertThatThrownBy(() -> { if (details) manager.get(INTERVIEW); else manager.feedback(INTERVIEW, null, 50); })
        .isInstanceOfSatisfying(PlatformException.class,
        error -> assertThat(error.code()).isEqualTo("INTERVIEW_ACCESS_FORBIDDEN"));
    verifyNoInteractions(feedback, mapper);
  }

}
