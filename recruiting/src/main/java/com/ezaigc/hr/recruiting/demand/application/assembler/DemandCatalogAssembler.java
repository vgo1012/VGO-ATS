package com.ezaigc.hr.recruiting.demand.application.assembler;

import static com.ezaigc.hr.common.web.version.ResourceVersion.format;
import static com.ezaigc.hr.common.web.version.ResourceVersion.parse;

import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.recruiting.demand.domain.dto.CreateClientDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.RecruitmentReadinessDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.CreateDemandDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.CreateJdVersionDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.PublicJdSourceDTO;
import com.ezaigc.hr.recruiting.demand.application.support.JdCityNames;
import com.ezaigc.hr.recruiting.demand.domain.dto.CreatePositionDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.CreateProjectDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.UpdateDemandDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.UpdateNameDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.UpdatePositionDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.VersionReasonDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.WorkflowDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.WorkflowDetailsDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.WorkflowStepDraft;
import com.ezaigc.hr.recruiting.demand.domain.entity.ClientEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdSourceEntity;
import com.ezaigc.hr.recruiting.demand.domain.response.JdSourceResponse;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.PositionEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.ProjectEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.WorkflowStepEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.WorkflowTemplateEntity;
import com.ezaigc.hr.recruiting.demand.domain.request.CreateClientRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.CreateDemandRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.CreateJdVersionRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.UpdateJdVersionRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.CreatePositionRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.CreateProjectRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.UpdateDemandRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.UpdateNameRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.UpdatePositionRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.VersionReasonRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.WorkflowRequest;
import com.ezaigc.hr.recruiting.demand.domain.response.ClientResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.RecruitmentReadinessResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.DemandResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.PublicJdSourceResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.JdResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.JdVersionResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.PositionResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.ProjectResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.WorkflowDetailsResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.WorkflowStepResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.WorkflowTemplateResponse;
import com.ezaigc.hr.recruiting.demand.enums.WorkflowStepCode;
import java.util.function.Function;
import java.util.List;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdCodeOptionEntity;
import com.ezaigc.hr.recruiting.demand.enums.JdCodeOptionKind;
import com.ezaigc.hr.recruiting.demand.domain.response.JdCodeOptionResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.JdCodeOptionsResponse;
import org.springframework.stereotype.Component;
import com.ezaigc.hr.recruiting.demand.enums.JdSourceRecordType;

/** 招聘需求目录边界对象转换器，负责 DTO、领域对象与响应对象之间的转换。 */
@Component
public class DemandCatalogAssembler {
  public JdCodeOptionResponse toJdCodeOptionResponse(JdCodeOptionEntity value) {
    return new JdCodeOptionResponse(value.getCode(), value.getName());
  }

  public JdCodeOptionsResponse toJdCodeOptionsResponse(List<JdCodeOptionEntity> values) {
    return new JdCodeOptionsResponse(
        values.stream().filter(value -> JdCodeOptionKind.JOB_TYPE.matches(value.getKind())).map(this::toJdCodeOptionResponse).toList(),
        values.stream().filter(value -> JdCodeOptionKind.REGION.matches(value.getKind())).map(this::toJdCodeOptionResponse).toList());
  }

  public RecruitmentReadinessResponse toReadinessResponse(RecruitmentReadinessDTO dto) {
    var response = new RecruitmentReadinessResponse(dto.getJdId(), dto.getJdVersionId(), dto.getJdTitle(),
        dto.getDemandId(), dto.getRecruitmentType(), dto.isCanProcess(), dto.getActiveWorkflowId(),
        dto.getActiveWorkflow() == null ? null : toWorkflowDetailsResponse(dto.getActiveWorkflow()),
        dto.getDraftWorkflows().stream().map(this::toWorkflowDetailsResponse).toList(), dto.getStandardSteps());
    response.setJdCode(dto.getJdCode());
    response.setCanManageWorkflow(dto.isCanManageWorkflow());
    response.setClientId(dto.getClientId());
    response.setClientName(dto.getClientName());
    response.setHeadcount(dto.getHeadcount());
    response.setOccupiedHeadcount(dto.getOccupiedHeadcount());
    response.setRemainingHeadcount(dto.getRemainingHeadcount());
    return response;
  }

  public CreateClientDTO toCreateClientDTO(CreateClientRequest request) {
    CreateClientDTO dto = new CreateClientDTO();
    // 业务编码只在服务端生成。
    dto.setName(request.getName());
    return dto;
  }

  public CreateProjectDTO toCreateProjectDTO(CreateProjectRequest request) {
    CreateProjectDTO dto = new CreateProjectDTO();
    // 业务编码只在服务端生成。
    dto.setName(request.getName());
    return dto;
  }

  public UpdateNameDTO toUpdateNameDTO(UpdateNameRequest request) {
    UpdateNameDTO dto = new UpdateNameDTO();
    dto.setName(request.getName());
    dto.setVersion(parse(request.getVersion()));
    return dto;
  }

  public VersionReasonDTO toVersionReasonDTO(VersionReasonRequest request) {
    VersionReasonDTO dto = new VersionReasonDTO();
    dto.setVersion(parse(request.getVersion()));
    dto.setReason(request.getReason());
    return dto;
  }

  public CreatePositionDTO toCreatePositionDTO(CreatePositionRequest request) {
    CreatePositionDTO dto = new CreatePositionDTO();
    dto.setClientId(request.getClientId());
    dto.setProjectId(request.getProjectId());
    // 业务编码只在服务端生成。
    dto.setName(request.getName());
    return dto;
  }

  public UpdatePositionDTO toUpdatePositionDTO(UpdatePositionRequest request) {
    UpdatePositionDTO dto = new UpdatePositionDTO();
    dto.setProjectId(request.getProjectId());
    dto.setName(request.getName());
    dto.setVersion(parse(request.getVersion()));
    return dto;
  }

  public CreateDemandDTO toCreateDemandDTO(CreateDemandRequest request) {
    CreateDemandDTO dto = new CreateDemandDTO();
    // 业务编码只在服务端生成。
    dto.setTitle(request.getTitle());
    dto.setClientId(request.getClientId());
    dto.setProjectId(request.getProjectId());
    dto.setPositionId(request.getPositionId());
    dto.setRecruitmentType(request.getRecruitmentType());
    dto.setHeadcount(request.getHeadcount());
    dto.setDescription(request.getDescription());
    dto.setOwnerUserId(request.getOwnerUserId());
    dto.setOwnerDepartmentId(request.getOwnerDepartmentId());
    dto.setCollaboratorUserIds(request.getCollaboratorUserIds());
    return dto;
  }

  public UpdateDemandDTO toUpdateDemandDTO(UpdateDemandRequest request) {
    UpdateDemandDTO dto = new UpdateDemandDTO();
    dto.setTitle(request.getTitle());
    dto.setClientId(request.getClientId());
    dto.setProjectId(request.getProjectId());
    dto.setPositionId(request.getPositionId());
    dto.setRecruitmentType(request.getRecruitmentType());
    dto.setHeadcount(request.getHeadcount());
    dto.setDescription(request.getDescription());
    dto.setOwnerUserId(request.getOwnerUserId());
    dto.setOwnerDepartmentId(request.getOwnerDepartmentId());
    dto.setCollaboratorUserIds(request.getCollaboratorUserIds());
    dto.setOwnershipReason(request.getOwnershipReason());
    dto.setVersion(parse(request.getVersion()));
    return dto;
  }

  public CreateJdVersionDTO toCreateJdVersionDTO(CreateJdVersionRequest request) {
    CreateJdVersionDTO dto = new CreateJdVersionDTO();
    dto.setWorkRegionName(request.getWorkRegionName());
    dto.setOwnerUserId(request.getOwnerUserId());
    dto.setOwnerDepartmentId(request.getOwnerDepartmentId());
    dto.setRecruitmentType(request.getRecruitmentType());
    dto.setTitle(request.getTitle());
    dto.setRawText(request.getRawText());
    dto.setSourceType(request.getSourceType());
    dto.setClientId(request.getClientId());
    dto.setAppliedFromJdVersionId(request.getAppliedFromJdVersionId());
    dto.setDistinction(request.getDistinction());
    dto.setJobTypeCode(request.getJobTypeCode());
    dto.setWorkRegionCode(request.getWorkRegionCode());
    dto.setPublicVisible(request.isPublicVisible());
    dto.setContentShared(request.getContentShared());
    dto.setForceNew(request.isForceNew());
    dto.setHeadcount(request.getHeadcount());
    dto.setRecruitmentCycleDays(request.getRecruitmentCycleDays());
    return dto;
  }

  public CreateJdVersionDTO toUpdateJdVersionDTO(UpdateJdVersionRequest request) {
    CreateJdVersionDTO dto = new CreateJdVersionDTO();
    dto.setOwnerUserId(request.getOwnerUserId());
    dto.setOwnerDepartmentId(request.getOwnerDepartmentId());
    dto.setRecruitmentType(request.getRecruitmentType());
    dto.setTitle(request.getTitle());
    dto.setRawText(request.getRawText());
    dto.setSourceType(request.getSourceType());
    dto.setClientId(request.getClientId());
    dto.setDistinction(request.getDistinction());
    dto.setJobTypeCode(request.getJobTypeCode());
    dto.setWorkRegionCode(request.getWorkRegionCode());
    dto.setWorkRegionName(request.getWorkRegionName());
    dto.setPublicVisible(request.isPublicVisible());
    dto.setContentShared(request.getContentShared());
    dto.setForceNew(request.isForceNew());
    dto.setHeadcount(request.getHeadcount());
    dto.setRecruitmentCycleDays(request.getRecruitmentCycleDays());
    dto.setVersion(parse(request.getVersion()));
    return dto;
  }

  public WorkflowDTO toWorkflowDTO(WorkflowRequest request) {
    WorkflowDTO dto = new WorkflowDTO();
    dto.setRecruitmentType(request.getRecruitmentType());
    dto.setVersion(parse(request.getVersion()));
    dto.setSteps(
        request.getSteps().stream()
            .map(
                step -> {
                  WorkflowStepDraft draft = new WorkflowStepDraft();
                  draft.setCode(step.getCode());
                  draft.setDefaultFollowUpChannel(step.getDefaultFollowUpChannel());
                  return draft;
                })
            .toList());
    return dto;
  }

  public ClientResponse toClientResponse(ClientEntity value) {
    if (value == null) return null;
    ClientResponse response = new ClientResponse();
    response.setId(value.getId());
    response.setCode(value.getCode());
    response.setName(value.getName());
    response.setStatus(value.getStatus());
    response.setOwnerUserId(value.getOwnerUserId());
    response.setOwnerDepartmentId(value.getOwnerDepartmentId());
    response.setVersion(format(value.getVersion()));
    response.setUpdateTime(value.getUpdateTime());
    return response;
  }

  public ProjectResponse toProjectResponse(ProjectEntity value) {
    if (value == null) return null;
    ProjectResponse response = new ProjectResponse();
    response.setId(value.getId());
    response.setClientId(value.getClientId());
    response.setCode(value.getCode());
    response.setName(value.getName());
    response.setStatus(value.getStatus());
    response.setVersion(format(value.getVersion()));
    response.setUpdateTime(value.getUpdateTime());
    return response;
  }

  public PositionResponse toPositionResponse(PositionEntity value) {
    if (value == null) return null;
    PositionResponse response = new PositionResponse();
    response.setId(value.getId());
    response.setClientId(value.getClientId());
    response.setProjectId(value.getProjectId());
    response.setCode(value.getCode());
    response.setName(value.getName());
    response.setStatus(value.getStatus());
    response.setVersion(format(value.getVersion()));
    response.setUpdateTime(value.getUpdateTime());
    return response;
  }

  public DemandResponse toDemandResponse(DemandEntity value) {
    if (value == null) return null;
    DemandResponse response = new DemandResponse();
    response.setId(value.getId());
    response.setCode(value.getCode());
    response.setTitle(value.getTitle());
    response.setClientId(value.getClientId());
    response.setProjectId(value.getProjectId());
    response.setPositionId(value.getPositionId());
    response.setRecruitmentType(value.getRecruitmentType());
    response.setHeadcount(value.getHeadcount());
    response.setDescription(value.getDescription());
    response.setStatus(value.getStatus());
    response.setOwnerUserId(value.getOwnerUserId());
    response.setOwnerDepartmentId(value.getOwnerDepartmentId());
    response.setVersion(format(value.getVersion()));
    response.setUpdateTime(value.getUpdateTime());
    return response;
  }

  public JdResponse toJdResponse(JdEntity value) {
    if (value == null) return null;
    JdResponse response = new JdResponse();
    response.setCurrentParseStatus(value.getCurrentParseStatus());
    response.setCode(value.getCode());
    response.setDistinction(value.getDistinction());
    response.setJobTypeCode(value.getJobTypeCode());
    response.setWorkRegionCode(value.getWorkRegionCode());
    response.setWorkRegionName(JdCityNames.display(value.getWorkRegionName()));
    response.setOnboardedHeadcount(value.getOnboardedHeadcount());
    response.setPublicVisible(false);
    response.setContentShared(value.isContentShared());
    response.setAppliedFromJdVersionId(value.getAppliedFromJdVersionId());
    response.setDeleted(value.isDeleted());
    response.setRecruitmentType(value.getRecruitmentType());
    response.setHeadcount(value.getHeadcount());
    response.setRecruitmentCycleDays(value.getRecruitmentCycleDays());
    response.setOccupiedHeadcount(value.getOccupiedHeadcount());
    response.setRemainingHeadcount(value.getHeadcount() == null ? null
        : Math.max(0, value.getHeadcount() - value.getOccupiedHeadcount()));
    response.setVersion(format(value.getVersion()));
    response.setId(value.getId());
    response.setDemandId(value.getDemandId());
    response.setDemandCode(value.getDemandCode());
    response.setTitle(value.getTitle());
    response.setStatus(value.getStatus());
    response.setCurrentVersionNo(value.getCurrentVersionNo());
    response.setClientId(value.getClientId());
    response.setClientName(value.getClientName());
    response.setOwnerUserId(value.getOwnerUserId());
    response.setOwnerDepartmentId(value.getOwnerDepartmentId());
    response.setCreateTime(value.getCreateTime());
    return response;
  }

  public PublicJdSourceResponse toPublicJdSourceResponse(PublicJdSourceDTO value) {
    if (value == null) return null;
    var response = new PublicJdSourceResponse();
    response.setOwnerUserId(value.getOwnerUserId());
    response.setOwnerUserName(value.getOwnerUserName());
    response.setOwnerDepartmentId(value.getOwnerDepartmentId());
    response.setOwnerDepartmentName(value.getOwnerDepartmentName());
    response.setId(value.getId());
    response.setCode(value.getCode());
    response.setTitle(value.getTitle());
    response.setWorkRegionName(JdCityNames.display(value.getWorkRegionName()));
    response.setCurrentVersionNo(value.getCurrentVersionNo());
    response.setCurrentVersionId(value.getCurrentVersionId());
    response.setRawText(value.getRawText());
    response.setCreateTime(value.getCreateTime());
    return response;
  }

  public JdSourceResponse toJdSourceResponse(JdSourceEntity value) {
    var response = new JdSourceResponse();
    response.setId(value.getId()); response.setJdVersionId(value.getJdVersionId());
    response.setSourceType(value.getSourceType()); response.setSourceLabel(JdSourceRecordType.MAIL.matches(value.getSourceType()) ? JdSourceRecordType.MAIL.getDesc() : JdSourceRecordType.MANUAL.getDesc());
    response.setMailSourceId(value.getMailSourceId()); response.setOwnerUserId(value.getOwnerUserId());
    response.setCreateTime(value.getCreateTime());
    return response;
  }

  public JdVersionResponse toJdVersionResponse(JdVersionEntity value) {
    if (value == null) return null;
    JdVersionResponse response = new JdVersionResponse();
    response.setId(value.getId());
    response.setJdId(value.getJdId());
    response.setJdCode(value.getJdCode());
    response.setVersionNo(value.getVersionNo());
    response.setTitle(value.getTitle());
    response.setRawText(value.getRawText());
    response.setStatus(value.getStatus());
    response.setSourceType(value.getSourceType());
    response.setCreateId(value.getCreateId());
    response.setCreateTime(value.getCreateTime());
    return response;
  }

  public WorkflowDetailsResponse toWorkflowDetailsResponse(WorkflowDetailsDTO value) {
    if (value == null) return null;
    WorkflowDetailsResponse response = new WorkflowDetailsResponse();
    response.setTemplate(toWorkflowTemplateResponse(value.getTemplate()));
    response.setSteps(
        value.getSteps().stream().map(DemandCatalogAssembler::toWorkflowStepResponse).toList());
    return response;
  }

  public <S, T> CursorPage<T> toPage(CursorPage<S> page, Function<S, T> converter) {
    return page.map(converter);
  }

  private static WorkflowTemplateResponse toWorkflowTemplateResponse(WorkflowTemplateEntity value) {
    WorkflowTemplateResponse response = new WorkflowTemplateResponse();
    response.setId(value.getId());
    response.setJdVersionId(value.getJdVersionId());
    response.setVersionNo(value.getVersionNo());
    response.setRecruitmentType(value.getRecruitmentType());
    response.setStatus(value.getStatus());
    response.setVersion(format(value.getVersion()));
    response.setCreateId(value.getCreateId());
    response.setActivatedByUserId(value.getActivatedByUserId());
    response.setActivatedAt(value.getActivatedAt());
    response.setCreateTime(value.getCreateTime());
    response.setUpdateTime(value.getUpdateTime());
    return response;
  }

  private static WorkflowStepResponse toWorkflowStepResponse(WorkflowStepEntity value) {
    WorkflowStepResponse response = new WorkflowStepResponse();
    response.setId(value.getId());
    response.setTemplateVersionId(value.getTemplateVersionId());
    response.setStepCode(value.getStepCode());
    response.setStepCodeDesc(WorkflowStepCode.fromCode(value.getStepCode()).getDesc());
    response.setSequenceNo(value.getSequenceNo());
    response.setCandidateOptional(value.getCandidateOptional());
    response.setDefaultFollowUpChannel(value.getDefaultFollowUpChannel());
    return response;
  }
}
