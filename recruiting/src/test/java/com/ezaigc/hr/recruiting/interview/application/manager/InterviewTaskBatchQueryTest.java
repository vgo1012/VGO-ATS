package com.ezaigc.hr.recruiting.interview.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.recruiting.interview.application.assembler.InterviewAssembler;
import com.ezaigc.hr.talent.api.CandidateDirectoryService;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.interview.domain.entity.Feedback;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.mapper.FeedbackMapper;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.impl.InterviewFeedbackPersistenceServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InterviewTaskBatchQueryTest {
  private static final String ACTOR = "A".repeat(26);
  private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");
  private final InterviewApplicationManager manager = new InterviewApplicationManager();
  private final InterviewPersistenceService interviews = mock(InterviewPersistenceService.class);
  private final CandidateDirectoryService candidates = mock(CandidateDirectoryService.class);
  private final FeedbackMapper mapper = mock(FeedbackMapper.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);

  @BeforeEach
  void setUp() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Feedback.class);
    var feedback = new InterviewFeedbackPersistenceServiceImpl();
    ReflectionTestUtils.setField(feedback, "baseMapper", mapper);
    ReflectionTestUtils.setField(manager, "interviewFeedbackPersistenceService", feedback);
    ReflectionTestUtils.setField(manager, "persistence", interviews);
    ReflectionTestUtils.setField(manager, "candidates", candidates);
    ReflectionTestUtils.setField(manager, "assembler", new InterviewAssembler());
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    CurrentActorSnapshot actor = mock(CurrentActorSnapshot.class);
    when(actor.getUserId()).thenReturn(ACTOR);
    when(authorization.currentActor()).thenReturn(actor);
  }

  @Test
  void fullPagePreservesTaskStateAndOrderWithOneFeedbackQuery() {
    List<Interview> page = new ArrayList<>();
    Map<String, Feedback> feedbackByInterview = new HashMap<>();
    for (int index = 0; index < 200; index++) {
      Interview interview = new Interview();
      interview.setId(String.format("%026d", index));
      interview.setCandidateId("candidate");
      interview.setScheduledEndAt(NOW.minus(index % 2 == 0 ? 48 : 12, ChronoUnit.HOURS));
      page.add(interview);
      if (index % 3 != 2) {
        feedbackByInterview.put(interview.getId(), new Feedback("F" + interview.getId().substring(1),
            interview.getId(), ACTOR, "INTERNAL", index % 3 == 0 ? "SUBMITTED" : "DRAFT", 1));
      }
    }
    when(interviews.findMyInterviewTasks(ACTOR, 200)).thenReturn(page);
    when(mapper.selectOne(any())).thenAnswer(call -> {
      LambdaQueryWrapper<Feedback> query = call.getArgument(0);
      query.getSqlSegment();
      return query.getParamNameValuePairs().values().stream()
          .filter(feedbackByInterview::containsKey).map(feedbackByInterview::get).findFirst().orElse(null);
    });
    when(mapper.selectList(any())).thenReturn(feedbackByInterview.values().stream()
        .filter(value -> "SUBMITTED".equals(value.getStatus())).toList());

    when(candidates.namesForAuthorizedCandidates(Set.of("candidate"))).thenReturn(Map.of("candidate", "任务候选人"));
    var tasks = manager.myTaskResponses(1000);
    verify(candidates).namesForAuthorizedCandidates(Set.of("candidate"));

    assertThat(tasks).hasSize(200);
    for (int index = 0; index < tasks.size(); index++) {
      var task = tasks.get(index);
      assertThat(task.getCandidateName()).isEqualTo("任务候选人");
      assertThat(task.getInterviewId()).isEqualTo(page.get(index).getId());
      assertThat(task.isSubmitted()).isEqualTo(index % 3 == 0);
      assertThat(task.isOverdue()).isEqualTo(index % 3 != 0 && index % 2 == 0);
      assertThat(task.getFeedbackDueAt()).isEqualTo(page.get(index).getScheduledEndAt().plus(24, ChronoUnit.HOURS));
    }
    verify(authorization).requirePermission("recruitment.manage");
    verify(mapper, never()).selectOne(any());
    verify(mapper).selectList(any());
  }

  @Test
  void emptyTaskPageDoesNotQueryFeedback() {
    when(interviews.findMyInterviewTasks(ACTOR, 200)).thenReturn(List.of());
    assertThat(manager.myTaskResponses(200)).isEmpty();
    verifyNoInteractions(mapper, candidates);
  }
}
