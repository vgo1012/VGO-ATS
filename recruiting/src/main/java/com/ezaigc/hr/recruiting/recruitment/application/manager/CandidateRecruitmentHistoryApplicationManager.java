package com.ezaigc.hr.recruiting.recruitment.application.manager;

import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.MAX_FEEDBACK_VERSIONS_PER_BATCH;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ezaigc.hr.recruiting.interview.domain.entity.Feedback;
import com.ezaigc.hr.recruiting.interview.domain.entity.FeedbackVersion;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.enums.InterviewFeedbackStatus;
import com.ezaigc.hr.recruiting.interview.service.FeedbackVersionPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.InterviewFeedbackPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OwnershipHistory;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.StageHistory;
import com.ezaigc.hr.recruiting.recruitment.domain.response.CandidateRecruitmentHistoryResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.CandidateRecruitmentHistoryResponse.ApplicationHistory;
import com.ezaigc.hr.recruiting.recruitment.domain.response.CandidateRecruitmentHistoryResponse.FeedbackHistory;
import com.ezaigc.hr.recruiting.recruitment.domain.response.CandidateRecruitmentHistoryResponse.InterviewHistory;
import com.ezaigc.hr.recruiting.recruitment.domain.response.CandidateRecruitmentHistoryResponse.OwnershipEvent;
import com.ezaigc.hr.recruiting.recruitment.domain.response.CandidateRecruitmentHistoryResponse.StageEvent;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentOwnershipHistoryPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.StageHistoryPersistenceService;
import com.ezaigc.hr.talent.api.CandidateAccessPolicy;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 候选人详情使用的事实型招聘历史；不从当前阶段反推面试或面评。 */
@Service
public class CandidateRecruitmentHistoryApplicationManager {
  @Resource private CandidateAccessPolicy candidateAccess;
  @Resource private ApplicationPersistenceService applications;
  @Resource private StageHistoryPersistenceService stages;
  @Resource private RecruitmentOwnershipHistoryPersistenceService ownerships;
  @Resource private InterviewPersistenceService interviews;
  @Resource private InterviewFeedbackPersistenceService feedbacks;
  @Resource private FeedbackVersionPersistenceService feedbackVersions;

  @Transactional(readOnly = true)
  public CandidateRecruitmentHistoryResponse history(String candidateId) {
    candidateAccess.requireAllResumesReadable(candidateId);
    var rows = applications.findForCandidates(List.of(candidateId));
    if (rows.isEmpty()) return new CandidateRecruitmentHistoryResponse(List.of());
    var visible = rows.stream()
        .sorted(Comparator.comparing(value -> value.getCreateTime(), Comparator.reverseOrder()))
        .toList();
    Set<String> visibleApplicationIds = visible.stream().map(value -> value.getId()).collect(Collectors.toSet());
    List<Interview> interviewRows = interviews.list(Wrappers.lambdaQuery(Interview.class)
        .in(Interview::getApplicationId, visibleApplicationIds)
        .orderByAsc(Interview::getScheduledStartAt, Interview::getId));
    Set<String> interviewIds = interviewRows.stream().map(Interview::getId).collect(Collectors.toSet());
    List<Feedback> feedbackRows = interviewIds.isEmpty() ? List.of() : feedbacks.list(
        Wrappers.lambdaQuery(Feedback.class)
            .in(Feedback::getInterviewId, interviewIds)
            .eq(Feedback::getStatus, InterviewFeedbackStatus.SUBMITTED.getCode())
            .orderByAsc(Feedback::getCreateTime, Feedback::getId));
    var versionMap = loadFeedbackVersions(feedbackRows);
    Map<String, List<Feedback>> feedbackByInterview = feedbackRows.stream()
        .collect(Collectors.groupingBy(Feedback::getInterviewId));
    Map<String, List<Interview>> interviewsByApplication = interviewRows.stream()
        .collect(Collectors.groupingBy(Interview::getApplicationId));
    Map<String, List<OwnershipHistory>> ownershipsByApplication = ownerships.list(
        Wrappers.lambdaQuery(OwnershipHistory.class)
            .in(OwnershipHistory::getApplicationId, visibleApplicationIds)
            .orderByAsc(OwnershipHistory::getCreateTime, OwnershipHistory::getId)).stream()
        .collect(Collectors.groupingBy(OwnershipHistory::getApplicationId));
    Map<String, List<StageHistory>> stagesByApplication = stages.list(
        Wrappers.lambdaQuery(StageHistory.class)
            .in(StageHistory::getApplicationId, visibleApplicationIds)
            .orderByAsc(StageHistory::getOccurredAt, StageHistory::getId)).stream()
        .collect(Collectors.groupingBy(StageHistory::getApplicationId));
    List<ApplicationHistory> result = new ArrayList<>();
    for (var application : visible) {
      var ownershipRows = ownershipsByApplication.getOrDefault(application.getId(), List.of());
      String originalOwner = ownershipRows.isEmpty()
          ? application.getOwnerUserId()
          : ownershipRows.getFirst().getToUserId();
      var stageEvents = stagesByApplication.getOrDefault(application.getId(), List.of()).stream()
          .map(value -> new StageEvent(value.getFromStage(), value.getToStage(), value.getReason(),
              value.getOperatedByUserId(), value.getOccurredAt()))
          .toList();
      var ownershipEvents = ownershipRows.stream()
          .map(value -> new OwnershipEvent(value.getActionType(), value.getFromUserId(), value.getToUserId(),
              value.getReason(), value.getOperatedByUserId(), value.getCreateTime()))
          .toList();
      var interviewHistory = interviewsByApplication.getOrDefault(application.getId(), List.of()).stream()
          .map(interview -> new InterviewHistory(interview.getId(), interview.getRoundNo(),
              interview.getInterviewType(), interview.getStatus(), interview.getScheduledStartAt(),
              interview.getScheduledEndAt(), interview.getOwnerUserId(),
              feedbackByInterview.getOrDefault(interview.getId(), List.of()).stream().map(feedback -> {
                var version = versionMap.get(feedback.getId());
                return new FeedbackHistory(feedback.getId(), feedback.getAuthorUserId(), feedback.getFeedbackKind(),
                    version == null ? null : version.getVerdict(),
                    version == null ? null : version.getShortComment(),
                    version == null ? null : version.getFeedbackOccurredAt());
              }).toList()))
          .toList();
      result.add(new ApplicationHistory(application.getId(), application.getJdId(), application.getJdVersionId(),
          application.getResumeVersionId(), application.getRecruitmentType(), application.getCurrentStage(),
          application.getOutcome(), originalOwner, application.getOwnerUserId(), application.getCreateTime(),
          application.getTerminalAt(), stageEvents, ownershipEvents, interviewHistory));
    }
    return new CandidateRecruitmentHistoryResponse(result);
  }

  private Map<String, FeedbackVersion> loadFeedbackVersions(List<Feedback> rows) {
    Map<String, FeedbackVersion> result = new HashMap<>();
    for (int offset = 0; offset < rows.size(); offset += MAX_FEEDBACK_VERSIONS_PER_BATCH) {
      var batch = rows.subList(offset, Math.min(offset + MAX_FEEDBACK_VERSIONS_PER_BATCH, rows.size()));
      var requested = batch.stream()
          .collect(Collectors.toMap(Feedback::getId, Feedback::getCurrentVersionNo));
      result.putAll(feedbackVersions.findFeedbackVersions(requested));
    }
    return result;
  }
}
