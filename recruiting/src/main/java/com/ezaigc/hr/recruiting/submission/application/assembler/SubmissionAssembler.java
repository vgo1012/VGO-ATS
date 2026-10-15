package com.ezaigc.hr.recruiting.submission.application.assembler;

import com.ezaigc.hr.recruiting.submission.domain.dto.ApplicationClientRestrictionDTO;
import com.ezaigc.hr.recruiting.submission.domain.request.ApplicationClientRestrictionRequest;
import static com.ezaigc.hr.common.web.version.ResourceVersion.format;
import static com.ezaigc.hr.common.web.version.ResourceVersion.parse;

import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.recruiting.submission.domain.dto.AcknowledgeSubmissionRiskDTO;
import com.ezaigc.hr.recruiting.submission.domain.dto.CorrectClientRestrictionDTO;
import com.ezaigc.hr.recruiting.submission.domain.dto.CreateSubmissionDTO;
import com.ezaigc.hr.recruiting.submission.domain.dto.DisableClientRestrictionDTO;
import com.ezaigc.hr.recruiting.submission.domain.dto.SendSubmissionDTO;
import com.ezaigc.hr.recruiting.submission.domain.dto.SubmissionFeedbackDTO;
import com.ezaigc.hr.recruiting.submission.domain.dto.WithdrawSubmissionDTO;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientRestriction;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientSubmission;
import com.ezaigc.hr.recruiting.submission.domain.entity.Eligibility;
import com.ezaigc.hr.recruiting.submission.domain.entity.Feedback;
import com.ezaigc.hr.recruiting.submission.domain.entity.RiskAcknowledgement;
import com.ezaigc.hr.recruiting.submission.domain.entity.SendSnapshot;
import com.ezaigc.hr.recruiting.submission.domain.entity.SubmissionDetails;
import com.ezaigc.hr.recruiting.submission.domain.request.SubmissionCreateRequest;
import com.ezaigc.hr.recruiting.submission.domain.request.SubmissionDisableRequest;
import com.ezaigc.hr.recruiting.submission.domain.request.SubmissionFeedbackRequest;
import com.ezaigc.hr.recruiting.submission.domain.request.SubmissionRestrictionRequest;
import com.ezaigc.hr.recruiting.submission.domain.request.SubmissionRiskRequest;
import com.ezaigc.hr.recruiting.submission.domain.request.SubmissionSendRequest;
import com.ezaigc.hr.recruiting.submission.domain.request.SubmissionWithdrawRequest;
import com.ezaigc.hr.recruiting.submission.domain.response.ClientRestrictionResponse;
import com.ezaigc.hr.recruiting.submission.domain.response.ClientSubmissionResponse;
import com.ezaigc.hr.recruiting.submission.domain.response.EligibilityResponse;
import com.ezaigc.hr.recruiting.submission.domain.response.FeedbackResponse;
import com.ezaigc.hr.recruiting.submission.domain.response.RiskAcknowledgementResponse;
import com.ezaigc.hr.recruiting.submission.domain.response.SendSnapshotResponse;
import com.ezaigc.hr.recruiting.submission.domain.response.SubmissionDetailsResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/** 客户推荐边界对象转换器，负责 DTO、领域对象与响应对象之间的转换。 */
@Component
public class SubmissionAssembler {
  public ApplicationClientRestrictionDTO toDTO(ApplicationClientRestrictionRequest request) {
    var value = new ApplicationClientRestrictionDTO();
    value.setClientId(request.getClientId());
    value.setReasonDetail(request.getReasonDetail());
    value.setSourceChannel(request.getSourceChannel());
    value.setEvidenceReference(request.getEvidenceReference());
    value.setExpiresAt(request.getExpiresAt());
    value.setSourceOccurredAt(request.getSourceOccurredAt());
    return value;
  }

  public CreateSubmissionDTO toDTO(SubmissionCreateRequest v) {
    return new CreateSubmissionDTO(v.getApplicationId());
  }

  public SendSubmissionDTO toDTO(SubmissionSendRequest v) {
    return new SendSubmissionDTO(
        v.getRecipient(), v.getChannel(), v.getReason(), parse(v.getVersion()));
  }

  public SubmissionFeedbackDTO toDTO(SubmissionFeedbackRequest v) {
    return new SubmissionFeedbackDTO(
        v.getResult(),
        v.getReasonCategory(),
        v.getNextAction(),
        v.getNote(),
        v.getSourceChannel(),
        v.getSourceOccurredAt(),
        v.isExplicitClientBan(),
        v.getRestrictionExpiresAt(),
        v.getRestrictionIntent());
  }

  public WithdrawSubmissionDTO toDTO(SubmissionWithdrawRequest v) {
    return new WithdrawSubmissionDTO(v.getReason(), parse(v.getVersion()));
  }

  public AcknowledgeSubmissionRiskDTO toDTO(SubmissionRiskRequest v) {
    return new AcknowledgeSubmissionRiskDTO(v.getRiskCode(), v.getReason());
  }

  public CorrectClientRestrictionDTO toDTO(SubmissionRestrictionRequest v) {
    return new CorrectClientRestrictionDTO(
        v.getRestrictionIntent(),
        v.getExpiresAt(),
        v.getReasonDetail(),
        v.getSourceChannel(),
        v.getSourceOccurredAt());
  }

  public DisableClientRestrictionDTO toDTO(SubmissionDisableRequest v) {
    return new DisableClientRestrictionDTO(v.getReason());
  }

  public ClientSubmissionResponse toResponse(ClientSubmission v) {
    return new ClientSubmissionResponse(
        v.getId(),
        v.getApplicationId(),
        v.getCandidateId(),
        v.getDemandId(),
        v.getClientId(),
        v.getProjectId(),
        v.getPositionId(),
        v.getJdId(),
        v.getJdVersionId(),
        v.getResumeVersionId(),
        v.getDeliveryStatus(),
        v.getProcessStatus(),
        v.getSentAt(),
        v.getWithdrawnAt(),
        v.getWithdrawnByUserId(),
        v.getWithdrawalReason(),
        v.getOwnerUserId(),
        v.getOwnerDepartmentId(),
        v.getCreateId(),
        format(v.getVersion()),
        v.getCreateTime(),
        v.getUpdateTime());
  }

  public CursorPage<ClientSubmissionResponse> toResponse(CursorPage<ClientSubmission> v) {
    return v.map(this::toResponse);
  }

  public FeedbackResponse toResponse(Feedback v) {
    return new FeedbackResponse(
        v.getId(),
        v.getSubmissionId(),
        v.getResult(),
        v.getReasonCategory(),
        v.getNextAction(),
        v.getNote(),
        v.getSourceChannel(),
        v.getSourceOccurredAt(),
        v.getRecordedByUserId(),
        v.getCreateTime());
  }

  public RiskAcknowledgementResponse toResponse(RiskAcknowledgement v) {
    return new RiskAcknowledgementResponse(
        v.getId(),
        v.getSubmissionId(),
        v.getRiskCode(),
        v.getReason(),
        v.getAcknowledgedByUserId(),
        v.getCreateTime());
  }

  public SendSnapshotResponse toResponse(SendSnapshot v) {
    return v == null
        ? null
        : new SendSnapshotResponse(
            v.getId(),
            v.getSubmissionId(),
            v.getResumeVersionId(),
            v.getRecipient(),
            v.getChannel(),
            v.getFileName(),
            v.getContentSha256(),
            v.getSentByUserId(),
            v.getSentAt());
  }

  public SubmissionDetailsResponse toResponse(SubmissionDetails v) {
    return new SubmissionDetailsResponse(
        toResponse(v.getSubmission()),
        toResponse(v.getSendSnapshot()),
        v.getFeedback().stream().map(this::toResponse).toList(),
        v.getRiskAcknowledgements().stream().map(this::toResponse).toList());
  }

  public ClientRestrictionResponse toResponse(ClientRestriction v) {
    return new ClientRestrictionResponse(
        v.getId(),
        v.getClientId(),
        v.getCandidateId(),
        v.getSourceSubmissionId(),
        v.getSourceApplicationId(),
        v.getEvidenceReference(),
        v.getRecordType(),
        v.getStatus(),
        v.getRestrictionIntent(),
        v.getExpiresAt(),
        v.getReasonDetail(),
        v.getSourceChannel(),
        v.getSourceOccurredAt(),
        v.getCorrectsRecordId(),
        v.getRecordedByUserId(),
        v.getCreateTime(),
        format(v.getVersion()));
  }

  public List<ClientRestrictionResponse> toRestrictionResponses(List<ClientRestriction> v) {
    return v.stream().map(this::toResponse).toList();
  }

  public EligibilityResponse toResponse(Eligibility v) {
    return new EligibilityResponse(
        v.getApplicationId(),
        v.getCandidateId(),
        v.getClientId(),
        v.isCompanyBlacklist(),
        v.isClientRestriction(),
        v.isEligible(),
        v.getBlockingCodes());
  }
}
