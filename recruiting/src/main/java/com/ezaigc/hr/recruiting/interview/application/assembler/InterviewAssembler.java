package com.ezaigc.hr.recruiting.interview.application.assembler;

import static com.ezaigc.hr.common.web.version.ResourceVersion.format;
import static com.ezaigc.hr.common.web.version.ResourceVersion.parse;

import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.recruiting.interview.domain.dto.CreateInterviewDTO;
import com.ezaigc.hr.recruiting.interview.domain.dto.InterviewFeedbackDTO;
import com.ezaigc.hr.recruiting.interview.domain.dto.InterviewExternalCalendarDTO;
import com.ezaigc.hr.recruiting.interview.domain.dto.RescheduleInterviewDTO;
import com.ezaigc.hr.recruiting.interview.domain.dto.UpdateInterviewDTO;
import com.ezaigc.hr.recruiting.interview.domain.entity.Feedback;
import com.ezaigc.hr.recruiting.interview.domain.entity.FeedbackVersion;
import com.ezaigc.hr.recruiting.interview.domain.entity.FeedbackView;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewJobOption;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewJobOptionResponse;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewDetails;
import com.ezaigc.hr.recruiting.interview.domain.entity.ScheduleHistory;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewTask;
import com.ezaigc.hr.recruiting.interview.domain.entity.NotificationIntent;
import com.ezaigc.hr.recruiting.interview.domain.request.InterviewCreateRequest;
import com.ezaigc.hr.recruiting.interview.domain.request.InterviewExternalCalendarRequest;
import com.ezaigc.hr.recruiting.interview.domain.request.InterviewFeedbackRequest;
import com.ezaigc.hr.recruiting.interview.domain.request.InterviewRescheduleRequest;
import com.ezaigc.hr.recruiting.interview.domain.request.InterviewUpdateRequest;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewDetailsResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewFeedbackResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewFeedbackSummaryResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewFeedbackVersionResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewNotificationResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewParticipantResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewScheduleHistoryResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewTaskResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/** 面试边界对象转换器，负责 DTO、领域对象与响应对象之间的转换。 */
@Component
public class InterviewAssembler {
  public InterviewJobOptionResponse toResponse(InterviewJobOption value) {
    return new InterviewJobOptionResponse(value.getJdId(),value.getJdVersionId(),
        value.getJdVersionNo(),value.getTitle());
  }
  public CreateInterviewDTO toDTO(InterviewCreateRequest v) {
    var result = new CreateInterviewDTO(
        v.getApplicationId(),
        v.getWorkflowStepId(),
        v.getStartAt(),
        v.getEndAt(),
        v.getTimezoneId(),
        v.getMeetingMode(),
        v.getLocationText(),
        v.getMeetingUrl(),
        v.getParticipantUserIds(),
        v.getCandidateNotificationChannel(),
        v.getClientSubmissionId(),
        v.getReason());
    result.setInterviewType(v.getInterviewType());
    result.setInitialComment(v.getInitialComment());
    result.setExternalCalendar(toDTO(v.getExternalCalendar()));
    return result;
  }

  public UpdateInterviewDTO toDTO(InterviewUpdateRequest v) {
    var result = new UpdateInterviewDTO(
        v.getStartAt(),
        v.getEndAt(),
        v.getTimezoneId(),
        v.getMeetingMode(),
        v.getLocationText(),
        v.getMeetingUrl(),
        v.getParticipantUserIds(),
        v.getCandidateNotificationChannel(),
        v.getNextResponsibleUserId(),
        parse(v.getVersion()),
        v.getReason());
    result.setExternalCalendar(toDTO(v.getExternalCalendar()));
    return result;
  }

  private InterviewExternalCalendarDTO toDTO(InterviewExternalCalendarRequest value) {
    if (value == null) return null;
    return new InterviewExternalCalendarDTO(
        value.getMode(), value.getTitle(), value.getDescription(), value.getAdminUserId(),
        value.getAdditionalInviteeUserIds(), value.getReminderMinutes(),
        value.getWaitingRoomEnabled(), value.getAllowEnterBeforeHost(), value.getEnterMuteEnabled());
  }

  public RescheduleInterviewDTO toDTO(InterviewRescheduleRequest v) {
    return new RescheduleInterviewDTO(
        v.getStartAt(), v.getEndAt(), v.getTimezoneId(), parse(v.getVersion()), v.getReason());
  }

  public InterviewFeedbackDTO toDTO(InterviewFeedbackRequest v) {
    return new InterviewFeedbackDTO(
        v.getVerdict(),
        v.getShortComment(),
        v.getExternalFeedbackSource(),
        v.getFeedbackOccurredAt(),
        v.getStatus(),
        v.getChangeReason());
  }

  public InterviewResponse toResponse(Interview v) {
    var result = new InterviewResponse(
        v.getId(),
        v.getApplicationId(),
        v.getCandidateId(),
        v.getJdId(),
        v.getJdVersionId(),
        v.getWorkflowTemplateVersionId(),
        v.getWorkflowStepId(),
        v.getStepCode(),
        v.getInterviewType(),
        v.getRoundNo(),
        v.getClientSubmissionId(),
        v.getStatus(),
        v.getScheduledStartAt(),
        v.getScheduledEndAt(),
        v.getTimezoneId(),
        v.getMeetingMode(),
        v.getLocationText(),
        v.getMeetingUrl(),
        v.getCandidateNotificationChannel(),
        v.getCandidateConfirmationStatus(),
        v.getOwnerUserId(),
        v.getOwnerDepartmentId(),
        v.getNextResponsibleUserId(),
        format(v.getVersion()),
        v.getCreateId(),
        v.getCreateTime(),
        v.getUpdateId(),
        v.getUpdateTime());
    result.setInitialComment(v.getInitialComment());
    return result;
  }

  public CursorPage<InterviewResponse> toResponse(CursorPage<Interview> v) {
    return v.map(this::toResponse);
  }

  public InterviewFeedbackResponse toResponse(FeedbackView v) {
    Feedback f = v.getFeedback();
    FeedbackVersion x = v.getCurrentVersion();
    return new InterviewFeedbackResponse(
        new InterviewFeedbackSummaryResponse(
            f.getId(),
            f.getInterviewId(),
            f.getAuthorUserId(),
            f.getFeedbackKind(),
            f.getStatus(),
            f.getCurrentVersionNo(),
            f.getCreateTime(),
            f.getUpdateTime()),
        new InterviewFeedbackVersionResponse(
            x.getId(),
            x.getFeedbackId(),
            x.getVersionNo(),
            x.getVerdict(),
            x.getShortComment(),
            x.getExternalFeedbackSource(),
            x.getFeedbackOccurredAt(),
            x.getChangeReason(),
            x.getCreateId(),
            x.getCreateTime()));
  }

  public InterviewNotificationResponse toResponse(NotificationIntent v) {
    return new InterviewNotificationResponse(
        v.getId(),
        v.getInterviewId(),
        v.getNotificationType(),
        v.getRecipientType(),
        v.getRecipientReference(),
        v.getChannel(),
        v.getTemplateVersionId(),
        v.getRenderedSubject(),
        v.getRenderedBody(),
        v.getStatus(),
        v.getExternalMessageId(),
        v.getLastErrorCode(),
        v.getLastErrorDetail(),
        v.getRetryCount(),
        format(v.getVersion()),
        v.getCreateTime(),
        v.getUpdateTime());
  }

  public InterviewDetailsResponse toResponse(InterviewDetails v) {
    return new InterviewDetailsResponse(
        toResponse(v.getInterview()),
        v.getParticipants().stream()
            .map(
                x ->
                    new InterviewParticipantResponse(
                        x.getInterviewId(),
                        x.getUserId(),
                        x.getParticipantRole(),
                        x.getResponseStatus(),
                        x.getCreateTime()))
            .toList());
  }

  public InterviewScheduleHistoryResponse toResponse(ScheduleHistory value) {
    return new InterviewScheduleHistoryResponse(
        value.getId(), value.getInterviewId(), value.getActionType(), value.getBeforeSnapshot(),
        value.getAfterSnapshot(), value.getReason(), value.getOperatedByUserId(), value.getOccurredAt());
  }

  public List<InterviewTaskResponse> toTaskResponses(List<InterviewTask> v) {
    return v.stream()
        .map(
            x ->
                new InterviewTaskResponse(
                    x.getInterviewId(),
                    x.getApplicationId(),
                    x.getCandidateId(),
                    x.getJdId(),
                    x.getStepCode(),
                    x.getScheduledStartAt(),
                    x.getScheduledEndAt(),
                    x.getTimezoneId(),
                    x.getMeetingMode(),
                    x.getLocationText(),
                    x.getMeetingUrl(),
                    x.getFeedbackDueAt(),
                    x.isOverdue(),
                    null,
                    x.isSubmitted()))
        .toList();
  }
}
