package com.ezaigc.hr.recruiting.demand.controller;

import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_JD_QUERY_LENGTH;

import com.ezaigc.hr.common.web.version.ResourceVersion;
import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import com.ezaigc.hr.common.web.request.OptionalFilterCode;
import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorCount;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.recruiting.demand.application.assembler.DemandCatalogAssembler;
import com.ezaigc.hr.recruiting.demand.application.manager.DemandCatalogApplicationManager;
import com.ezaigc.hr.recruiting.demand.application.manager.ClientDeletionApplicationManager;
import com.ezaigc.hr.recruiting.demand.domain.request.CreateClientRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.CreateDemandRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.CreateJdVersionRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.CreateJdCodeOptionRequest;
import com.ezaigc.hr.recruiting.demand.domain.response.JdCodeOptionResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.JdCodeOptionsResponse;
import com.ezaigc.hr.recruiting.demand.domain.request.UpdateJdVersionRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.CreatePositionRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.CreateProjectRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.UpdateDemandRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.UpdateNameRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.UpdatePositionRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.VersionReasonRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.WorkflowRequest;
import com.ezaigc.hr.recruiting.demand.domain.response.ClientResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.DemandResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.PublicJdSourceResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.JdResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.JdSourceResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.JdVersionResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.PositionResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.ProjectResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.WorkflowDetailsResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/v1")
public class DemandCatalogController {

    @Resource
    private DemandCatalogApplicationManager service;
    @Resource
    private ClientDeletionApplicationManager clientDeletion;
    @Resource
    private DemandCatalogAssembler assembler;

    @GetMapping("/clients")
    CursorPage<ClientResponse> clients(
            @OptionalFilterCode @RequestParam(required = false) String status,
            @ValidCursor @RequestParam(required = false) String cursor,
            @StandardPageLimit @RequestParam(required = false) Integer limit) {
        return assembler.toPage(
                service.listClients(status, cursor, limit), assembler::toClientResponse);
    }

    @GetMapping("/clients/count")
    CursorCount countClients(
            @OptionalFilterCode @RequestParam(required = false) String status) {
        return service.countClients(status);
    }

    /**
     * 创建客户。
     */
    @PostMapping("/clients")
    @ResponseStatus(HttpStatus.CREATED)
    ClientResponse createClient(
            @Valid @RequestBody CreateClientRequest request,
            @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
        return assembler.toClientResponse(
                service.createClient(assembler.toCreateClientDTO(request), key));
    }

    @GetMapping("/clients/{clientId}")
    ClientResponse client(@PlatformIdentifier @PathVariable String clientId) {
        return assembler.toClientResponse(service.getClient(clientId));
    }

    /**
     * 更新客户。
     */
    @PatchMapping("/clients/{clientId}")
    ClientResponse updateClient(
            @PlatformIdentifier @PathVariable String clientId, @Valid @RequestBody UpdateNameRequest request) {
        return assembler.toClientResponse(
                service.updateClient(clientId, assembler.toUpdateNameDTO(request)));
    }

    /**
     * 停用客户。
     */
    @PostMapping("/clients/{clientId}/disable")
    ClientResponse disableClient(
            @PlatformIdentifier @PathVariable String clientId, @Valid @RequestBody VersionReasonRequest request) {
        return assembler.toClientResponse(
                service.disableClient(clientId, assembler.toVersionReasonDTO(request)));
    }

    @DeleteMapping("/clients/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteClient(@PlatformIdentifier @PathVariable String clientId,
            @Valid @RequestBody VersionReasonRequest request) {
        clientDeletion.deleteClient(clientId, assembler.toVersionReasonDTO(request));
    }

    @GetMapping("/clients/{clientId}/projects")
    CursorPage<ProjectResponse> projects(
            @PlatformIdentifier @PathVariable String clientId,
            @OptionalFilterCode @RequestParam(required = false) String status,
            @ValidCursor @RequestParam(required = false) String cursor,
            @StandardPageLimit @RequestParam(required = false) Integer limit) {
        return assembler.toPage(
                service.listProjects(clientId, status, cursor, limit), assembler::toProjectResponse);
    }

    /**
     * 创建项目。
     */
    @PostMapping("/clients/{clientId}/projects")
    @ResponseStatus(HttpStatus.CREATED)
    ProjectResponse createProject(
            @PlatformIdentifier @PathVariable String clientId,
            @Valid @RequestBody CreateProjectRequest request,
            @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
        return assembler.toProjectResponse(
                service.createProject(clientId, assembler.toCreateProjectDTO(request), key));
    }

    @GetMapping("/client-projects/{projectId}")
    ProjectResponse project(@PlatformIdentifier @PathVariable String projectId) {
        return assembler.toProjectResponse(service.getProject(projectId));
    }

    /**
     * 更新项目。
     */
    @PatchMapping("/client-projects/{projectId}")
    ProjectResponse updateProject(
            @PlatformIdentifier @PathVariable String projectId, @Valid @RequestBody UpdateNameRequest request) {
        return assembler.toProjectResponse(
                service.updateProject(projectId, assembler.toUpdateNameDTO(request)));
    }

    /**
     * 停用项目。
     */
    @PostMapping("/client-projects/{projectId}/disable")
    ProjectResponse disableProject(
            @PlatformIdentifier @PathVariable String projectId, @Valid @RequestBody VersionReasonRequest request) {
        return assembler.toProjectResponse(
                service.disableProject(projectId, assembler.toVersionReasonDTO(request)));
    }

    @GetMapping("/client-positions")
    CursorPage<PositionResponse> positions(
            @OptionalPlatformIdentifier @RequestParam(required = false) String clientId,
            @OptionalPlatformIdentifier @RequestParam(required = false) String projectId,
            @OptionalFilterCode @RequestParam(required = false) String status,
            @ValidCursor @RequestParam(required = false) String cursor,
            @StandardPageLimit @RequestParam(required = false) Integer limit) {
        return assembler.toPage(
                service.listPositions(clientId, projectId, status, cursor, limit),
                assembler::toPositionResponse);
    }

    /**
     * 创建职位。
     */
    @PostMapping("/client-positions")
    @ResponseStatus(HttpStatus.CREATED)
    PositionResponse createPosition(
            @Valid @RequestBody CreatePositionRequest request,
            @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
        return assembler.toPositionResponse(
                service.createPosition(assembler.toCreatePositionDTO(request), key));
    }

    @GetMapping("/client-positions/{positionId}")
    PositionResponse position(@PlatformIdentifier @PathVariable String positionId) {
        return assembler.toPositionResponse(service.getPosition(positionId));
    }

    /**
     * 更新职位。
     */
    @PatchMapping("/client-positions/{positionId}")
    PositionResponse updatePosition(
            @PlatformIdentifier @PathVariable String positionId, @Valid @RequestBody UpdatePositionRequest request) {
        return assembler.toPositionResponse(
                service.updatePosition(positionId, assembler.toUpdatePositionDTO(request)));
    }

    /**
     * 停用职位。
     */
    @PostMapping("/client-positions/{positionId}/disable")
    PositionResponse disablePosition(
            @PlatformIdentifier @PathVariable String positionId, @Valid @RequestBody VersionReasonRequest request) {
        return assembler.toPositionResponse(
                service.disablePosition(positionId, assembler.toVersionReasonDTO(request)));
    }

    @GetMapping("/demands")
    CursorPage<DemandResponse> demands(
            @OptionalFilterCode @RequestParam(required = false) String status,
            @ValidCursor @RequestParam(required = false) String cursor,
            @StandardPageLimit @RequestParam(required = false) Integer limit) {
        return assembler.toPage(
                service.listDemands(status, cursor, limit), assembler::toDemandResponse);
    }

    /**
     * 创建招聘需求。
     */
    @PostMapping("/demands")
    @ResponseStatus(HttpStatus.CREATED)
    DemandResponse createDemand(
            @Valid @RequestBody CreateDemandRequest request,
            @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
        return assembler.toDemandResponse(
                service.createDemand(assembler.toCreateDemandDTO(request), key));
    }

    @GetMapping("/demands/{demandId}")
    DemandResponse demand(@PlatformIdentifier @PathVariable String demandId) {
        return assembler.toDemandResponse(service.getDemand(demandId));
    }

    /**
     * 更新招聘需求。
     */
    @PatchMapping("/demands/{demandId}")
    DemandResponse updateDemand(
            @PlatformIdentifier @PathVariable String demandId, @Valid @RequestBody UpdateDemandRequest request) {
        return assembler.toDemandResponse(
                service.updateDemand(demandId, assembler.toUpdateDemandDTO(request)));
    }

    /**
     * 删除招聘需求。
     */
    @DeleteMapping("/demands/{demandId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteDemand(
            @PlatformIdentifier @PathVariable String demandId, @Valid @RequestBody VersionReasonRequest request) {
        service.deleteDemand(demandId, assembler.toVersionReasonDTO(request));
    }

    @PostMapping("/demands/{demandId}/{action:activate|pause|close}")
    DemandResponse transitionDemand(
            @PlatformIdentifier @PathVariable String demandId,
            @PathVariable String action,
            @Valid @RequestBody VersionReasonRequest request) {
        return assembler.toDemandResponse(
                service.transitionDemand(demandId, action, assembler.toVersionReasonDTO(request)));
    }

    @PostMapping("/demands/{demandId}/jd-versions")
    @ResponseStatus(HttpStatus.CREATED)
    JdVersionResponse createJdVersion(
            @PlatformIdentifier @PathVariable String demandId,
            @Valid @RequestBody CreateJdVersionRequest request,
            @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
        return assembler.toJdVersionResponse(
                service.addJdVersion(demandId, assembler.toCreateJdVersionDTO(request), key));
    }

    /** 接收待完善原文，不虚构客户、岗位或招聘人数。 */
    @PostMapping("/jd-drafts")
    @ResponseStatus(HttpStatus.CREATED)
    JdVersionResponse createSourceJdDraft(
            @Valid @RequestBody CreateJdVersionRequest request,
            @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
        return assembler.toJdVersionResponse(
                service.createSourceJdDraft(assembler.toCreateJdVersionDTO(request), key));
    }

    @GetMapping("/demands/{demandId}/jd-versions")
    CursorPage<JdVersionResponse> demandJdVersions(
            @PlatformIdentifier @PathVariable String demandId,
            @ValidCursor @RequestParam(required = false) String cursor,
            @StandardPageLimit @RequestParam(required = false) Integer limit) {
        return assembler.toPage(
                service.listDemandJdVersions(demandId, cursor, limit), assembler::toJdVersionResponse);
    }

    @GetMapping("/jd-versions/{jdVersionId}/sources")
    CursorPage<JdSourceResponse> jdSources(@PlatformIdentifier @PathVariable String jdVersionId,
            @ValidCursor @RequestParam(required = false) String cursor,
            @StandardPageLimit @RequestParam(required = false) Integer limit) {
        return assembler.toPage(service.listJdSources(jdVersionId, cursor, limit), assembler::toJdSourceResponse);
    }

    @GetMapping("/jd-code-options")
    JdCodeOptionsResponse jdCodeOptions() {
        return assembler.toJdCodeOptionsResponse(service.listJdCodeOptions());
    }

    @PostMapping("/jd-code-options")
    @ResponseStatus(HttpStatus.CREATED)
    JdCodeOptionResponse createJdCodeOption(@Valid @RequestBody CreateJdCodeOptionRequest request) {
        return assembler.toJdCodeOptionResponse(service.createJdCodeOption(request.getKind(), request.getName()));
    }

    @PostMapping("/jds")
    @ResponseStatus(HttpStatus.CREATED)
    JdVersionResponse createJd(
            @Validated(CreateJdVersionRequest.NewJd.class) @RequestBody CreateJdVersionRequest request,
            @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
        return assembler.toJdVersionResponse(service.createJd(assembler.toCreateJdVersionDTO(request), key));
    }

    @PostMapping("/jds/{jdId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    JdVersionResponse updateJd(@PlatformIdentifier @PathVariable String jdId,
            @Valid @RequestBody UpdateJdVersionRequest request,
            @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
        return assembler.toJdVersionResponse(service.updateJd(jdId, assembler.toUpdateJdVersionDTO(request), key));
    }

    @DeleteMapping("/jds/{jdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteJd(@PlatformIdentifier @PathVariable String jdId, @ValidResourceVersion @RequestParam String version) {
        service.deleteJd(jdId, ResourceVersion.parse(version));
    }

    @GetMapping("/jds")
    CursorPage<JdResponse> jds(
            @RequestParam(required = false)
            @Size(max = MAX_JD_QUERY_LENGTH, message = "query 长度不能超过 {max}")
            String query,
            @OptionalPlatformIdentifier @RequestParam(required = false) String clientId,
            @OptionalFilterCode @RequestParam(required = false) String status,
            @ValidCursor @RequestParam(required = false) String cursor,
            @StandardPageLimit @RequestParam(required = false) Integer limit) {
        return assembler.toPage(
                service.listJds(query, clientId, status, cursor, limit), assembler::toJdResponse);
    }

    @GetMapping("/jds/count")
    CursorCount countJds(
            @RequestParam(required = false)
            @Size(max = MAX_JD_QUERY_LENGTH, message = "query 长度不能超过 {max}")
            String query,
            @OptionalPlatformIdentifier @RequestParam(required = false) String clientId,
            @OptionalFilterCode @RequestParam(required = false) String status) {
        return service.countJds(query, clientId, status);
    }

    @GetMapping("/jds/public-sources")
    CursorPage<PublicJdSourceResponse> publicJdSources(
            @RequestParam(required = false)
            @Size(max = MAX_JD_QUERY_LENGTH, message = "query 长度不能超过 {max}") String query,
            @ValidCursor @RequestParam(required = false) String cursor,
            @StandardPageLimit @RequestParam(required = false) Integer limit) {
        return assembler.toPage(service.listPublicJdSources(query, cursor, limit),
                assembler::toPublicJdSourceResponse);
    }

    @GetMapping("/jds/public-sources/count")
    CursorCount countPublicJdSources(
            @RequestParam(required = false)
            @Size(max = MAX_JD_QUERY_LENGTH, message = "query 长度不能超过 {max}") String query) {
        return service.countPublicJdSources(query);
    }

    @GetMapping("/jds/public-sources/{jdId}")
    PublicJdSourceResponse publicJdSource(@PlatformIdentifier @PathVariable String jdId) {
        return assembler.toPublicJdSourceResponse(service.getPublicJdSource(jdId));
    }

    @GetMapping("/jds/{jdId}/versions")
    CursorPage<JdVersionResponse> jdVersions(
            @PlatformIdentifier @PathVariable String jdId,
            @ValidCursor @RequestParam(required = false) String cursor,
            @StandardPageLimit @RequestParam(required = false) Integer limit) {
        return assembler.toPage(
                service.listJdVersions(jdId, cursor, limit), assembler::toJdVersionResponse);
    }

    @GetMapping("/jd-versions/{jdVersionId}")
    JdVersionResponse jdVersion(
            @PlatformIdentifier @PathVariable String jdVersionId) {
        return assembler.toJdVersionResponse(service.getJdVersion(jdVersionId));
    }

    /**
     * 创建流程。
     */
    @PostMapping("/jd-versions/{jdVersionId}/workflow-template-versions")
    @ResponseStatus(HttpStatus.CREATED)
    WorkflowDetailsResponse createWorkflow(
            @PlatformIdentifier @PathVariable String jdVersionId,
            @Valid @RequestBody WorkflowRequest request,
            @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
        return assembler.toWorkflowDetailsResponse(
                service.createWorkflowTemplate(jdVersionId, assembler.toWorkflowDTO(request), key));
    }

    @GetMapping("/jd-versions/{jdVersionId}/workflow-template-versions")
    CursorPage<WorkflowDetailsResponse> workflowVersions(
            @PlatformIdentifier @PathVariable String jdVersionId,
            @ValidCursor @RequestParam(required = false) String cursor,
            @StandardPageLimit @RequestParam(required = false) Integer limit) {
        return assembler.toPage(
                service.listWorkflowTemplates(jdVersionId, cursor, limit),
                assembler::toWorkflowDetailsResponse);
    }

    @GetMapping("/workflow-template-versions/{templateVersionId}")
    WorkflowDetailsResponse workflow(@PlatformIdentifier @PathVariable String templateVersionId) {
        return assembler.toWorkflowDetailsResponse(service.getWorkflowTemplate(templateVersionId));
    }

    /**
     * 更新流程。
     */
    @PatchMapping("/workflow-template-versions/{templateVersionId}")
    WorkflowDetailsResponse updateWorkflow(
            @PlatformIdentifier @PathVariable String templateVersionId, @Valid @RequestBody WorkflowRequest request) {
        return assembler.toWorkflowDetailsResponse(
                service.updateWorkflowTemplate(templateVersionId, assembler.toWorkflowDTO(request)));
    }

    @PostMapping("/workflow-template-versions/{templateVersionId}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    WorkflowDetailsResponse cloneWorkflow(
            @PlatformIdentifier @PathVariable String templateVersionId,
            @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
        return assembler.toWorkflowDetailsResponse(
                service.cloneWorkflowTemplate(templateVersionId, key));
    }

    /**
     * 激活流程。
     */
    @PostMapping("/workflow-template-versions/{templateVersionId}/activate")
    WorkflowDetailsResponse activateWorkflow(
            @PlatformIdentifier @PathVariable String templateVersionId, @Valid @RequestBody VersionReasonRequest request) {
        return assembler.toWorkflowDetailsResponse(
                service.activateWorkflowTemplate(templateVersionId, assembler.toVersionReasonDTO(request)));
    }
}
