package com.ezaigc.hr.recruiting.interview.service;

import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.MAX_MY_INTERVIEW_TASKS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ezaigc.hr.recruiting.interview.domain.entity.Feedback;
import com.ezaigc.hr.recruiting.interview.mapper.FeedbackMapper;
import com.ezaigc.hr.recruiting.interview.service.impl.InterviewFeedbackPersistenceServiceImpl;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.time.Instant;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class InterviewFeedbackBatchQueryTest {
  private static final String AUTHOR = "A".repeat(26);
  private final FeedbackMapper mapper = mock(FeedbackMapper.class);
  private final InterviewFeedbackPersistenceServiceImpl service = new InterviewFeedbackPersistenceServiceImpl();

  @BeforeEach
  void setUp() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Feedback.class);
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void maximumPageUsesParameterizedAuthorKindStatusAndExactInterviewIds() {
    Set<String> ids = ids(MAX_MY_INTERVIEW_TASKS);
    String submittedId = ids.iterator().next();
    Feedback submitted = new Feedback();
    submitted.setInterviewId(submittedId);
    when(mapper.selectList(any())).thenReturn(List.of(submitted));

    assertThat(service.findSubmittedInterviewIds(ids, AUTHOR)).containsExactly(submittedId);

    ArgumentCaptor<LambdaQueryWrapper<Feedback>> queryCaptor = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectList(queryCaptor.capture());
    var query = queryCaptor.getValue();
    assertThat(query.getSqlSelect()).isEqualTo("interview_id");
    assertThat(query.getSqlSegment())
        .contains("interview_id IN", "author_user_id =", "feedback_kind =", "status =")
        .doesNotContain(AUTHOR, submittedId, "SUBMITTED", "INTERNAL", " OR ");
    Set<Object> expected = new HashSet<>(ids);
    expected.addAll(List.of(AUTHOR, "INTERNAL", "SUBMITTED"));
    assertThat(query.getParamNameValuePairs().values()).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void emptyOrInvalidBatchesNeverOpenADataSession() {
    assertThat(service.findSubmittedInterviewIds(Set.of(), AUTHOR)).isEmpty();
    Set<String> nullEntry = new HashSet<>();
    nullEntry.add(null);
    assertThatThrownBy(() -> service.findSubmittedInterviewIds(null, AUTHOR)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.findSubmittedInterviewIds(nullEntry, AUTHOR)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.findSubmittedInterviewIds(Set.of(" "), AUTHOR)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.findSubmittedInterviewIds(ids(MAX_MY_INTERVIEW_TASKS + 1), AUTHOR))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.findSubmittedInterviewIds(Set.of("I".repeat(26)), null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.findSubmittedInterviewIds(Set.of("I".repeat(26)), " ")).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(mapper);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void visibleBatchUsesNoCountLimitPlusOneAndFiltersDraftsBeforePaging() {
    Set<String> ids = ids(20);
    when(mapper.selectPage(any(), any())).thenReturn(new Page<>());
    Instant cursorTime = Instant.parse("2026-01-02T03:04:05Z");
    String cursorId = "Z".repeat(26);

    assertThat(service.findVisibleFeedbackByInterviewIds(ids, AUTHOR, cursorTime, cursorId, 101))
        .isEmpty();

    ArgumentCaptor<IPage<Feedback>> pageCaptor = ArgumentCaptor.forClass((Class) IPage.class);
    ArgumentCaptor<LambdaQueryWrapper<Feedback>> queryCaptor =
        ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectPage(pageCaptor.capture(), queryCaptor.capture());
    assertThat(pageCaptor.getValue().getSize()).isEqualTo(101);
    assertThat(pageCaptor.getValue().searchCount()).isFalse();
    var query = queryCaptor.getValue();
    assertThat(query.getSqlSegment())
        .contains("interview_id IN", "status =", "author_user_id =", "create_time <", "id <")
        .contains("ORDER BY create_time DESC,id DESC")
        .doesNotContain(AUTHOR, cursorId, "SUBMITTED");
    assertThat(query.getParamNameValuePairs().values())
        .containsAll(ids)
        .contains(AUTHOR, "SUBMITTED", cursorId)
        .filteredOn(cursorTime::equals)
        .hasSize(2);
  }

  private static Set<String> ids(int count) {
    return IntStream.range(0, count).mapToObj(index -> String.format("%026d", index)).collect(Collectors.toSet());
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void sharedDraftVisibilityIsRestrictedToTheExplicitSharedInterviewSubsetBeforePaging() {
    when(mapper.selectPage(any(), any())).thenReturn(new Page<>());
    service.findVisibleFeedbackByInterviewIds(Set.of("shared", "private"), AUTHOR,
        Set.of("shared"), null, null, 10);
    ArgumentCaptor<LambdaQueryWrapper<Feedback>> queryCaptor = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectPage(any(), queryCaptor.capture());
    var query = queryCaptor.getValue();
    assertThat(query.getSqlSegment()).contains("OR interview_id IN", "status =", "author_user_id =");
    assertThat(query.getParamNameValuePairs().values()).filteredOn("shared"::equals).hasSize(2);
    assertThat(query.getParamNameValuePairs().values()).filteredOn("private"::equals).hasSize(1);
  }
}
