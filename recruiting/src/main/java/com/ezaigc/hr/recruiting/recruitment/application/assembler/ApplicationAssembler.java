package com.ezaigc.hr.recruiting.recruitment.application.assembler;

import static com.ezaigc.hr.common.web.version.ResourceVersion.format;

import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationCandidateFilterDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationCandidateFilterRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.CreateApplicationDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationBatchCreateDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.OfferCommandDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.ApplicationDetails;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Communication;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Decision;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.FollowUp;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Intention;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OfferStatusRecord;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OnboardingEvent;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OnboardingProcess;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkItem;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkflowStep;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationCreateRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationBatchCreateRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationOfferRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationCommunicationResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationDecisionResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationDetailsResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationFollowUpResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationIntentionResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationWorkflowStepResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.OfferStatusResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.OnboardingEventResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.OnboardingProcessResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.WorkItemResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** 应聘流程边界对象转换器，负责 DTO、领域对象与响应对象之间的转换。 */
@Component
public class ApplicationAssembler {
  public ApplicationCandidateFilterDTO toDTO(ApplicationCandidateFilterRequest request) {
    var query = CommonUtils.stripToNull(request.getQ());
    return new ApplicationCandidateFilterDTO(query == null ? null : query.toLowerCase(Locale.ROOT),
        request.getConditions().stream().map(String::strip).distinct().sorted().toList(),
        request.getDegrees().stream().map(String::strip).distinct().sorted().toList(),
        request.getMinWorkYears() == null ? null : request.getMinWorkYears().stripTrailingZeros(),
        request.getMaxWorkYears() == null ? null : request.getMaxWorkYears().stripTrailingZeros(),
        request.getMinAge(), request.getMaxAge(), request.getGender() == null ? null : request.getGender().toUpperCase(Locale.ROOT));
  }

  public ApplicationBatchCreateDTO toDTO(ApplicationBatchCreateRequest request) {
    return new ApplicationBatchCreateDTO(request.getItems().stream().map(item -> new CreateApplicationDTO(item.getCandidateId(),
        request.getJdVersionId(), item.getResumeVersionId(), request.getOwnerUserId(), request.getReason())).toList(), request.getSelectedStepCodes());
  }
  public CreateApplicationDTO toDTO(ApplicationCreateRequest v) {
    return new CreateApplicationDTO(
        v.getCandidateId(), v.getJdVersionId(), v.getResumeVersionId(), v.getOwnerUserId(), v.getReason());
  }

  public OfferCommandDTO toDTO(ApplicationOfferRequest v) {
    return new OfferCommandDTO(
        v.getStatus(),
        v.getTerminationReason(),
        v.getApplicationOutcome(),
        v.getReason(),
        v.getOccurredAt(),
        v.getSourceReference(),
        v.getSourceStatus());
  }

  public ApplicationResponse toResponse(Application v) {
    var response = new ApplicationResponse(
        v.getId(),
        v.getCandidateId(),
        v.getDemandId(),
        v.getJdId(),
        v.getJdVersionId(),
        v.getResumeVersionId(),
        v.getWorkflowTemplateVersionId(),
        v.getMatchPlanVersionId(),
        v.getRecruitmentType(),
        v.getCurrentStage(),
        v.getOutcome(),
        v.getOwnerUserId(),
        v.getOwnerDepartmentId(),
        v.getActiveSlot(),
        v.getTerminalAt(),
        format(v.getVersion()),
        v.getCreateId(),
        v.getCreateTime(),
        v.getUpdateId(),
        v.getUpdateTime());
    response.setProgressiveFollowUp(v.isProgressiveFollowUp());
    return response;
  }

  public CursorPage<ApplicationResponse> toResponse(CursorPage<Application> v) {
    return v.map(this::toResponse);
  }

  public ApplicationWorkflowStepResponse toResponse(WorkflowStep v) {
    var response = new ApplicationWorkflowStepResponse(
        v.getId(),
        v.getApplicationId(),
        v.getTemplateVersionId(),
        v.getStepDefinitionId(),
        v.getStepCode(),
        v.getSequenceNo(),
        v.isCandidateOptional(),
        v.getDefaultFollowUpChannel(),
        v.getStatus(),
        v.getOwnerUserId(),
        v.getDueAt(),
        v.getStartedAt(),
        v.getCompletedAt(),
        v.getResultReason(),
        v.getClientSubmissionId(),
        format(v.getVersion()),
        v.getCreateTime(),
        v.getUpdateTime());
    response.setRoundNo(v.getRoundNo());
    return response;
  }

  public List<ApplicationWorkflowStepResponse> toStepResponses(List<WorkflowStep> v) {
    return v.stream().map(this::toResponse).toList();
  }

  public ApplicationDecisionResponse toResponse(Decision v) {
    return new ApplicationDecisionResponse(
        v.getId(),
        v.getApplicationId(),
        v.getRecordType(),
        v.getDecision(),
        v.getReason(),
        v.getCorrectsRecordId(),
        v.getClientSubmissionId(),
        v.getDecidedByUserId(),
        v.getDecidedAt(),
        v.getCreateTime());
  }

  public ApplicationFollowUpResponse toResponse(FollowUp v) {
    return new ApplicationFollowUpResponse(
        v.getId(),
        v.getApplicationId(),
        v.getWorkflowStepId(),
        v.getChannel(),
        v.getSummary(),
        v.getNextPlan(),
        v.getContactedAt(),
        v.getRecordedByUserId(),
        v.getCreateTime());
  }

  public List<ApplicationFollowUpResponse> toFollowUpResponses(List<FollowUp> v) {
    return v.stream().map(this::toResponse).toList();
  }

  public ApplicationCommunicationResponse toResponse(Communication v) {
    return new ApplicationCommunicationResponse(
        v.getId(),
        v.getApplicationId(),
        v.getChannel(),
        v.getSummary(),
        v.getResult(),
        v.getNextPlan(),
        v.getCommunicatedAt(),
        v.getRecordedByUserId(),
        v.getCreateTime());
  }

  public List<ApplicationCommunicationResponse> toCommunicationResponses(List<Communication> v) {
    return v.stream().map(this::toResponse).toList();
  }

  public ApplicationIntentionResponse toResponse(Intention v) {
    return new ApplicationIntentionResponse(
        v.getId(),
        v.getCandidateId(),
        v.getApplicationId(),
        v.getExpectedSalaryMin(),
        v.getExpectedSalaryMax(),
        v.getSalaryPeriod(),
        v.getAvailability(),
        v.getIntentionLocations(),
        v.getReason(),
        v.getRecordedByUserId(),
        v.getCreateTime());
  }

  public List<ApplicationIntentionResponse> toIntentionResponses(List<Intention> v) {
    return v.stream().map(this::toResponse).toList();
  }

  public OfferStatusResponse toResponse(OfferStatusRecord v) {
    return new OfferStatusResponse(
        v.getId(),
        v.getApplicationId(),
        v.getRecordType(),
        v.getStatus(),
        v.getTerminationReason(),
        v.getApplicationOutcome(),
        v.getReason(),
        v.getCorrectsRecordId(),
        v.getReopensRecordId(),
        v.getSource(),
        v.getSourceReference(),
        v.getSourceStatus(),
        v.getOccurredAt(),
        v.getOperatedByUserId(),
        v.getCreateTime());
  }

  public List<OfferStatusResponse> toOfferResponses(List<OfferStatusRecord> v) {
    return v.stream().map(this::toResponse).toList();
  }

  public OnboardingEventResponse toResponse(OnboardingEvent v) {
    return new OnboardingEventResponse(
        v.getId(),
        v.getApplicationId(),
        v.getOnboardedAt(),
        v.getSource(),
        v.getSourceReference(),
        v.getReason(),
        v.getOperatedByUserId(),
        v.getCreateTime());
  }

  public OnboardingProcessResponse toResponse(OnboardingProcess value, long elapsedSeconds) {
    return new OnboardingProcessResponse(
        value.getApplicationId(),
        value.getStatus(),
        value.getStartedAt(),
        value.getActualOnboardedAt(),
        value.getClosedAt(),
        elapsedSeconds);
  }

  public ApplicationDetailsResponse toResponse(ApplicationDetails v) {
    return new ApplicationDetailsResponse(
        toResponse(v.getApplication()),
        v.getCollaboratorUserIds(),
        toStepResponses(v.getWorkflowSteps()),
        v.getDecisions().stream().map(this::toResponse).toList(),
        toOfferResponses(v.getOfferStatusRecords()),
        v.getElapsedSeconds());
  }

  public List<WorkItemResponse> toWorkItemResponses(List<WorkItem> v) {
    return v.stream()
        .map(
            x ->
                new WorkItemResponse(
                    x.getApplicationId(),
                    x.getCandidateId(),
                    x.getJdId(),
                    x.getJdVersionId(),
                    x.getStepId(),
                    x.getStepCode(),
                    x.getStepStatus(),
                    x.getOwnerUserId(),
                    x.getDueAt(),
                    x.isOverdue(),
                    x.getCurrentStage(),
                    x.getOutcome(),
                    x.getApplicationCreatedAt(),
                    x.getElapsedSeconds()))
        .toList();
  }

}
