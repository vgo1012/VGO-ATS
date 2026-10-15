package com.ezaigc.hr.recruiting.recruitment.domain.response;

import java.time.Instant;
import java.util.List;
import lombok.Value;

@Value
public class CandidateRecruitmentHistoryResponse {
  List<ApplicationHistory> items;

  @Value
  public static class ApplicationHistory {
    String applicationId;
    String jdId;
    String jdVersionId;
    String resumeVersionId;
    String recruitmentType;
    String currentStage;
    String outcome;
    String originalOwnerUserId;
    String currentOwnerUserId;
    Instant createTime;
    Instant terminalAt;
    List<StageEvent> stageEvents;
    List<OwnershipEvent> ownershipEvents;
    List<InterviewHistory> interviews;
  }

  @Value
  public static class StageEvent {
    String fromStage;
    String toStage;
    String reason;
    String operatedByUserId;
    Instant occurredAt;
  }

  @Value
  public static class OwnershipEvent {
    String actionType;
    String fromUserId;
    String toUserId;
    String reason;
    String operatedByUserId;
    Instant occurredAt;
  }

  @Value
  public static class InterviewHistory {
    String interviewId;
    int roundNo;
    String interviewType;
    String status;
    Instant scheduledStartAt;
    Instant scheduledEndAt;
    String ownerUserId;
    List<FeedbackHistory> feedbacks;
  }

  @Value
  public static class FeedbackHistory {
    String feedbackId;
    String authorUserId;
    String feedbackKind;
    String verdict;
    String shortComment;
    Instant feedbackOccurredAt;
  }
}
