package com.ezaigc.hr.talent.application.assembler;

import static com.ezaigc.hr.common.web.version.ResourceVersion.format;
import static com.ezaigc.hr.common.web.version.ResourceVersion.parse;

import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.talent.domain.dto.AssignIntakeDTO;
import com.ezaigc.hr.talent.domain.dto.ConfirmIntakeDTO;
import com.ezaigc.hr.talent.domain.dto.ImportIntakeDTO;
import com.ezaigc.hr.talent.domain.dto.LinkIntakeBatchJdDTO;
import com.ezaigc.hr.talent.domain.dto.CreateIntakeBatchDTO;
import com.ezaigc.hr.talent.domain.dto.CreateSourceChannelDTO;
import com.ezaigc.hr.talent.domain.dto.ResolveIdentityConflictDTO;
import com.ezaigc.hr.talent.domain.dto.ResumeUploadDTO;
import com.ezaigc.hr.talent.domain.dto.TalentVersionReasonDTO;
import com.ezaigc.hr.talent.domain.dto.UpdateCandidateDTO;
import com.ezaigc.hr.talent.domain.dto.UpdateSourceChannelDTO;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.IdentityConflict;
import com.ezaigc.hr.talent.domain.entity.IntakeBatch;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.domain.entity.SourceChannel;
import com.ezaigc.hr.talent.domain.request.TalentIntakeAssignRequest;
import com.ezaigc.hr.talent.domain.request.TalentIntakeBatchRequest;
import com.ezaigc.hr.talent.domain.request.TalentIntakeCandidateRequest;
import com.ezaigc.hr.talent.domain.request.TalentIntakeChannelRequest;
import com.ezaigc.hr.talent.domain.request.TalentIntakeConfirmRequest;
import com.ezaigc.hr.talent.domain.request.TalentIntakeImportRequest;
import com.ezaigc.hr.talent.domain.request.TalentIntakeBatchJdRequest;
import com.ezaigc.hr.talent.enums.ResumeIntakeImportStatus;
import com.ezaigc.hr.talent.domain.request.TalentIntakeConflictRequest;
import com.ezaigc.hr.talent.domain.request.TalentIntakeUpdateChannelRequest;
import com.ezaigc.hr.talent.domain.request.TalentIntakeVersionReasonRequest;
import com.ezaigc.hr.talent.domain.response.CandidateResponse;
import com.ezaigc.hr.talent.domain.response.IdentityConflictResponse;
import com.ezaigc.hr.talent.domain.response.IntakeBatchResponse;
import com.ezaigc.hr.talent.domain.response.ResumeIntakeResponse;
import com.ezaigc.hr.talent.domain.response.ResumeSourceResponse;
import com.ezaigc.hr.talent.domain.response.ResumeVersionResponse;
import com.ezaigc.hr.talent.domain.response.SourceChannelResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** 人才接收边界对象转换器，负责 DTO、领域对象与响应对象之间的转换。 */
@Component
public class TalentIntakeAssembler {
  public ImportIntakeDTO toDTO(TalentIntakeImportRequest v) {
    return new ImportIntakeDTO(parse(v.getVersion()));
  }

  public LinkIntakeBatchJdDTO toDTO(TalentIntakeBatchJdRequest v) {
    return new LinkIntakeBatchJdDTO(v.getJdVersionId(), v.getReason());
  }

  public CreateSourceChannelDTO toDTO(TalentIntakeChannelRequest v) {
    return new CreateSourceChannelDTO(v.getName(), v.getChannelType());
  }

  public UpdateSourceChannelDTO toDTO(TalentIntakeUpdateChannelRequest v) {
    return new UpdateSourceChannelDTO(v.getName(), v.getChannelType(), parse(v.getVersion()));
  }

  public TalentVersionReasonDTO toDTO(TalentIntakeVersionReasonRequest v) {
    return new TalentVersionReasonDTO(parse(v.getVersion()), v.getReason(), v.getResumeVersionId());
  }

  public CreateIntakeBatchDTO toDTO(TalentIntakeBatchRequest v) {
    return new CreateIntakeBatchDTO(
        v.getSourceChannelId(),
        v.getDefaultOwnerUserId(),
        v.getOwnerDepartmentId(),
        v.getJdVersionId());
  }

  public ResumeUploadDTO toDTO(MultipartFile v) {
    return new ResumeUploadDTO(v.getOriginalFilename(), v.getSize(), v);
  }

  public AssignIntakeDTO toDTO(TalentIntakeAssignRequest v) {
    return new AssignIntakeDTO(
        v.getOwnerUserId(), v.getOwnerDepartmentId(), parse(v.getVersion()), v.getReason());
  }

  public ConfirmIntakeDTO toDTO(TalentIntakeConfirmRequest v) {
    return new ConfirmIntakeDTO(
        v.getCandidateId(), v.getFullName(), v.getEmail(), v.getPhone(), parse(v.getVersion()));
  }

  public UpdateCandidateDTO toDTO(TalentIntakeCandidateRequest v) {
    return new UpdateCandidateDTO(
        v.getFullName(),
        v.getEmail(),
        v.getPhone(),
        v.getOwnerUserId(),
        v.getOwnerDepartmentId(),
        parse(v.getVersion()));
  }

  public ResolveIdentityConflictDTO toDTO(TalentIntakeConflictRequest v) {
    return new ResolveIdentityConflictDTO(
        v.getResolution(), v.getChosenCandidateId(), v.getReason());
  }

  public SourceChannelResponse toResponse(SourceChannel v) {
    return new SourceChannelResponse(
        v.getId(),
        v.getCode(),
        v.getName(),
        v.getChannelType(),
        v.getStatus(),
        format(v.getVersion()),
        v.getCreateId(),
        v.getCreateTime(),
        v.getUpdateTime());
  }

  public CursorPage<SourceChannelResponse> toChannelResponses(CursorPage<SourceChannel> v) {
    return v.map(this::toResponse);
  }

  public IntakeBatchResponse toResponse(IntakeBatch v) {
    return new IntakeBatchResponse(
        v.getId(),
        v.getSourceChannelId(),
        v.getSourceRecruiterUserId(),
        v.getDefaultOwnerUserId(),
        v.getOwnerDepartmentId(),
        v.getJdVersionId(),
        v.getStatus(),
        v.getTotalCount(),
        v.getSucceededCount(),
        v.getFailedCount(),
        v.getParsingCount(),
        v.getProcessingCount(),
        v.getParsedCount(),
        v.getCompletedCount(),
        v.getIgnoredCount(),
        v.getAttentionCount(),
        v.getCreateTime(),
        v.getUpdateTime());
  }

  public ResumeIntakeResponse toResponse(ResumeIntake v) {
    return new ResumeIntakeResponse(
        v.getId(),
        v.getBatchId(),
        v.getJdVersionId(),
        v.getJdTitle(),
        v.getJdCode(),
        v.getSourceChannelId(),
        v.getSourceRecruiterUserId(),
        v.getDefaultOwnerUserId(),
        v.getOwnerUserId(),
        v.getOwnerDepartmentId(),
        v.getHandledByUserId(),
        v.getOriginalFileName(),
        v.getMediaType(),
        v.getSizeBytes(),
        v.getContentSha256(),
        v.getObjectKey(),
        v.getProcessingId(),
        v.getProcessingStatus(),
        v.getProcessingAttempt(),
        v.getParsedFullName(),
        v.getParsedEmail(),
        v.getParsedPhone(),
        v.isDuplicateContent(),
        v.getScanStatus(),
        v.getStatus(),
        ResumeIntakeImportStatus.from(v.getStatus(), v.getProcessingId(), v.getScoreStatus()),
        v.getFailureCode(),
        v.getCandidateId(),
        v.getResumeVersionId(),
        v.getScoreRunId(),
        v.getScoreStatus(),
        format(v.getVersion()),
        v.getCreateTime(),
        v.getUpdateTime());
  }

  public CursorPage<ResumeIntakeResponse> toIntakeResponses(CursorPage<ResumeIntake> v) {
    return v.map(this::toResponse);
  }

  public ResumeSourceResponse toSourceResponse(ResumeIntake intake, SourceChannel channel) {
    return new ResumeSourceResponse(intake.getId(), intake.getSourceChannelId(),
        channel == null ? null : channel.getName(), channel == null ? null : channel.getChannelType(),
        intake.getSourceRecruiterUserId(), intake.getCreateTime());
  }

  public CandidateResponse toResponse(Candidate v) {
    return new CandidateResponse(
        v.getId(),
        v.getFullName(),
        v.getPrimaryEmail(),
        v.getPrimaryPhone(),
        v.getOwnerUserId(),
        v.getOwnerDepartmentId(),
        v.getStatus(),
        format(v.getVersion()),
        v.getCreateId(),
        v.getCreateTime(),
        v.getUpdateTime());
  }

  public CursorPage<CandidateResponse> toCandidateResponses(CursorPage<Candidate> v) {
    return v.map(this::toResponse);
  }

  public ResumeVersionResponse toResponse(ResumeVersion v) {
    return new ResumeVersionResponse(
        v.getId(),
        v.getResumeId(),
        v.getIntakeId(),
        v.getVersionNo(),
        v.getOriginalFileName(),
        v.getMediaType(),
        v.getSizeBytes(),
        v.getContentSha256(),
        v.getStatus(),
        v.getCreateId(),
        v.getCreateTime());
  }

  public CursorPage<ResumeVersionResponse> toResumeVersionResponses(CursorPage<ResumeVersion> v) {
    return v.map(this::toResponse);
  }

  public IdentityConflictResponse toResponse(IdentityConflict v) {
    return new IdentityConflictResponse(
        v.getId(),
        v.getIntakeId(),
        v.getCandidateIdA(),
        v.getCandidateIdB(),
        v.getConflictType(),
        v.getStatus(),
        v.getResolution(),
        v.getChosenCandidateId(),
        v.getReason(),
        v.getResolvedByUserId(),
        v.getResolvedAt(),
        v.getCreateTime());
  }
}
