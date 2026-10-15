package com.ezaigc.hr.recruiting.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.recruiting.interview.domain.entity.Feedback;
import com.ezaigc.hr.recruiting.interview.domain.entity.NotificationIntent;
import com.ezaigc.hr.recruiting.interview.domain.entity.ScheduleHistory;
import com.ezaigc.hr.recruiting.interview.mapper.FeedbackMapper;
import com.ezaigc.hr.recruiting.interview.mapper.NotificationIntentMapper;
import com.ezaigc.hr.recruiting.interview.mapper.ScheduleHistoryMapper;
import com.ezaigc.hr.recruiting.interview.service.impl.InterviewFeedbackPersistenceServiceImpl;
import com.ezaigc.hr.recruiting.interview.service.impl.NotificationIntentPersistenceServiceImpl;
import com.ezaigc.hr.recruiting.interview.service.impl.ScheduleHistoryPersistenceServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class InterviewHistoryPageQueryTest {
  private static final Instant TIME = Instant.parse("2026-09-07T01:00:00Z");
  private static final String ID = "I".repeat(26);
  private static final String CURSOR_ID = "C".repeat(26);
  private final FeedbackMapper feedback = mock(FeedbackMapper.class);
  private final NotificationIntentMapper notifications = mock(NotificationIntentMapper.class);
  private final ScheduleHistoryMapper history = mock(ScheduleHistoryMapper.class);
  private final InterviewFeedbackPersistenceServiceImpl feedbackService = new InterviewFeedbackPersistenceServiceImpl();
  private final NotificationIntentPersistenceServiceImpl notificationService = new NotificationIntentPersistenceServiceImpl();
  private final ScheduleHistoryPersistenceServiceImpl historyService = new ScheduleHistoryPersistenceServiceImpl();

  @BeforeEach
  void setUp() {
    for (Class<?> type : List.of(Feedback.class, NotificationIntent.class, ScheduleHistory.class)) {
      TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "page-test"), type);
    }
    ReflectionTestUtils.setField(feedbackService, "baseMapper", feedback);
    ReflectionTestUtils.setField(notificationService, "baseMapper", notifications);
    ReflectionTestUtils.setField(historyService, "baseMapper", history);
  }

  @Test
  void feedbackAppliesAuthorVisibilityInsideTheBoundedQuery() {
    String author = "' OR 1=1 --";
    LambdaQueryWrapper<Feedback> query = assertPage(feedback,
        () -> feedbackService.findVisibleFeedback(ID, author, TIME, CURSOR_ID, 101), "create_time");
    assertThat(query.getSqlSegment()).containsPattern("AND \\(status = #\\{[^}]+} OR author_user_id = #\\{[^}]+}\\)")
        .doesNotContain(author);
    assertThat(query.getParamNameValuePairs()).containsValues("SUBMITTED", author);
  }

  @Test
  void notificationsUseCreationTimeRatherThanMutableDeliveryTime() {
    assertPage(notifications, () -> notificationService.findNotifications(ID, TIME, CURSOR_ID, 101), "create_time");
  }

  @Test
  void scheduleHistoryKeepsEqualTimestampRowsWithTheIdTieBreaker() {
    assertPage(history, () -> historyService.findHistory(ID, TIME, CURSOR_ID, 101), "occurred_at");
  }

  @Test
  void invalidPhysicalFetchSizesAreRejectedBeforeDatabaseAccess() {
    for (int limit : List.of(0, -1, 102, Integer.MAX_VALUE)) {
      assertThatThrownBy(() -> feedbackService.findVisibleFeedback(ID, ID, null, null, limit)).isInstanceOf(PlatformException.class);
      assertThatThrownBy(() -> notificationService.findNotifications(ID, null, null, limit)).isInstanceOf(PlatformException.class);
      assertThatThrownBy(() -> historyService.findHistory(ID, null, null, limit)).isInstanceOf(PlatformException.class);
    }
    verifyNoInteractions(feedback, notifications, history);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <T> LambdaQueryWrapper<T> assertPage(BaseMapper<T> mapper, Supplier<List<T>> read, String timeColumn) {
    when(mapper.selectPage(any(Page.class), any())).thenAnswer(call -> call.getArgument(0));
    assertThat(read.get()).isEmpty();
    ArgumentCaptor<Page<T>> page = ArgumentCaptor.forClass((Class) Page.class);
    ArgumentCaptor<LambdaQueryWrapper<T>> query = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectPage(page.capture(), query.capture());
    assertThat(page.getValue().getSize()).isEqualTo(101);
    assertThat(page.getValue().getCurrent()).isOne();
    assertThat(page.getValue().searchCount()).isFalse();
    String sql = query.getValue().getSqlSegment();
    assertThat(sql).contains("interview_id =", "AND (" + timeColumn + " <", "OR (" + timeColumn + " =", "AND id <")
        .endsWith("ORDER BY " + timeColumn + " DESC,id DESC").doesNotContain("update_time", ID, CURSOR_ID);
    assertThat(query.getValue().getParamNameValuePairs()).containsValues(ID, TIME, CURSOR_ID);
    return query.getValue();
  }
}
