package com.ezaigc.hr.talent.application.assembler;

import static com.ezaigc.hr.common.web.version.ResourceVersion.format;

import com.ezaigc.hr.talent.domain.entity.AffectedWorkflow;
import com.ezaigc.hr.talent.domain.entity.CompanyBlacklistRecord;
import com.ezaigc.hr.talent.domain.entity.CompanyBlacklistView;
import com.ezaigc.hr.talent.domain.entity.EducationRecord;
import com.ezaigc.hr.talent.domain.entity.RestrictionSummary;
import com.ezaigc.hr.talent.domain.response.AffectedWorkflowResponse;
import com.ezaigc.hr.talent.domain.response.CompanyBlacklistRecordResponse;
import com.ezaigc.hr.talent.domain.response.CompanyBlacklistResponse;
import com.ezaigc.hr.talent.domain.response.EducationVerificationResponse;
import com.ezaigc.hr.talent.domain.response.RestrictionSummaryResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/** 风险边界对象转换器，负责 DTO、领域对象与响应对象之间的转换。 */
@Component
public class RiskAssembler {
  public RestrictionSummaryResponse toResponse(RestrictionSummary v) {
    return new RestrictionSummaryResponse(
        v.getRestrictionType(),
        v.getClientId(),
        v.getClientName(),
        v.getReason(),
        v.getSourceChannel(),
        v.getOccurredAt(),
        v.getExpiresAt(),
        v.isBlocking());
  }

  public List<RestrictionSummaryResponse> toRestrictionResponses(List<RestrictionSummary> v) {
    return v.stream().map(this::toResponse).toList();
  }

  public CompanyBlacklistRecordResponse toResponse(CompanyBlacklistRecord v) {
    return new CompanyBlacklistRecordResponse(
        v.getId(),
        v.getCandidateId(),
        v.getRecordType(),
        v.getStatus(),
        v.getReasonCode(),
        v.getReasonDetail(),
        v.getSourceChannel(),
        v.getEvidenceReference(),
        v.getCorrectsRecordId(),
        v.getRecordedByUserId(),
        v.getOccurredAt(),
        v.getCreateTime(),
        format(v.getVersion()));
  }

  public CompanyBlacklistResponse toResponse(CompanyBlacklistView v) {
    return new CompanyBlacklistResponse(toResponse(v.getRecord()), v.getAffectedWorkflowCount());
  }

  public AffectedWorkflowResponse toResponse(AffectedWorkflow v) {
    return new AffectedWorkflowResponse(
        v.getResourceType(),
        v.getResourceId(),
        v.getApplicationId(),
        v.getClientId(),
        v.getJdId(),
        v.getStatus(),
        v.getOwnerUserId(),
        v.getUpdateTime());
  }

  public List<AffectedWorkflowResponse> toAffectedResponses(List<AffectedWorkflow> v) {
    return v.stream().map(this::toResponse).toList();
  }

  public EducationVerificationResponse toResponse(EducationRecord v) {
    return new EducationVerificationResponse(
        v.getId(),
        v.getCandidateId(),
        v.getEducationFactId(),
        v.getApplicationId(),
        v.getRecordType(),
        v.getVerificationStatus(),
        v.getStudyMode(),
        v.getSourceChannel(),
        v.getEvidenceReference(),
        v.getNote(),
        v.getCorrectsRecordId(),
        v.getRecordedByUserId(),
        v.getOccurredAt(),
        v.getCreateTime(),
        format(v.getVersion()));
  }

  public List<EducationVerificationResponse> toEducationResponses(List<EducationRecord> v) {
    return v.stream().map(this::toResponse).toList();
  }
}
