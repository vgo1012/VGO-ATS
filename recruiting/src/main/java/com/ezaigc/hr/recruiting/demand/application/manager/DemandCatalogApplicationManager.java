package com.ezaigc.hr.recruiting.demand.application.manager;

import static com.ezaigc.hr.common.web.error.PlatformExceptions.versionConflict;
import static com.ezaigc.hr.common.web.pagination.CursorPages.page;
import static com.ezaigc.hr.common.web.request.RequiredText.optional;
import static com.ezaigc.hr.common.web.request.RequiredText.require;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.DEFAULT_PAGE_LIMIT;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_COLLABORATORS_PER_DEMAND;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_JD_QUERY_LENGTH;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_PAGE_LIMIT;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorCount;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.common.web.pagination.StableCursor;
import com.ezaigc.hr.common.web.request.IdempotencyKey;
import com.ezaigc.hr.common.web.request.RequiredIdentifier;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.identity.api.IdentityDirectoryService;
import com.ezaigc.hr.identity.api.IdentityDirectoryService.UserReference;
import com.ezaigc.hr.recruiting.demand.application.support.ValidatedDemandHierarchy;
import com.ezaigc.hr.recruiting.demand.application.support.JdCityNames;
import com.ezaigc.hr.recruiting.demand.domain.dto.CreateClientDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.RecruitmentReadinessDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.CreateDemandDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.CreateJdVersionDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.PublicJdSourceDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.CreatePositionDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.CreateProjectDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.UpdateDemandDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.UpdateNameDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.UpdatePositionDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.ValidatedWorkflowStepDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.VersionReasonDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.WorkflowDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.WorkflowDetailsDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.WorkflowStepDraft;
import com.ezaigc.hr.recruiting.demand.domain.entity.ClientEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.IdempotencyRecordEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdCodeOptionEntity;
import com.ezaigc.hr.recruiting.demand.enums.JdCodeOptionKind;
import com.ezaigc.hr.recruiting.demand.service.JdCodeOptionPersistenceService;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdSourceEntity;
import com.ezaigc.hr.recruiting.demand.service.JdSourcePersistenceService;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.PositionEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.ProjectEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.WorkflowTemplateEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.WorkflowStepEntity;
import com.ezaigc.hr.recruiting.demand.enums.DemandActivationStatus;
import com.ezaigc.hr.recruiting.demand.enums.DemandStatus;
import com.ezaigc.hr.recruiting.demand.enums.JdSourceType;
import com.ezaigc.hr.recruiting.demand.enums.JdStatus;
import com.ezaigc.hr.recruiting.demand.enums.RecruitmentType;
import com.ezaigc.hr.recruiting.demand.enums.JdVersionStatus;
import com.ezaigc.hr.recruiting.demand.enums.WorkflowTemplateStatus;
import com.ezaigc.hr.recruiting.demand.service.DemandAuditEventPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandIdempotencyPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandOwnershipHistoryPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandWorkflowStepPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdVersionPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.PositionPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.ProjectPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.WorkflowTemplatePersistenceService;
import com.ezaigc.hr.recruiting.demand.service.validator.WorkflowTemplateValidator;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.CandidateScreeningStateQuery;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.ezaigc.hr.recruiting.demand.enums.WorkflowStepCode;

@Service
public class DemandCatalogApplicationManager {
    @Resource private SharedJdReadApplicationManager sharedJdRead;
    @Resource @Lazy private CandidateLibraryQueryService candidateLibrary;
    @Resource private CandidateScreeningStateQuery candidateScreening;
    @Resource private ApplicationPersistenceService applications;

    @Resource
    private JdCodeOptionPersistenceService jdCodeOptionPersistenceService;

    /**
     * ID 列表。
     */
    @Resource
    private JdSourcePersistenceService jdSourcePersistenceService;

    @Resource
    private PlatformIdGenerator ids;
    /**
     * 时钟。
     */
    @Resource
    private Clock clock;

    @Resource
    private IdentityDirectoryService identityDirectory;

    /**
     * 持久化。
     */
    @Resource
    private DemandCatalogPersistenceService persistence;

    @Resource
    private DemandAuditEventPersistenceService demandAuditEventPersistenceService;

    @Resource
    private DemandCollaboratorPersistenceService demandCollaboratorPersistenceService;

    @Resource
    private DemandEntityPersistenceService demandEntityPersistenceService;

    @Resource
    private DemandIdempotencyPersistenceService demandIdempotencyPersistenceService;

    @Resource
    private DemandOwnershipHistoryPersistenceService demandOwnershipHistoryPersistenceService;

    @Resource
    private DemandWorkflowStepPersistenceService demandWorkflowStepPersistenceService;

    @Resource
    private JdEntityPersistenceService jdEntityPersistenceService;

    @Resource
    private JdVersionPersistenceService jdVersionPersistenceService;

    @Resource
    private PositionPersistenceService positionPersistenceService;

    @Resource
    private ProjectPersistenceService projectPersistenceService;

    @Resource
    private WorkflowTemplatePersistenceService workflowTemplatePersistenceService;
    /**
     * 授权。
     */
    @Resource
    private AuthorizationService authorization;
    /**
     * 流程校验器。
     */
    @Resource
    private WorkflowTemplateValidator workflowValidator;
    @Resource
    private CursorCodec cursorCodec;

    /**
     * 列出客户列表。
     */
    @Transactional(readOnly = true)
    public CursorPage<ClientEntity> listClients(
            String status, String cursor, Integer requestedLimit) {
        CurrentActorSnapshot actor = authorization.currentActor();
        authorization.requirePermission("demand.read");
        int limit = limit(requestedLimit);
        String normalizedStatus = CommonUtils.upperToNull(status);
        String scope = actorScope("demand.clients", actor, normalizedStatus);
        StableCursor decoded = StableCursor.decode(cursorCodec, scope, cursor);
        return page(
                persistence.findClients(
                        normalizedStatus,
                        actor.getUserId(),
                        true,
                        actor.getOperationalManagedUserIds(),
                        actor.visibleDepartmentIds(),
                        time(decoded),
                        id(decoded),
                        limit + 1),
                limit,
                cursorCodec,
                scope,
                ClientEntity::getUpdateTime,
                ClientEntity::getId);
    }

    @Transactional(readOnly = true)
    public CursorCount countClients(String status) {
        CurrentActorSnapshot actor = authorization.currentActor();
        authorization.requirePermission("demand.read");
        String normalizedStatus = CommonUtils.upperToNull(status);
        return new CursorCount(persistence.countClients(
                normalizedStatus,
                actor.getUserId(),
                true,
                actor.getOperationalManagedUserIds(),
                actor.visibleDepartmentIds()), null, false);
    }

    @Transactional(readOnly = true)
    public ClientEntity getClient(String id) {
        ClientEntity client = requireClient(id);
        authorization.requirePermission("demand.read");
        return client;
    }

    /**
     * 创建客户。
     */
    @Transactional
    public ClientEntity createClient(CreateClientDTO command, String rawKey) {
        requireAdministrator();
        CurrentActorSnapshot actor = authorization.currentActor();
        authorization.requirePermission("demand.manage");
        String key = IdempotencyKey.fromHttpHeader(rawKey);
        String code = "SYS-" + fingerprint(actor.getUserId(), key).substring(0, 32).toUpperCase();
        String name = require(command.getName(), "name", 160);
        String fingerprint = fingerprint(code, name);
        ClientEntity replay = replay(key, "CLIENT_CREATE", fingerprint, this::findClientById);
        if (replay != null) {
            return replay;
        }
        if (persistence.countClientCode(code) > 0) {
            throw PlatformExceptions.conflict("CLIENT_CODE_DUPLICATE", "客户编码已存在");
        }
        Instant now = clock.instant();
        String id = ids.newId();
        persistence.insertClient(
                id, code, name, actor.getUserId(), actor.getHomeDepartmentId(), actor.getUserId(), now);
        remember(actor.getUserId(), key, "CLIENT_CREATE", fingerprint, id, now);
        audit("CLIENT_CREATED", actor.getUserId(), "CLIENT", id, null, now);
        return findClientById(id);
    }

    /**
     * 更新客户。
     */
    @Transactional
    public ClientEntity updateClient(String id, UpdateNameDTO command) {
        requireAdministrator();
        ClientEntity client = requireClient(id);
        authorization.requireAccess("demand.manage", target(client));
        CurrentActorSnapshot actor = authorization.currentActor();
        if (persistence.updateClient(
                id,
                require(command.getName(), "name", 160),
                command.getVersion(),
                actor.getUserId(),
                clock.instant())
                == 0) {
            throw versionConflict("CLIENT_VERSION_CONFLICT");
        }
        audit("CLIENT_UPDATED", actor.getUserId(), "CLIENT", id, null, clock.instant());
        return findClientById(id);
    }

    /**
     * 停用客户。
     */
    @Transactional
    public ClientEntity disableClient(String id, VersionReasonDTO command) {
        requireAdministrator();
        ClientEntity client = requireClient(id);
        authorization.requireAccess("demand.manage", target(client));
        CurrentActorSnapshot actor = authorization.currentActor();
        Instant now = clock.instant();
        if (persistence.disableClient(id, command.getVersion(), actor.getUserId(), now) == 0) {
            throw versionConflict("CLIENT_VERSION_CONFLICT");
        }
        audit(
                "CLIENT_DISABLED",
                actor.getUserId(),
                "CLIENT",
                id,
                require(command.getReason(), "reason", 500),
                now);
        return findClientById(id);
    }

    /**
     * 列出项目列表。
     */
    @Transactional(readOnly = true)
    public CursorPage<ProjectEntity> listProjects(
            String clientId, String status, String cursor, Integer requestedLimit) {
        ClientEntity client = requireClient(clientId);
        authorization.requirePermission("demand.read");
        CurrentActorSnapshot actor = authorization.currentActor();
        int limit = limit(requestedLimit);
        String normalizedStatus = CommonUtils.upperToNull(status);
        String scope = actorScope("demand.projects", actor, clientId, normalizedStatus);
        StableCursor decoded = StableCursor.decode(cursorCodec, scope, cursor);
        return page(
                projectPersistenceService.findProjects(
                        clientId,
                        normalizedStatus,
                        time(decoded),
                        id(decoded),
                        limit + 1),
                limit,
                cursorCodec,
                scope,
                ProjectEntity::getUpdateTime,
                ProjectEntity::getId);
    }

    @Transactional(readOnly = true)
    public ProjectEntity getProject(String id) {
        ProjectEntity project = requireProject(id);
        authorization.requireAccess("demand.read", target(requireClient(project.getClientId())));
        return project;
    }

    /**
     * 创建项目。
     */
    @Transactional
    public ProjectEntity createProject(String clientId, CreateProjectDTO command, String rawKey) {
        requireAdministrator();
        ClientEntity client = requireActiveClient(clientId);
        authorization.requireAccess("demand.manage", target(client));
        CurrentActorSnapshot actor = authorization.currentActor();
        String key = IdempotencyKey.fromHttpHeader(rawKey);
        String code = "SYS-" + fingerprint(actor.getUserId(), key).substring(0, 32).toUpperCase();
        String name = require(command.getName(), "name", 160);
        String fingerprint = fingerprint(clientId, code, name);
        ProjectEntity replay =
                replay(key, "PROJECT_CREATE", fingerprint, projectPersistenceService::findProject);
        if (replay != null) {
            return replay;
        }
        if (projectPersistenceService.countProjectCode(clientId, code) > 0) {
            throw PlatformExceptions.conflict("PROJECT_CODE_DUPLICATE", "同一客户下项目编码已存在");
        }
        Instant now = clock.instant();
        String id = ids.newId();
        projectPersistenceService.insertProject(id, clientId, code, name, actor.getUserId(), now);
        remember(actor.getUserId(), key, "PROJECT_CREATE", fingerprint, id, now);
        audit("PROJECT_CREATED", actor.getUserId(), "CLIENT_PROJECT", id, null, now);
        return projectPersistenceService.findProject(id);
    }

    /**
     * 更新项目。
     */
    @Transactional
    public ProjectEntity updateProject(String id, UpdateNameDTO command) {
        requireAdministrator();
        ProjectEntity project = requireProject(id);
        authorization.requireAccess("demand.manage", target(requireClient(project.getClientId())));
        CurrentActorSnapshot actor = authorization.currentActor();
        if (projectPersistenceService.updateProject(
                id,
                require(command.getName(), "name", 160),
                command.getVersion(),
                actor.getUserId(),
                clock.instant())
                == 0) {
            throw versionConflict("PROJECT_VERSION_CONFLICT");
        }
        audit("PROJECT_UPDATED", actor.getUserId(), "CLIENT_PROJECT", id, null, clock.instant());
        return projectPersistenceService.findProject(id);
    }

    /**
     * 停用项目。
     */
    @Transactional
    public ProjectEntity disableProject(String id, VersionReasonDTO command) {
        requireAdministrator();
        ProjectEntity project = requireProject(id);
        authorization.requireAccess("demand.manage", target(requireClient(project.getClientId())));
        CurrentActorSnapshot actor = authorization.currentActor();
        Instant now = clock.instant();
        if (projectPersistenceService.disableProject(id, command.getVersion(), actor.getUserId(), now) == 0) {
            throw versionConflict("PROJECT_VERSION_CONFLICT");
        }
        audit(
                "PROJECT_DISABLED",
                actor.getUserId(),
                "CLIENT_PROJECT",
                id,
                require(command.getReason(), "reason", 500),
                now);
        return projectPersistenceService.findProject(id);
    }

    /**
     * 列出职位列表。
     */
    @Transactional(readOnly = true)
    public CursorPage<PositionEntity> listPositions(
            String clientId, String projectId, String status, String cursor, Integer requestedLimit) {
        CurrentActorSnapshot actor = authorization.currentActor();
        authorization.requirePermission("demand.read");
        int limit = limit(requestedLimit);
        String normalizedStatus = CommonUtils.upperToNull(status);
        String scope = actorScope("demand.positions", actor, clientId, projectId, normalizedStatus);
        StableCursor decoded = StableCursor.decode(cursorCodec, scope, cursor);
        return page(
                persistence.findPositions(
                        clientId,
                        projectId,
                        normalizedStatus,
                        actor.getUserId(),
                        actor.hasCompanyWideScope(),
                        actor.getOperationalManagedUserIds(),
                        actor.visibleDepartmentIds(),
                        time(decoded),
                        id(decoded),
                        limit + 1),
                limit,
                cursorCodec,
                scope,
                PositionEntity::getUpdateTime,
                PositionEntity::getId);
    }

    @Transactional(readOnly = true)
    public PositionEntity getPosition(String id) {
        PositionEntity position = requirePosition(id);
        authorization.requireAccess("demand.read", target(requireClient(position.getClientId())));
        return position;
    }

    /**
     * 创建职位。
     */
    @Transactional
    public PositionEntity createPosition(CreatePositionDTO command, String rawKey) {
        requireAdministrator();
        ClientEntity client = requireActiveClient(command.getClientId());
        authorization.requireAccess("demand.manage", target(client));
        ProjectEntity project = requireProjectForClient(command.getProjectId(), client.getId(), true);
        CurrentActorSnapshot actor = authorization.currentActor();
        String key = IdempotencyKey.fromHttpHeader(rawKey);
        String code = "SYS-" + fingerprint(actor.getUserId(), key).substring(0, 32).toUpperCase();
        String name = require(command.getName(), "name", 160);
        String fingerprint =
                fingerprint(client.getId(), project == null ? "" : project.getId(), code, name);
        PositionEntity replay =
                replay(key, "POSITION_CREATE", fingerprint, positionPersistenceService::findPosition);
        if (replay != null) {
            return replay;
        }
        if (positionPersistenceService.countPositionCode(client.getId(), code) > 0) {
            throw PlatformExceptions.conflict("POSITION_CODE_DUPLICATE", "同一客户下岗位编码已存在");
        }
        Instant now = clock.instant();
        String id = ids.newId();
        positionPersistenceService.insertPosition(
                id,
                client.getId(),
                project == null ? null : project.getId(),
                code,
                name,
                actor.getUserId(),
                now);
        remember(actor.getUserId(), key, "POSITION_CREATE", fingerprint, id, now);
        audit("POSITION_CREATED", actor.getUserId(), "CLIENT_POSITION", id, null, now);
        return positionPersistenceService.findPosition(id);
    }

    /**
     * 更新职位。
     */
    @Transactional
    public PositionEntity updatePosition(String id, UpdatePositionDTO command) {
        requireAdministrator();
        PositionEntity position = requirePosition(id);
        ClientEntity client = requireClient(position.getClientId());
        authorization.requireAccess("demand.manage", target(client));
        ProjectEntity project = requireProjectForClient(command.getProjectId(), client.getId(), true);
        CurrentActorSnapshot actor = authorization.currentActor();
        if (positionPersistenceService.updatePosition(
                id,
                project == null ? null : project.getId(),
                require(command.getName(), "name", 160),
                command.getVersion(),
                actor.getUserId(),
                clock.instant())
                == 0) {
            throw versionConflict("POSITION_VERSION_CONFLICT");
        }
        audit("POSITION_UPDATED", actor.getUserId(), "CLIENT_POSITION", id, null, clock.instant());
        return positionPersistenceService.findPosition(id);
    }

    /**
     * 停用职位。
     */
    @Transactional
    public PositionEntity disablePosition(String id, VersionReasonDTO command) {
        requireAdministrator();
        PositionEntity position = requirePosition(id);
        authorization.requireAccess("demand.manage", target(requireClient(position.getClientId())));
        CurrentActorSnapshot actor = authorization.currentActor();
        Instant now = clock.instant();
        if (positionPersistenceService.disablePosition(id, command.getVersion(), actor.getUserId(), now) == 0) {
            throw versionConflict("POSITION_VERSION_CONFLICT");
        }
        audit(
                "POSITION_DISABLED",
                actor.getUserId(),
                "CLIENT_POSITION",
                id,
                require(command.getReason(), "reason", 500),
                now);
        return positionPersistenceService.findPosition(id);
    }

    /**
     * 列出招聘需求列表。
     */
    @Transactional(readOnly = true)
    public CursorPage<DemandEntity> listDemands(
            String status, String cursor, Integer requestedLimit) {
        CurrentActorSnapshot actor = authorization.currentActor();
        authorization.requirePermission("demand.read");
        int limit = limit(requestedLimit);
        String normalizedStatus = CommonUtils.upperToNull(status);
        String scope = actorScope("demand.demands", actor, normalizedStatus);
        StableCursor decoded = StableCursor.decode(cursorCodec, scope, cursor);
        return page(
                persistence.findDemands(
                        normalizedStatus,
                        actor.getUserId(),
                        actor.hasCompanyWideScope(),
                        actor.getOperationalManagedUserIds(),
                        actor.visibleDepartmentIdsFor("DEMAND"),
                        time(decoded),
                        id(decoded),
                        limit + 1),
                limit,
                cursorCodec,
                scope,
                DemandEntity::getUpdateTime,
                DemandEntity::getId);
    }

    /**
     * 获取招聘需求。
     */
    @Transactional(readOnly = true)
    public DemandEntity getDemand(String id) {
        DemandEntity demand = requireDemand(id);
        authorization.requireAccess("demand.read", target(demand));
        return demand;
    }

    /**
     * 创建招聘需求。
     */
    @Transactional
    public DemandEntity createDemand(CreateDemandDTO command, String rawKey) {
        requireAdministrator();
        CurrentActorSnapshot actor = authorization.currentActor();
        authorization.requirePermission("demand.manage");
        ValidatedDemandHierarchy hierarchy =
                validateHierarchy(command.getClientId(), command.getProjectId(), command.getPositionId());
        String ownerUserId =
                CommonUtils.stripOrElse(command.getOwnerUserId(), actor.getUserId());
        String ownerDepartmentId =
                CommonUtils.stripOrElse(
                        command.getOwnerDepartmentId(), actor.getHomeDepartmentId());
        requireAssignableOwner(actor, ownerUserId, ownerDepartmentId);
        String key = IdempotencyKey.fromHttpHeader(rawKey);
        String code = "SYS-" + fingerprint(actor.getUserId(), key).substring(0, 32).toUpperCase();
        String title = require(command.getTitle(), "title", 180);
        String type = recruitmentType(command.getRecruitmentType());
        int headcount = headcount(command.getHeadcount());
        Set<String> collaborators = collaboratorIds(command.getCollaboratorUserIds(), ownerUserId);
        String fingerprint =
                fingerprint(
                        code,
                        title,
                        hierarchy.getClientId(),
                        hierarchy.getProjectId(),
                        hierarchy.getPositionId(),
                        type,
                        Integer.toString(headcount),
                        ownerUserId,
                        ownerDepartmentId,
                        String.join(",", collaborators));
        DemandEntity replay =
                replay(key, "DEMAND_CREATE", fingerprint, demandEntityPersistenceService::findDemand);
        if (replay != null) {
            return replay;
        }
        if (demandEntityPersistenceService.countDemandCode(code) > 0) {
            throw PlatformExceptions.conflict("DEMAND_CODE_DUPLICATE", "招聘需求编码已存在");
        }
        Instant now = clock.instant();
        String id = ids.newId();
        demandEntityPersistenceService.insertDemand(
                id,
                code,
                title,
                hierarchy.getClientId(),
                hierarchy.getProjectId(),
                hierarchy.getPositionId(),
                type,
                headcount,
                trimNullable(command.getDescription(), 10000),
                ownerUserId,
                ownerDepartmentId,
                actor.getUserId(),
                now);
        replaceCollaborators(id, collaborators, actor.getUserId(), now);
        remember(actor.getUserId(), key, "DEMAND_CREATE", fingerprint, id, now);
        audit("DEMAND_CREATED", actor.getUserId(), "DEMAND", id, null, now);
        return demandEntityPersistenceService.findDemand(id);
    }

    /**
     * 更新招聘需求。
     */
    @Transactional
    public DemandEntity updateDemand(String id, UpdateDemandDTO command) {
        requireAdministrator();
        DemandEntity existing = requireDemand(id);
        authorization.requireAccess("demand.manage", target(existing));
        CurrentActorSnapshot actor = authorization.currentActor();
        ValidatedDemandHierarchy hierarchy =
                validateHierarchy(command.getClientId(), command.getProjectId(), command.getPositionId());
        String ownerUserId =
                CommonUtils.stripOrElse(command.getOwnerUserId(), existing.getOwnerUserId());
        String ownerDepartmentId =
                CommonUtils.stripOrElse(
                        command.getOwnerDepartmentId(), existing.getOwnerDepartmentId());
        requireAssignableOwner(actor, ownerUserId, ownerDepartmentId);
        Set<String> collaborators = collaboratorIds(command.getCollaboratorUserIds(), ownerUserId);
        Instant now = clock.instant();
        if (demandEntityPersistenceService.updateDemand(
                id,
                require(command.getTitle(), "title", 180),
                hierarchy.getClientId(),
                hierarchy.getProjectId(),
                hierarchy.getPositionId(),
                recruitmentType(command.getRecruitmentType()),
                headcount(command.getHeadcount()),
                trimNullable(command.getDescription(), 10000),
                ownerUserId,
                ownerDepartmentId,
                command.getVersion(),
                actor.getUserId(),
                now)
                == 0) {
            throw versionConflict("DEMAND_VERSION_CONFLICT");
        }
        // PATCH 未提供协作人字段时保留现有关系；显式空列表才表示清空。
        if (command.getCollaboratorUserIds() != null) {
            replaceCollaborators(id, collaborators, actor.getUserId(), now);
        }
        if (!existing.getOwnerUserId().equals(ownerUserId)
                || !existing.getOwnerDepartmentId().equals(ownerDepartmentId)) {
            String reason = require(command.getOwnershipReason(), "ownershipReason", 500);
            demandOwnershipHistoryPersistenceService.insertOwnershipHistory(
                    ids.newId(),
                    "DEMAND",
                    id,
                    existing.getOwnerUserId(),
                    ownerUserId,
                    existing.getOwnerDepartmentId(),
                    ownerDepartmentId,
                    reason,
                    actor.getUserId(),
                    now);
            demandAuditEventPersistenceService.insertAudit(
                    ids.newId(), "DEMAND_OWNERSHIP_CHANGED", actor.getUserId(), "DEMAND", id, reason, now);
        }
        audit("DEMAND_UPDATED", actor.getUserId(), "DEMAND", id, null, now);
        return demandEntityPersistenceService.findDemand(id);
    }

    /**
     * 删除招聘需求。
     */
    @Transactional
    public void deleteDemand(String id, VersionReasonDTO command) {
        requireAdministrator();
        DemandEntity demand = requireDemand(id);
        authorization.requireAccess("demand.manage", target(demand));
        if (jdEntityPersistenceService.countJdsForDemand(id) > 0) {
            throw PlatformExceptions.conflict("DEMAND_REFERENCED", "招聘需求已有 JD 版本，不能删除");
        }
        demandCollaboratorPersistenceService.deleteCollaborators(id);
        if (demandEntityPersistenceService.deleteDemand(id, command.getVersion()) == 0) {
            throw versionConflict("DEMAND_VERSION_CONFLICT");
        }
        audit(
                "DEMAND_DELETED",
                authorization.currentActor().getUserId(),
                "DEMAND",
                id,
                require(command.getReason(), "reason", 500),
                clock.instant());
    }

    @Transactional
    public DemandEntity transitionDemand(String id, String action, VersionReasonDTO command) {
        requireAdministrator();
        DemandEntity demand = requireDemand(id);
        authorization.requireAccess("demand.manage", target(demand));
        DemandStatus target =
                switch (action) {
                    case "activate" -> DemandStatus.ACTIVE;
                    case "pause" -> DemandStatus.PAUSED;
                    case "close" -> DemandStatus.CLOSED;
                    default -> throw new PlatformException(
                            HttpStatus.BAD_REQUEST, "DEMAND_ACTION_INVALID", "需求操作无效", false);
                };
        DemandStatus current;
        try {
            current = DemandStatus.fromCode(demand.getStatus());
        } catch (IllegalArgumentException exception) {
            throw PlatformExceptions.conflict("DEMAND_STATUS_INVALID", "当前需求状态无效");
        }
        if (!current.canTransitionTo(target)) {
            throw PlatformExceptions.conflict("DEMAND_TRANSITION_INVALID", "当前需求状态不能执行该操作");
        }
        String toStatus = target.name();
        CurrentActorSnapshot actor = authorization.currentActor();
        Instant now = clock.instant();
        if (demandEntityPersistenceService.transitionDemand(
                id, demand.getStatus(), toStatus, command.getVersion(), actor.getUserId(), now)
                == 0) {
            throw versionConflict("DEMAND_VERSION_CONFLICT");
        }
        audit(
                "DEMAND_" + toStatus,
                actor.getUserId(),
                "DEMAND",
                id,
                require(command.getReason(), "reason", 500),
                now);
        return demandEntityPersistenceService.findDemand(id);
    }

    @Transactional
    public JdVersionEntity addJdVersion(String demandId, CreateJdVersionDTO command, String rawKey) {
        DemandEntity demand = requireDemand(demandId);
        authorization.requireAccess("demand.manage", target(demand));
        if (DemandStatus.CLOSED.matches(demand.getStatus())) {
            throw PlatformExceptions.conflict("DEMAND_CLOSED", "已关闭需求不能新增 JD 版本");
        }
        CurrentActorSnapshot actor = authorization.currentActor();
        if (CommonUtils.stripToNull(command.getAppliedFromJdVersionId()) != null) {
            throw PlatformExceptions.unprocessable("JD_APPLICATION_ENTRY_REQUIRED", "应用公共 JD 请使用 JD 新建入口");
        }
        String key = IdempotencyKey.fromHttpHeader(rawKey);
        String title = require(command.getTitle(), "title", 180);
        String rawText = require(command.getRawText(), "rawText", 200000);
        String sourceType = CommonUtils.upperOrElse(command.getSourceType(), "MANUAL");
        if (!JdSourceType.supports(sourceType)) {
            throw PlatformExceptions.unprocessable("JD_SOURCE_INVALID", "JD 来源类型无效");
        }
        String regionName = CommonUtils.stripToNull(command.getWorkRegionName());
        String fingerprint = fingerprint(demandId, title, rawText, sourceType);
        if (regionName != null) fingerprint = fingerprint(fingerprint, regionName);
        JdVersionEntity replay =
                replay(key, "JD_VERSION_CREATE", fingerprint, jdVersionPersistenceService::findJdVersion);
        if (replay != null) {
            replay.setJdCode(requireJd(replay.getJdId()).getCode());
            return replay;
        }
        Instant now = clock.instant();
        String jdId = jdEntityPersistenceService.findJdIdByDemand(demandId);
        String regionCode;
        if (jdId == null) {
            if (command.getJobTypeCode() != null || command.getWorkRegionCode() != null) {
                throw PlatformExceptions.unprocessable("JD_CODE_INPUT_CONFLICT", "首次创建 JD 时请填写工作地区，不要传入编号维度代码");
            }
            if (regionName == null || regionName.length() > 80) {
                throw PlatformExceptions.unprocessable("JD_WORK_REGION_REQUIRED", "首次创建 JD 必须填写工作地区，长度不能超过 80 字");
            }
            if (title.length() > 80) {
                throw PlatformExceptions.unprocessable("JD_CODE_TITLE_TOO_LONG", "新增岗位名称不能超过 80 字，以便生成岗位编号");
            }
            identityDirectory.activeDepartment(demand.getOwnerDepartmentId());
            String typeCode = createJdCodeOption(JdCodeOptionKind.JOB_TYPE.getCode(), title).getCode();
            regionCode = createJdCodeOption(JdCodeOptionKind.REGION.getCode(), regionName).getCode();
            jdId = ids.newId();
            var jd = new JdEntity();
            jd.setId(jdId); jd.setDemandId(demandId); jd.setTitle(title);
            jd.setCode(allocateJdCode(demand.getOwnerDepartmentId(), typeCode, regionCode));
            jd.setJobTypeCode(typeCode); jd.setWorkRegionCode(regionCode);
            jd.setOwnerUserId(demand.getOwnerUserId()); jd.setOwnerDepartmentId(demand.getOwnerDepartmentId());
            jd.setHeadcount(demand.getHeadcount()); jd.setStatus(JdStatus.ACTIVE.getCode());
            jd.setCurrentVersionNo(0); jd.setVersion(0);
            jd.setCreateId(actor.getUserId()); jd.setCreateTime(now);
            jd.setUpdateId(actor.getUserId()); jd.setUpdateTime(now);
            if (!jdEntityPersistenceService.save(jd)) throw new IllegalStateException("JD 写入失败");
        } else {
            var existing = requireJd(jdId);
            if (existing.getAppliedFromJdVersionId() != null) {
                existing = requireLockedJd(jdId);
                requirePrivateApplication(command);
                var currentVersion = jdVersionPersistenceService.findJdVersions(jdId, null, null, 1).stream()
                        .findFirst().orElseThrow(() -> PlatformExceptions.notFound("JD_VERSION_NOT_FOUND", "JD 当前版本不存在"));
                command.setTitle(title);
                requireApplicationContent(currentVersion, command);
                title = command.getTitle(); rawText = command.getRawText();
                if (existing.isContentShared()) {
                    if (jdEntityPersistenceService.updateRecruitmentMetadata(jdId, existing.getHeadcount(),
                            existing.getRecruitmentCycleDays(), existing.getOwnerUserId(), existing.getOwnerDepartmentId(),
                            false, existing.getVersion(), actor.getUserId(), now) != 1) throw versionConflict("JD_VERSION_CONFLICT");
                    audit("JD_CONTENT_SHARING_CHANGED", actor.getUserId(), "JD", jdId, "true -> false", now);
                }
            }
            if (regionName != null) {
                throw PlatformExceptions.unprocessable("JD_WORK_REGION_UPDATE_UNSUPPORTED", "已有 JD 的工作地区请通过 JD 编辑入口修改");
            }
            regionCode = requireJd(jdId).getWorkRegionCode();
        }
        Integer current = persistence.lockJdVersionNo(jdId);
        int versionNo = Objects.requireNonNull(current, "JD disappeared") + 1;
        String versionId = ids.newId();
        jdVersionPersistenceService.insertJdVersion(
                versionId, jdId, versionNo, title, rawText, sourceType, regionCode, actor.getUserId(), now);
        var frozenVersion = jdVersionPersistenceService.findJdVersion(versionId);
        frozenVersion.setClientId(demand.getClientId()); frozenVersion.setRecruitmentType(demand.getRecruitmentType());
        if (!jdVersionPersistenceService.updateById(frozenVersion)) throw new IllegalStateException("JD 版本客户快照写入失败");
        jdEntityPersistenceService.updateJdVersionNo(jdId, title, versionNo, actor.getUserId(), now);
        remember(actor.getUserId(), key, "JD_VERSION_CREATE", fingerprint, versionId, now);
        audit("JD_VERSION_CREATED", actor.getUserId(), "JD_VERSION", versionId, null, now);
        var createdVersion = jdVersionPersistenceService.findJdVersion(versionId);
        createdVersion.setJdCode(requireJd(jdId).getCode());
        return createdVersion;
    }

    @Transactional(readOnly = true)
    public CursorPage<JdEntity> listJds(
            String query, String clientId, String status, String cursor, Integer requestedLimit) {
        CurrentActorSnapshot actor = authorization.currentActor();
        authorization.requirePermission("demand.read");
        int limit = limit(requestedLimit);
        String normalizedQuery = JdCityNames.searchQuery(optional(query, "query", MAX_JD_QUERY_LENGTH));
        String normalizedStatus = CommonUtils.upperToNull(status);
        String scope = actorScope("demand.jds", actor, normalizedQuery, clientId, normalizedStatus);
        StableCursor decoded = StableCursor.decode(cursorCodec, scope, cursor);
        return page(
                persistence.findJds(
                        normalizedQuery,
                        clientId,
                        normalizedStatus,
                        actor.getUserId(),
                        actor.hasCompanyWideScope(),
                        actor.getOperationalManagedUserIds(),
                        actor.visibleDepartmentIdsFor("JD"),
                        time(decoded),
                        id(decoded),
                        limit + 1),
                limit,
                cursorCodec,
                scope,
                JdEntity::getCreateTime,
                JdEntity::getId);
    }

    @Transactional(readOnly = true)
    public CursorCount countJds(String query, String clientId, String status) {
        CurrentActorSnapshot actor = authorization.currentActor();
        authorization.requirePermission("demand.read");
        String normalizedQuery = JdCityNames.searchQuery(optional(query, "query", MAX_JD_QUERY_LENGTH));
        String normalizedStatus = CommonUtils.upperToNull(status);
        long count = persistence.countJds(
                normalizedQuery,
                clientId,
                normalizedStatus,
                actor.getUserId(),
                actor.hasCompanyWideScope(),
                actor.getOperationalManagedUserIds(),
                actor.visibleDepartmentIdsFor("JD"));
        return new CursorCount(count, null, false);
    }

    @Transactional(readOnly = true)
    public CursorPage<PublicJdSourceDTO> listPublicJdSources(String query, String cursor, Integer requestedLimit) {
        CurrentActorSnapshot actor = authorization.currentActor();
        authorization.requirePermission("demand.read");
        String normalizedQuery = JdCityNames.searchQuery(optional(query, "query", MAX_JD_QUERY_LENGTH));
        int limit = limit(requestedLimit);
        String scope = actorScope("demand.public-jd-sources", actor, normalizedQuery);
        StableCursor decoded = StableCursor.decode(cursorCodec, scope, cursor);
        var result = page(persistence.findPublicJdSources(normalizedQuery,
                        JdStatus.ACTIVE.getCode(), JdVersionStatus.PUBLISHED.getCode(),
                        time(decoded), id(decoded), limit + 1),
                limit, cursorCodec, scope, PublicJdSourceDTO::getCreateTime, PublicJdSourceDTO::getId);
        enrichPublicJdOwners(result.getItems());
        return result;
    }

    @Transactional(readOnly = true)
    public CursorCount countPublicJdSources(String query) {
        authorization.currentActor();
        authorization.requirePermission("demand.read");
        return new CursorCount(persistence.countPublicJdSources(
                JdCityNames.searchQuery(optional(query, "query", MAX_JD_QUERY_LENGTH)),
                JdStatus.ACTIVE.getCode(), JdVersionStatus.PUBLISHED.getCode()), null, false);
    }

    @Transactional(readOnly = true)
    public PublicJdSourceDTO getPublicJdSource(String jdId) {
        authorization.currentActor();
        authorization.requirePermission("demand.read");
        PublicJdSourceDTO source = persistence.findPublicJdSource(jdId,
                JdStatus.ACTIVE.getCode(), JdVersionStatus.PUBLISHED.getCode());
        if (source == null) throw PlatformExceptions.notFound("JD_SOURCE_NOT_FOUND", "公共 JD 不存在或已关闭");
        enrichPublicJdOwners(List.of(source));
        return source;
    }

    private void enrichPublicJdOwners(List<PublicJdSourceDTO> sources) {
        Set<String> userIds = sources.stream().map(PublicJdSourceDTO::getOwnerUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> departmentIds = sources.stream().map(PublicJdSourceDTO::getOwnerDepartmentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        var userNames = userIds.isEmpty() ? Map.<String, String>of() : identityDirectory.userNames(userIds);
        var departmentNames = departmentIds.isEmpty() ? Map.<String, String>of() : identityDirectory.departmentNames(departmentIds);
        for (var source : sources) {
            source.setOwnerUserName(source.getOwnerUserId() == null ? null : userNames.get(source.getOwnerUserId()));
            source.setOwnerDepartmentName(source.getOwnerDepartmentId() == null ? null : departmentNames.get(source.getOwnerDepartmentId()));
            source.setWorkRegionName(JdCityNames.display(source.getWorkRegionName()));
        }
    }

    /** 兼容旧客户端，保存的原文已经是可使用的 JD。 */
    @Transactional
    public JdVersionEntity createSourceJdDraft(CreateJdVersionDTO command, String rawKey) {
        CurrentActorSnapshot actor = authorization.currentActor();
        if (CommonUtils.stripToNull(command.getOwnerDepartmentId()) == null) {
            command.setOwnerDepartmentId(actor.getHomeDepartmentId());
        }
        if (CommonUtils.stripToNull(command.getRecruitmentType()) == null) {
            command.setRecruitmentType(command.getClientId() == null ? "INTERNAL" : "EXTERNAL");
        }
        return createJd(command, rawKey);
    }

    @Transactional
    public JdVersionEntity createJd(CreateJdVersionDTO command, String rawKey) {
        authorization.requirePermission("demand.manage");
        if (command.getJobTypeCode() != null || command.getWorkRegionCode() != null) {
            throw PlatformExceptions.unprocessable("JD_CODE_INPUT_CONFLICT", "自动编号使用归属部门、岗位名称和工作地区，请勿传入维度代码");
        }
        var actor = authorization.currentActor();
        String sourceVersionId = CommonUtils.stripToNull(command.getAppliedFromJdVersionId());
        if (sourceVersionId != null) {
            authorization.requirePermission("demand.read");
            if (command.getOwnerUserId() != null
                    && !Objects.equals(actor.getUserId(), command.getOwnerUserId())) {
                throw PlatformExceptions.forbidden("JD_APPLICATION_OWNER_FORBIDDEN", "应用公共 JD 只能归属本人");
            }
            requireApplicationDepartment(actor, command.getOwnerDepartmentId());
            requirePrivateApplication(command);
            command.setOwnerUserId(actor.getUserId());
            command.setAppliedFromJdVersionId(sourceVersionId);
        }
        validateNewJdInput(command, actor);
        UserReference owner = resolveJdOwner(actor, command.getOwnerUserId());
        if (Boolean.TRUE.equals(command.getContentShared())
                && !actor.isSuperAdmin() && !Objects.equals(owner.userId(), actor.getUserId())) {
            throw PlatformExceptions.forbidden("JD_CONTENT_SHARE_FORBIDDEN", "仅 JD 所属人或超级管理员可以公开内容");
        }
        String regionName = CommonUtils.stripToNull(command.getWorkRegionName());
        if (regionName == null || regionName.length() > 80) {
            throw PlatformExceptions.unprocessable("JD_WORK_REGION_REQUIRED", "新增 JD 必须填写工作区域，长度不能超过 80 字");
        }
        if (command.getTitle().length() > 80) {
            throw PlatformExceptions.unprocessable("JD_CODE_TITLE_TOO_LONG", "新增岗位名称不能超过 80 字，以便生成岗位编号");
        }
        regionName = JdCityNames.requireCity(regionName);
        command.setWorkRegionName(regionName);
        String key = IdempotencyKey.fromHttpHeader(rawKey);
        String bodyFingerprint = jdFingerprint(command);
        String requestFingerprint = fingerprint(owner.userId(), command.getOwnerDepartmentId(), bodyFingerprint,
                Boolean.toString(command.isForceNew()), String.valueOf(command.getHeadcount()),
                String.valueOf(command.getRecruitmentCycleDays()), command.getJobTypeCode(), command.getWorkRegionCode(),
                sourceVersionId, String.valueOf(Boolean.TRUE.equals(command.getContentShared())));
        if (regionName != null) requestFingerprint = fingerprint(requestFingerprint, regionName);
        JdVersionEntity previous = replay(key, "JD_CREATE", requestFingerprint, jdVersionPersistenceService::findJdVersion);
        if (previous != null) {
            var jd = requireLockedJd(previous.getJdId());
            authorization.requireAccess("demand.read", target(jd));
            previous.setJdCode(jd.getCode());
            return previous;
        }
        if (sourceVersionId != null) {
            jdEntityPersistenceService.lockCodeAllocation();
            JdVersionEntity sourceVersion = jdVersionPersistenceService.findJdVersion(sourceVersionId);
            if (sourceVersion == null) {
                throw PlatformExceptions.notFound("JD_SOURCE_NOT_FOUND", "公共 JD 来源不可用，请重新选择来源");
            }
            JdEntity source = persistence.findJdForUpdate(sourceVersion.getJdId());
            if (source == null || !source.isContentShared() || source.getAppliedFromJdVersionId() != null) {
                throw PlatformExceptions.notFound("JD_SOURCE_NOT_FOUND", "公共 JD 来源不可用，请重新选择来源");
            }
            if (source.isDeleted() || !JdStatus.ACTIVE.matches(source.getStatus())
                    || !Objects.equals(source.getCurrentVersionNo(), sourceVersion.getVersionNo())
                    || !JdVersionStatus.PUBLISHED.matches(sourceVersion.getStatus())) {
                throw PlatformExceptions.conflict("JD_SOURCE_CHANGED", "公共 JD 已关闭或版本已变化，请重新应用");
            }
            requireApplicationContent(sourceVersion, command);
        }
        Instant now = clock.instant();
        JdEntity jd = new JdEntity();
        String typeCode = command.getJobTypeCode();
        String regionCode = command.getWorkRegionCode();
        typeCode = createJdCodeOption(JdCodeOptionKind.JOB_TYPE.getCode(), command.getTitle()).getCode();
        regionCode = createJdCodeOption(JdCodeOptionKind.REGION.getCode(), regionName).getCode();
        jd.setId(ids.newId());
        jd.setJobTypeCode(typeCode); jd.setWorkRegionCode(regionCode);
        jd.setCode(allocateJdCode(command.getOwnerDepartmentId(), typeCode, regionCode));
        jd.setOwnerUserId(owner.userId()); jd.setOwnerDepartmentId(command.getOwnerDepartmentId());
        jd.setAppliedFromJdVersionId(sourceVersionId);
        jd.setSourceOwnerDepartmentId(command.getOwnerDepartmentId());
        jd.setTitle(command.getTitle()); jd.setClientId(command.getClientId());
        jd.setRecruitmentType(command.getRecruitmentType());
        jd.setHeadcount(command.getHeadcount());
        jd.setRecruitmentCycleDays(command.getRecruitmentCycleDays());
        jd.setPublicVisible(false); jd.setContentShared(Boolean.TRUE.equals(command.getContentShared()));
        jd.setDistinction(command.getDistinction());
        jd.setStatus(JdStatus.ACTIVE.getCode()); jd.setCurrentVersionNo(1); jd.setVersion(0);
        jd.setCreateId(actor.getUserId()); jd.setCreateTime(now); jd.setUpdateId(actor.getUserId()); jd.setUpdateTime(now);
        try {
            if (!jdEntityPersistenceService.save(jd)) throw new IllegalStateException("JD 写入失败");
        } catch (DuplicateKeyException conflict) {
            throw PlatformExceptions.conflict("JD_DUPLICATE", "JD 编号或请求发生冲突，请刷新后重试");
        }
        var version = saveCatalogVersion(jd, command, now);
        if (jd.isContentShared()) audit("JD_CONTENT_SHARING_CHANGED", actor.getUserId(), "JD", jd.getId(), "false -> true", now);
        remember(actor.getUserId(), key, "JD_CREATE", requestFingerprint, version.getId(), now);
        recordManualSource(version.getId(), key, now);
        audit("JD_CREATED", actor.getUserId(), "JD", jd.getId(), null, now);
        return version;
    }

    @Transactional
    public JdVersionEntity updateJd(String jdId, CreateJdVersionDTO command, String rawKey) {
        if (command.getWorkRegionName() != null) {
            authorization.requirePermission("demand.manage");
            // 与新建一致：先锁编号分配，再锁 JD，避免碰撞检查反向等待。
            jdEntityPersistenceService.lockCodeAllocation();
        }
        var jd = requireLockedJd(jdId);
        requireJdManager(jd);
        if (command.getWorkRegionName() != null && Objects.equals(
                JdCityNames.display(command.getWorkRegionName()), JdCityNames.display(jd.getWorkRegionName()))) {
            command.setWorkRegionName(null);
        }
        validateUpdatedJdInput(command, jd);
        JdVersionEntity currentVersion = jdVersionPersistenceService.findJdVersions(jdId, null, null, 1).stream()
                .findFirst().orElseThrow(() -> PlatformExceptions.notFound("JD_VERSION_NOT_FOUND", "JD 当前版本不存在"));
        if (jd.getAppliedFromJdVersionId() != null) {
            requirePrivateApplication(command);
            requireApplicationContent(currentVersion, command);
            command.setContentShared(false);
        }
        boolean previousContentShared = jd.isContentShared();
        boolean contentShared = command.getContentShared() == null ? previousContentShared : command.getContentShared();
        boolean sharingChanged = contentShared != previousContentShared;
        if (sharingChanged && jd.getAppliedFromJdVersionId() == null && !authorization.currentActor().isSuperAdmin()
                && !Objects.equals(jd.getOwnerUserId(), authorization.currentActor().getUserId())) {
            throw PlatformExceptions.forbidden("JD_CONTENT_SHARE_FORBIDDEN", "仅 JD 所属人或超级管理员可以修改公开状态");
        }
        if (contentShared && command.getTitle().length() > 80) {
            throw PlatformExceptions.unprocessable("JD_SHARED_TITLE_TOO_LONG", "公开 JD 的岗位名称不能超过 80 字");
        }
        if (command.getWorkRegionName() != null) {
            String regionName = JdCityNames.requireCity(require(command.getWorkRegionName(), "workRegionName", 80));
            command.setWorkRegionName(regionName);
            if (command.getJobTypeCode() == null || !Objects.equals(jd.getTitle(), command.getTitle())) {
                command.setJobTypeCode(createJdCodeOption(JdCodeOptionKind.JOB_TYPE.getCode(), command.getTitle()).getCode());
            }
            command.setWorkRegionCode(createJdCodeOption(JdCodeOptionKind.REGION.getCode(), regionName).getCode());
        }
        if (command.getVersion() == null) throw PlatformExceptions.unprocessable("VERSION_REQUIRED", "更新需要当前 JD 版本");
        String key = IdempotencyKey.fromHttpHeader(rawKey);
        String requestFingerprint = fingerprint(jdId, command.getOwnerUserId(), command.getOwnerDepartmentId(), jdFingerprint(command),
                String.valueOf(command.getHeadcount()), String.valueOf(command.getRecruitmentCycleDays()),
                command.getVersion().toString(), command.getJobTypeCode(), command.getWorkRegionCode(),
                String.valueOf(command.getContentShared()));
        var previous = replay(key, "JD_UPDATE", requestFingerprint, jdVersionPersistenceService::findJdVersion);
        if (previous != null) {
            previous.setJdCode(jd.getCode());
            return previous;
        }
        String previousOwnerUserId = jd.getOwnerUserId();
        String previousOwnerDepartmentId = jd.getOwnerDepartmentId();
        boolean ownerUserChanged = !Objects.equals(previousOwnerUserId, command.getOwnerUserId());
        boolean ownerDepartmentChanged =
                !Objects.equals(previousOwnerDepartmentId, command.getOwnerDepartmentId());
        boolean contentUnchanged = Objects.equals(currentVersion.getTitle(), command.getTitle())
                && Objects.equals(normalizedRawText(currentVersion.getRawText()), normalizedRawText(command))
                && Objects.equals(currentVersion.getClientId(), command.getClientId())
                && Objects.equals(currentVersion.getRecruitmentType(), command.getRecruitmentType())
                && Objects.equals(jd.getDistinction(), command.getDistinction())
                && Objects.equals(jd.getJobTypeCode(), command.getJobTypeCode())
                && Objects.equals(jd.getWorkRegionCode(), command.getWorkRegionCode());
        if (contentUnchanged) {
            int occupiedHeadcount = persistence.countOccupiedHeadcountForUpdate(jdId);
            if (command.getHeadcount() < occupiedHeadcount) {
                throw PlatformExceptions.conflict("JD_HEADCOUNT_BELOW_OCCUPIED",
                        "HC 不能低于当前已入职人数 " + occupiedHeadcount + "，请先核对入职状态");
            }
            Instant now = clock.instant();
            if (jdEntityPersistenceService.updateRecruitmentMetadata(jdId, command.getHeadcount(),
                    command.getRecruitmentCycleDays(), command.getOwnerUserId(), command.getOwnerDepartmentId(), contentShared, command.getVersion(),
                    authorization.currentActor().getUserId(), now) != 1) {
                throw versionConflict("JD_VERSION_CONFLICT");
            }
            recordJdOwnershipChange(jd, previousOwnerUserId, command.getOwnerUserId(),
                    previousOwnerDepartmentId, command.getOwnerDepartmentId(), now);
            remember(authorization.currentActor().getUserId(), key, "JD_UPDATE", requestFingerprint,
                    currentVersion.getId(), now);
            audit("JD_RECRUITMENT_METADATA_UPDATED", authorization.currentActor().getUserId(), "JD", jdId, null, now);
            if (sharingChanged) audit("JD_CONTENT_SHARING_CHANGED", authorization.currentActor().getUserId(), "JD", jdId,
                    previousContentShared + " -> " + contentShared, now);
            currentVersion.setJdCode(jd.getCode());
            return currentVersion;
        }
        var current = jd.getCurrentVersionNo();
        var now = clock.instant();
        jd.setTitle(command.getTitle()); jd.setClientId(command.getClientId());
        jd.setJobTypeCode(command.getJobTypeCode()); jd.setWorkRegionCode(command.getWorkRegionCode());
        jd.setRecruitmentType(command.getRecruitmentType());
        jd.setOwnerUserId(command.getOwnerUserId());
        jd.setOwnerDepartmentId(command.getOwnerDepartmentId());
        int occupiedHeadcount = persistence.countOccupiedHeadcountForUpdate(jdId);
        if (command.getHeadcount() < occupiedHeadcount) {
            throw PlatformExceptions.conflict("JD_HEADCOUNT_BELOW_OCCUPIED",
                    "HC 不能低于当前已入职人数 " + occupiedHeadcount + "，请先核对入职状态");
        }
        jd.setHeadcount(command.getHeadcount());
        jd.setRecruitmentCycleDays(command.getRecruitmentCycleDays());
        jd.setDistinction(command.getDistinction()); jd.setPublicVisible(false); jd.setContentShared(contentShared);
        jd.setCurrentVersionNo(Objects.requireNonNull(current) + 1);
        jd.setUpdateId(authorization.currentActor().getUserId()); jd.setUpdateTime(now);
        try {
            if (jdEntityPersistenceService.updateCatalog(jd, command.getVersion()) != 1)
                throw versionConflict("JD_VERSION_CONFLICT");
        } catch (DuplicateKeyException conflict) {
            throw PlatformExceptions.conflict("JD_DUPLICATE", "JD 编号或请求发生冲突，请刷新后重试");
        }
        if (ownerUserChanged || ownerDepartmentChanged) {
            recordJdOwnershipChange(jd, previousOwnerUserId, command.getOwnerUserId(),
                    previousOwnerDepartmentId, command.getOwnerDepartmentId(), now);
        }
        var version = saveCatalogVersion(jd, command, now);
        remember(jd.getUpdateId(), key, "JD_UPDATE", requestFingerprint, version.getId(), now);
        recordManualSource(version.getId(), key, now);
        audit("JD_VERSION_CREATED", jd.getUpdateId(), "JD_VERSION", version.getId(), null, now);
        if (sharingChanged) audit("JD_CONTENT_SHARING_CHANGED", jd.getUpdateId(), "JD", jdId,
                previousContentShared + " -> " + contentShared, now);
        return version;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void deleteJd(String jdId, long version) {
        var jd = requireLockedJd(jdId);
        authorization.requirePermission("demand.manage");
        requireJdOwner(jd);
        if (jd.isDeleted()) return;
        if (jd.getVersion() != version) throw versionConflict("JD_VERSION_CONFLICT");
        // JD 锁后建立同一占用快照，防止模块间流转漏检；不反向锁 Candidate。
        var versionIds = jdVersionPersistenceService.findVersionIds(jdId);
        var locations = new ArrayList<String>();
        if (candidateLibrary.hasPersonalPoolResumes(versionIds)) locations.add("我的简历");
        if (candidateScreening.hasActiveAdmissions(versionIds)) locations.add("智能筛选");
        if (applications.hasActiveApplicationsForJd(jdId)) locations.add("招聘流程");
        if (!locations.isEmpty()) {
            throw PlatformExceptions.conflict("JD_IN_USE", "该 JD 关联的简历仍在"
                    + String.join("、", locations) + "中，不能删除。请先完成或移出相关流程后重试。");
        }
        if (jdEntityPersistenceService.softDelete(jdId, version, authorization.currentActor().getUserId(), clock.instant()) != 1)
            throw versionConflict("JD_VERSION_CONFLICT");
        audit("JD_DELETED", authorization.currentActor().getUserId(), "JD", jdId, "用户主动删除", clock.instant());
    }

    @Transactional(readOnly = true)
    public CursorPage<JdSourceEntity> listJdSources(String versionId, String cursor, Integer requestedLimit) {
        var jd = requireJdForVersion(versionId, "demand.read");
        var actor = authorization.currentActor();
        boolean scopeAll = actor.hasCompanyWideScope();
        int limit = limit(requestedLimit);
        String scope = actorScope("jd.sources", actor, versionId);
        var decoded = StableCursor.decode(cursorCodec, scope, cursor);
        return page(jdSourcePersistenceService.findPage(versionId, actor.getUserId(), scopeAll, decoded, limit + 1),
                limit, cursorCodec, scope, JdSourceEntity::getCreateTime, JdSourceEntity::getId);
    }

    private void recordManualSource(String versionId, String key, Instant now) {
        var row = new JdSourceEntity();
        row.setId(ids.newId()); row.setJdVersionId(versionId); row.setSourceType("MANUAL");
        row.setSourceKey(fingerprint(key)); row.setOwnerUserId(authorization.currentActor().getUserId());
        row.setCreateId(row.getOwnerUserId()); row.setCreateTime(now);
        row.setUpdateId(row.getOwnerUserId()); row.setUpdateTime(now);
        try {
            if (!jdSourcePersistenceService.save(row)) throw new IllegalStateException("JD 来源写入失败");
        } catch (DuplicateKeyException duplicate) {
            throw PlatformExceptions.conflict("JD_SOURCE_CONFLICT", "此来源已保存，请刷新后重试");
        }
    }

    private void validateNewJdInput(CreateJdVersionDTO command, CurrentActorSnapshot actor) {
        command.setOwnerDepartmentId(
                RequiredIdentifier.require(command.getOwnerDepartmentId(), "ownerDepartmentId"));
        requireAssignableJdDepartment(actor, command.getOwnerDepartmentId());
        command.setRecruitmentType(requireRecruitmentType(command.getRecruitmentType()));
        validateJdInput(command);
    }

    private void validateUpdatedJdInput(CreateJdVersionDTO command, JdEntity existing) {
        CurrentActorSnapshot actor = authorization.currentActor();
        String requestedOwnerUserId = CommonUtils.stripToNull(command.getOwnerUserId());
        if (requestedOwnerUserId == null) {
            requestedOwnerUserId = existing.getOwnerUserId();
        } else if (!Objects.equals(requestedOwnerUserId, existing.getOwnerUserId())) {
            if (!actor.isSuperAdmin()) {
                throw PlatformExceptions.forbidden(
                        "JD_OWNER_CHANGE_ADMIN_REQUIRED", "仅超级管理员可以变更 JD 所属人");
            }
            requestedOwnerUserId = resolveJdOwner(actor, requestedOwnerUserId).userId();
        }
        command.setOwnerUserId(requestedOwnerUserId);
        String requestedDepartmentId = CommonUtils.stripToNull(command.getOwnerDepartmentId());
        if (requestedDepartmentId == null) {
            requestedDepartmentId = existing.getOwnerDepartmentId();
        } else if (!Objects.equals(requestedDepartmentId, existing.getOwnerDepartmentId())) {
            if (existing.getAppliedFromJdVersionId() != null && !actor.isSuperAdmin()) {
                requireApplicationDepartment(actor, requestedDepartmentId);
            } else {
                requireAssignableJdDepartment(actor, requestedDepartmentId);
            }
        }
        command.setOwnerDepartmentId(requestedDepartmentId);
        String requestedType = CommonUtils.stripToNull(command.getRecruitmentType());
        command.setRecruitmentType(requestedType == null
                ? existing.getRecruitmentType() : requireRecruitmentType(requestedType));
        if (command.getWorkRegionName() == null && command.getWorkRegionCode() != null
                && !Objects.equals(command.getWorkRegionCode(), existing.getWorkRegionCode())) {
            var region = jdCodeOptionPersistenceService.findCodeOption(JdCodeOptionKind.REGION.getCode(), command.getWorkRegionCode());
            if (region == null) throw PlatformExceptions.unprocessable("JD_CODE_OPTION_NOT_FOUND", "工作地区不存在");
            JdCityNames.requireCity(region.getName());
        }
        if (command.getJobTypeCode() == null) command.setJobTypeCode(existing.getJobTypeCode());
        if (command.getWorkRegionCode() == null) command.setWorkRegionCode(existing.getWorkRegionCode());
        if (command.getDistinction() == null) command.setDistinction(existing.getDistinction());
        String originalRawText = command.getRawText();
        validateJdInput(command);
        if (existing.getAppliedFromJdVersionId() != null) command.setRawText(originalRawText);
    }

    private void recordJdOwnershipChange(
            JdEntity jd,
            String previousOwnerUserId,
            String targetOwnerUserId,
            String previousDepartmentId,
            String targetDepartmentId,
            Instant now) {
        boolean ownerChanged = !Objects.equals(previousOwnerUserId, targetOwnerUserId);
        boolean departmentChanged = !Objects.equals(previousDepartmentId, targetDepartmentId);
        if (!ownerChanged && !departmentChanged) {
            return;
        }
        String actorId = authorization.currentActor().getUserId();
        String reason = ownerChanged
                ? departmentChanged ? "编辑 JD 所属人和归属部门" : "编辑 JD 所属人"
                : "编辑 JD 归属部门";
        demandOwnershipHistoryPersistenceService.insertOwnershipHistory(
                ids.newId(), "JD", jd.getId(), previousOwnerUserId, targetOwnerUserId,
                previousDepartmentId, targetDepartmentId, reason, actorId, now);
        audit("JD_OWNERSHIP_CHANGED", actorId, "JD", jd.getId(), reason, now);
    }

    private void validateJdInput(CreateJdVersionDTO command) {
        validateJdCodeDimensions(command);
        command.setTitle(require(command.getTitle(), "title", 180));
        String originalRawText = command.getRawText();
        command.setRawText(require(originalRawText, "rawText", 200000));
        if (command.getAppliedFromJdVersionId() != null) command.setRawText(originalRawText);
        command.setDistinction(optional(command.getDistinction(), "distinction", 180));
        command.setClientId(trimNullable(command.getClientId(), 26));
        if (command.getHeadcount() == null || command.getHeadcount() < 1 || command.getHeadcount() > 10000) {
            throw PlatformExceptions.unprocessable("JD_HEADCOUNT_INVALID", "HC 必填且必须在 1～10000 之间");
        }
        if (command.getRecruitmentCycleDays() == null
                || command.getRecruitmentCycleDays() < 1
                || command.getRecruitmentCycleDays() > 3650) {
            throw PlatformExceptions.unprocessable(
                    "JD_RECRUITMENT_CYCLE_INVALID", "招聘周期必填且必须在 1～3650 天之间");
        }
        if (RecruitmentType.EXTERNAL.matches(command.getRecruitmentType())) {
            if (command.getClientId() == null) {
                throw PlatformExceptions.unprocessable(
                        "JD_CLIENT_REQUIRED", "外部招聘必须选择客户");
            }
            requireActiveClient(command.getClientId());
        } else if (command.getClientId() != null) {
            throw PlatformExceptions.unprocessable(
                    "JD_INTERNAL_CLIENT_FORBIDDEN", "内部招聘不能选择客户");
        }
        if (command.isPublicVisible())
            throw PlatformExceptions.unprocessable("PUBLIC_JD_UNSUPPORTED", "JD 不再支持公共可见");
    }

    private void validateJdCodeDimensions(CreateJdVersionDTO command) {
        String type = CommonUtils.stripToNull(command.getJobTypeCode());
        String region = CommonUtils.stripToNull(command.getWorkRegionCode());
        if (type == null && region == null) {
            command.setJobTypeCode(null); command.setWorkRegionCode(null);
            return;
        }
        if (type == null || region == null || !type.matches("(?:0[1-9]|[1-9][0-9]|00[1-9]|0[1-9][0-9]|[1-9][0-9]{2})")
                || !region.matches("(?:0[1-9]|[1-9][0-9]|00[1-9]|0[1-9][0-9]|[1-9][0-9]{2})")) {
            throw PlatformExceptions.unprocessable("JD_CODE_DIMENSIONS_REQUIRED", "请同时选择岗位类型和工作区域");
        }
        type = String.format(Locale.ROOT, "%03d", Integer.parseInt(type));
        region = String.format(Locale.ROOT, "%03d", Integer.parseInt(region));
        if (jdCodeOptionPersistenceService.findCodeOption(JdCodeOptionKind.JOB_TYPE.getCode(), type) == null
                || jdCodeOptionPersistenceService.findCodeOption(JdCodeOptionKind.REGION.getCode(), region) == null) {
            throw PlatformExceptions.unprocessable("JD_CODE_OPTION_NOT_FOUND", "岗位类型或工作区域不存在，请刷新后重新选择");
        }
        command.setJobTypeCode(type); command.setWorkRegionCode(region);
    }

    @Transactional(readOnly = true)
    public List<JdCodeOptionEntity> listJdCodeOptions() {
        authorization.requirePermission("demand.read");
        if (!authorization.currentActor().hasCompanyWideScope()) {
            throw PlatformExceptions.forbidden("JD_CODE_OPTIONS_SCOPE_REQUIRED", "编号字典包含岗位名称，仅全公司可见范围可查询");
        }
        return jdCodeOptionPersistenceService.findCodeOptions();
    }

    private String allocateJdCode(String departmentId, String typeCode, String regionCode) {
        String departmentCode = jdEntityPersistenceService.departmentCode(departmentId);
        if (departmentCode == null) {
            throw PlatformExceptions.conflict("JD_DEPARTMENT_CODES_EXHAUSTED", "两位 JD 部门码已用完，最多支持 99 个部门");
        }
        return jdEntityPersistenceService.nextCode(departmentCode, typeCode, regionCode);
    }

    @Transactional
    public JdCodeOptionEntity createJdCodeOption(String kind, String name) {
        authorization.requirePermission("demand.manage");
        if (!JdCodeOptionKind.JOB_TYPE.matches(kind) && !JdCodeOptionKind.REGION.matches(kind)) {
            throw PlatformExceptions.unprocessable("JD_CODE_OPTION_KIND_INVALID", "编号选项类型无效");
        }
        String normalizedName = require(name, "name", 80);
        if (JdCodeOptionKind.REGION.matches(kind)) normalizedName = JdCityNames.requireCity(normalizedName);
        jdEntityPersistenceService.lockCodeAllocation();
        var existing = jdCodeOptionPersistenceService.findCodeOptionByName(kind, normalizedName);
        if (existing == null && JdCodeOptionKind.REGION.matches(kind)) {
            existing = jdCodeOptionPersistenceService.findCodeOptionByName(kind, JdCityNames.shortName(normalizedName));
        }
        if (existing != null) return existing;
        String lastCode = jdCodeOptionPersistenceService.lastOptionCode(kind);
        int next = lastCode == null ? 1 : Integer.parseInt(lastCode) + 1;
        if (next > 999) {
            throw PlatformExceptions.conflict("JD_CODE_OPTIONS_EXHAUSTED", "当前类型的三位编号已用完，最多支持 999 项");
        }
        var option = new JdCodeOptionEntity();
        String actorId = authorization.currentActor().getUserId();
        Instant now = clock.instant();
        option.setId(ids.newId()); option.setKind(kind); option.setName(normalizedName);
        option.setCode(String.format(Locale.ROOT, "%03d", next));
        option.setCreateId(actorId); option.setCreateTime(now); option.setUpdateId(actorId); option.setUpdateTime(now);
        if (jdCodeOptionPersistenceService.insertCodeOption(option) != 1) {
            throw new IllegalStateException("JD 编号选项写入失败");
        }
        audit("JD_CODE_OPTION_CREATED", actorId, "JD_CODE_OPTION", option.getId(), kind, now);
        return option;
    }

    private void requireApplicationDepartment(CurrentActorSnapshot actor, String departmentId) {
        identityDirectory.activeDepartment(departmentId);
        if (!actor.affiliatedDepartmentIds().contains(departmentId)) {
            throw PlatformExceptions.forbidden("JD_APPLICATION_DEPARTMENT_FORBIDDEN", "应用 JD 只能选择本人的主部门或支持部门");
        }
    }

    private static void requirePrivateApplication(CreateJdVersionDTO command) {
        if (Boolean.TRUE.equals(command.getContentShared())) {
            throw PlatformExceptions.forbidden("JD_APPLICATION_SHARE_FORBIDDEN", "应用的 JD 不允许再次公开");
        }
    }

    private static void requireApplicationContent(JdVersionEntity frozen, CreateJdVersionDTO command) {
        if (!Objects.equals(frozen.getTitle(), command.getTitle())) {
            throw PlatformExceptions.unprocessable("JD_APPLICATION_TITLE_IMMUTABLE", "应用 JD 不能修改岗位名称");
        }
        if (!Objects.equals(normalizedRawText(frozen.getRawText()), normalizedRawText(command))) {
            throw PlatformExceptions.unprocessable("JD_APPLICATION_CONTENT_IMMUTABLE", "应用 JD 不能修改 JD 原文");
        }
        command.setTitle(frozen.getTitle());
        command.setRawText(frozen.getRawText());
    }

    private void requireAssignableJdDepartment(CurrentActorSnapshot actor, String departmentId) {
        identityDirectory.activeDepartment(departmentId);
        if (!actor.hasCompanyWideScope() && !actor.affiliatedDepartmentIds().contains(departmentId)) {
            throw PlatformExceptions.forbidden(
                    "OWNER_DEPARTMENT_FORBIDDEN", "JD 归属部门超出可分配范围");
        }
    }

    private static String requireRecruitmentType(String value) {
        String normalized = CommonUtils.upperToNull(value);
        if (!RecruitmentType.supports(normalized)) {
            throw PlatformExceptions.unprocessable(
                    "RECRUITMENT_TYPE_INVALID", "招聘类型只允许 INTERNAL 或 EXTERNAL");
        }
        return normalized;
    }

    private UserReference resolveJdOwner(CurrentActorSnapshot actor, String requestedOwnerUserId) {
        String ownerUserId = CommonUtils.stripToNull(requestedOwnerUserId);
        if (ownerUserId == null || ownerUserId.equals(actor.getUserId())) {
            return new UserReference(actor.getUserId(), actor.getDisplayName(), actor.getHomeDepartmentId());
        }
        if (!actor.hasCompanyWideScope() && !actor.getManagedUserIds().contains(ownerUserId)) {
            throw PlatformExceptions.forbidden(
                    "OWNER_ASSIGNMENT_FORBIDDEN", "只能把 JD 分配给本人或管理范围内人员");
        }
        UserReference owner = identityDirectory.activeUser(ownerUserId);
        if (!identityDirectory.isRecruiterOwner(ownerUserId)) {
            throw PlatformExceptions.unprocessable(
                    "JD_OWNER_ROLE_INVALID", "JD 负责人必须拥有招聘业务角色");
        }
        return owner;
    }

    private String jdFingerprint(CreateJdVersionDTO command) {
        return fingerprint(command.getOwnerDepartmentId(), command.getRecruitmentType(),
                command.getClientId(), command.getTitle(), normalizedRawText(command),
                command.getDistinction());
    }

    private static String normalizedRawText(CreateJdVersionDTO command) {
        return command.getRawText().replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String normalizedRawText(String rawText) {
        return rawText.replace("\r\n", "\n").replace('\r', '\n');
    }

    private JdVersionEntity saveCatalogVersion(JdEntity jd, CreateJdVersionDTO command, Instant now) {
        JdVersionEntity version = new JdVersionEntity();
        version.setId(ids.newId()); version.setJdId(jd.getId()); version.setVersionNo(jd.getCurrentVersionNo());
        version.setJdCode(jd.getCode());
        version.setContentOwnerUserId(jd.getOwnerUserId());
        // 正文不参与 JD 去重，历史内容指纹不再占用新版本。
        version.setTitle(jd.getTitle()); version.setRawText(command.getRawText()); version.setSourceType(JdSourceType.MANUAL.getCode());
        version.setClientId(jd.getClientId()); version.setRecruitmentType(jd.getRecruitmentType());
        version.setWorkRegionCode(jd.getWorkRegionCode());
        version.setStatus(JdVersionStatus.PUBLISHED.getCode()); version.setVersion(0);
        version.setCreateId(authorization.currentActor().getUserId()); version.setCreateTime(now);
        version.setUpdateId(authorization.currentActor().getUserId()); version.setUpdateTime(now);
        try {
            if (!jdVersionPersistenceService.save(version)) throw new IllegalStateException("JD 版本写入失败");
        } catch (DuplicateKeyException conflict) {
            throw PlatformExceptions.conflict("JD_DUPLICATE", "相同 JD 内容正在保存，请刷新后选择已有 JD");
        }
        String templateId = ids.newId();
        if (workflowTemplatePersistenceService.insertWorkflowTemplate(templateId, version.getId(), 1,
                jd.getRecruitmentType(), version.getCreateId(), now) != 1)
            throw new IllegalStateException("默认招聘流程写入失败");
        var drafts = standardSteps(jd.getRecruitmentType()).stream().map(code -> new WorkflowStepDraft(code, null)).toList();
        insertSteps(templateId, workflowValidator.validate(jd.getRecruitmentType(), drafts), now);
        if (workflowTemplatePersistenceService.activateWorkflowTemplate(templateId, 0L, version.getCreateId(), now) != 1)
            throw new IllegalStateException("默认招聘流程激活失败");
        audit("WORKFLOW_DEFAULT_ACTIVATED", version.getCreateId(), "JD_VERSION", version.getId(), "原文保存后自动采用标准流程", now);
        return version;
    }

    private JdEntity requireLockedJd(String jdId) {
        var jd = persistence.findJdForUpdate(jdId);
        if (jd == null) throw PlatformExceptions.notFound("JD_NOT_FOUND", "JD 不存在");
        return jd;
    }

    private void requireJdOwner(JdEntity jd) {
        var actor = authorization.currentActor();
        if (!actor.hasCompanyWideScope()
                && !Objects.equals(jd.getOwnerUserId(), actor.getUserId()))
            throw PlatformExceptions.forbidden(
                    "JD_MANAGEMENT_FORBIDDEN", "仅 JD 本人、人力负责人或超级管理员可以维护此原文");
    }

    private void requireJdManager(JdEntity jd) {
        authorization.requirePermission("demand.manage");
        requireJdOwner(jd);
        if (jd.isDeleted()) throw PlatformExceptions.conflict("JD_DELETED", "JD 已删除，不能用于新业务");
    }

    private void requireAdministrator() {
        if (!authorization.currentActor().isSuperAdmin())
            throw PlatformExceptions.forbidden("ADMIN_REQUIRED", "公共资料仅由管理员维护");
    }

    @Transactional(readOnly = true)
    public JdVersionEntity getJdVersion(String jdVersionId) {
        JdVersionEntity version = jdVersionPersistenceService.findJdVersion(jdVersionId);
        if (version == null) {
            throw PlatformExceptions.notFound("JD_VERSION_NOT_FOUND", "JD 版本不存在");
        }
        JdEntity jd = requireJd(version.getJdId());
        authorization.currentActor();
        try {
            authorization.requireAccess("demand.read", target(jd));
        } catch (PlatformException denied) {
            if (denied.status() != HttpStatus.FORBIDDEN || !sharedJdRead.isShared(jdVersionId)) throw denied;
        }
        version.setJdCode(jd.getCode());
        return version;
    }

    @Transactional(readOnly = true)
    public CursorPage<JdVersionEntity> listJdVersions(
            String jdId, String cursor, Integer requestedLimit) {
        JdEntity jd = requireJd(jdId);
        authorization.requireAccess("demand.read", target(jd));
        CurrentActorSnapshot actor = authorization.currentActor();
        int limit = limit(requestedLimit);
        String scope = actorScope("demand.jd-versions", actor, jdId);
        StableCursor decoded = StableCursor.decode(cursorCodec, scope, cursor);
        var versions = jdVersionPersistenceService.findJdVersions(jdId, time(decoded), id(decoded), limit + 1);
        versions.forEach(version -> version.setJdCode(jd.getCode()));
        return page(
                versions,
                limit,
                cursorCodec,
                scope,
                JdVersionEntity::getCreateTime,
                JdVersionEntity::getId);
    }

    @Transactional(readOnly = true)
    public CursorPage<JdVersionEntity> listDemandJdVersions(
            String demandId, String cursor, Integer requestedLimit) {
        DemandEntity demand = requireDemand(demandId);
        authorization.requireAccess("demand.read", target(demand));
        String jdId = jdEntityPersistenceService.findJdIdByDemand(demandId);
        if (jdId == null) {
            return new CursorPage<>(List.of(), null, false);
        }
        return listJdVersions(jdId, cursor, requestedLimit);
    }

    /**
     * 创建流程模板。
     */
    @Transactional
    public WorkflowDetailsDTO createWorkflowTemplate(
            String jdVersionId, WorkflowDTO command, String rawKey) {
        JdEntity demand = requireWorkflowManager(jdVersionId);
        CurrentActorSnapshot actor = authorization.currentActor();
        String recruitmentType =
                CommonUtils.upperOrElse(
                        command.getRecruitmentType(), demand.getRecruitmentType());
        if (!recruitmentType.equals(demand.getRecruitmentType())) {
            throw PlatformExceptions.unprocessable("WORKFLOW_RECRUITMENT_TYPE_MISMATCH", "流程类型必须与招聘需求一致");
        }
        var steps =
                workflowValidator.validate(
                        recruitmentType, Objects.requireNonNullElse(command.getSteps(), List.of()));
        String key = IdempotencyKey.fromHttpHeader(rawKey);
        String fingerprint = fingerprint(jdVersionId, recruitmentType, steps.toString());
        WorkflowTemplateEntity replay =
                replay(
                        key,
                        "WORKFLOW_CREATE",
                        fingerprint,
                        workflowTemplatePersistenceService::findWorkflowTemplate);
        if (replay != null) {
            return details(replay);
        }
        Instant now = clock.instant();
        String id = ids.newId();
        int versionNo = persistence.findMaxWorkflowVersionNo(jdVersionId) + 1;
        workflowTemplatePersistenceService.insertWorkflowTemplate(
                id, jdVersionId, versionNo, recruitmentType, actor.getUserId(), now);
        insertSteps(id, steps, now);
        remember(actor.getUserId(), key, "WORKFLOW_CREATE", fingerprint, id, now);
        audit("WORKFLOW_TEMPLATE_CREATED", actor.getUserId(), "WORKFLOW_TEMPLATE", id, null, now);
        return details(workflowTemplatePersistenceService.findWorkflowTemplate(id));
    }

    /**
     * 列出流程模板列表。
     */
    @Transactional(readOnly = true)
    public CursorPage<WorkflowDetailsDTO> listWorkflowTemplates(
            String jdVersionId, String cursor, Integer requestedLimit) {
        requireJdForVersion(jdVersionId, "demand.read");
        CurrentActorSnapshot actor = authorization.currentActor();
        int limit = limit(requestedLimit);
        String scope = actorScope("demand.workflow-templates", actor, jdVersionId);
        StableCursor decoded = StableCursor.decode(cursorCodec, scope, cursor);
        List<WorkflowTemplateEntity> rows =
                workflowTemplatePersistenceService.findWorkflowTemplates(jdVersionId, time(decoded), id(decoded), limit + 1);
        var templatePage = page(
                rows,
                limit,
                cursorCodec,
                scope,
                WorkflowTemplateEntity::getCreateTime,
                WorkflowTemplateEntity::getId);
        var steps = workflowStepsByTemplate(templatePage.getItems());
        return templatePage.map(template -> details(template, steps.getOrDefault(template.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public WorkflowDetailsDTO getWorkflowTemplate(String id) {
        WorkflowTemplateEntity template = requireWorkflow(id);
        requireJdForVersion(template.getJdVersionId(), "demand.read");
        return details(template);
    }

    /**
     * 更新流程模板。
     */
    @Transactional
    public WorkflowDetailsDTO updateWorkflowTemplate(String id, WorkflowDTO command) {
        WorkflowTemplateEntity template = requireWorkflow(id);
        requireWorkflowManager(template.getJdVersionId());
        if (!WorkflowTemplateStatus.DRAFT.matches(template.getStatus())) {
            throw PlatformExceptions.conflict("WORKFLOW_TEMPLATE_IMMUTABLE", "已激活流程模板不可原地修改，请复制新草稿");
        }
        String type =
                CommonUtils.upperOrElse(
                        command.getRecruitmentType(), template.getRecruitmentType());
        var steps =
                workflowValidator.validate(
                        type, Objects.requireNonNullElse(command.getSteps(), List.of()));
        CurrentActorSnapshot actor = authorization.currentActor();
        Instant now = clock.instant();
        if (workflowTemplatePersistenceService.updateWorkflowTemplate(id, type, command.getVersion(), actor.getUserId(), now)
                == 0) {
            throw versionConflict("WORKFLOW_TEMPLATE_VERSION_CONFLICT");
        }
        demandWorkflowStepPersistenceService.deleteWorkflowSteps(id);
        insertSteps(id, steps, now);
        audit("WORKFLOW_TEMPLATE_UPDATED", actor.getUserId(), "WORKFLOW_TEMPLATE", id, null, now);
        return details(workflowTemplatePersistenceService.findWorkflowTemplate(id));
    }

    @Transactional
    public WorkflowDetailsDTO cloneWorkflowTemplate(String id, String rawKey) {
        WorkflowTemplateEntity source = requireWorkflow(id);
        requireWorkflowManager(source.getJdVersionId());
        CurrentActorSnapshot actor = authorization.currentActor();
        String key = IdempotencyKey.fromHttpHeader(rawKey);
        String fingerprint = fingerprint(id, Long.toString(source.getVersion()));
        WorkflowTemplateEntity replay =
                replay(
                        key,
                        "WORKFLOW_CLONE",
                        fingerprint,
                        workflowTemplatePersistenceService::findWorkflowTemplate);
        if (replay != null) {
            return details(replay);
        }
        List<WorkflowStepDraft> drafts =
                demandWorkflowStepPersistenceService.findWorkflowSteps(id).stream()
                        .map(
                                step ->
                                        new WorkflowStepDraft(
                                                WorkflowStepCode.valueOf(step.getStepCode()),
                                                step.getDefaultFollowUpChannel()))
                        .toList();
        var steps = workflowValidator.validate(source.getRecruitmentType(), drafts);
        Instant now = clock.instant();
        String cloneId = ids.newId();
        int versionNo = persistence.findMaxWorkflowVersionNo(source.getJdVersionId()) + 1;
        workflowTemplatePersistenceService.insertWorkflowTemplate(
                cloneId,
                source.getJdVersionId(),
                versionNo,
                source.getRecruitmentType(),
                actor.getUserId(),
                now);
        insertSteps(cloneId, steps, now);
        remember(actor.getUserId(), key, "WORKFLOW_CLONE", fingerprint, cloneId, now);
        audit(
                "WORKFLOW_TEMPLATE_CLONED",
                actor.getUserId(),
                "WORKFLOW_TEMPLATE",
                cloneId,
                "source=" + id,
                now);
        return details(workflowTemplatePersistenceService.findWorkflowTemplate(cloneId));
    }

    /**
     * 激活流程模板。
     */
    @Transactional
    public WorkflowDetailsDTO activateWorkflowTemplate(String id, VersionReasonDTO command) {
        WorkflowTemplateEntity template = requireWorkflow(id);
        requireWorkflowManager(template.getJdVersionId());
        if (!WorkflowTemplateStatus.DRAFT.matches(template.getStatus())) {
            throw PlatformExceptions.conflict("WORKFLOW_TEMPLATE_IMMUTABLE", "只有流程草稿可以激活");
        }
        List<WorkflowStepDraft> drafts =
                demandWorkflowStepPersistenceService.findWorkflowSteps(id).stream()
                        .map(
                                step ->
                                        new WorkflowStepDraft(
                                                WorkflowStepCode.valueOf(step.getStepCode()),
                                                step.getDefaultFollowUpChannel()))
                        .toList();
        workflowValidator.validate(template.getRecruitmentType(), drafts);
        CurrentActorSnapshot actor = authorization.currentActor();
        Instant now = clock.instant();
        persistence.lockWorkflowTemplates(template.getJdVersionId());
        workflowTemplatePersistenceService.supersedeActiveWorkflow(template.getJdVersionId(), actor.getUserId(), now);
        if (workflowTemplatePersistenceService.activateWorkflowTemplate(id, command.getVersion(), actor.getUserId(), now) == 0) {
            throw versionConflict("WORKFLOW_TEMPLATE_VERSION_CONFLICT");
        }
        audit(
                "WORKFLOW_TEMPLATE_ACTIVATED",
                actor.getUserId(),
                "WORKFLOW_TEMPLATE",
                id,
                require(command.getReason(), "reason", 500),
                now);
        return details(workflowTemplatePersistenceService.findWorkflowTemplate(id));
    }

    @Transactional(readOnly = true)
    public JdEntity getJd(String jdId) {
        JdEntity jd = requireJd(jdId);
        authorization.requireAccess("demand.read", target(jd));
        jd.setOccupiedHeadcount(persistence.countOccupiedHeadcount(jdId));
        return jd;
    }

    @Transactional(readOnly = true)
    public RecruitmentReadinessDTO recruitmentReadiness(String jdVersionId) {
        JdVersionEntity version = jdVersionPersistenceService.findJdVersion(jdVersionId);
        if (version == null) throw PlatformExceptions.notFound("JD_VERSION_NOT_FOUND", "JD 版本不存在");
        JdEntity jd = requireJd(version.getJdId());
        authorization.requireAccess("recruitment.manage", target(jd));
        DemandEntity demand = jd.getDemandId() == null ? null : requireDemand(jd.getDemandId());
        boolean allowed = Objects.equals(jd.getOwnerUserId(), authorization.currentActor().getUserId())
                || demand != null && demandCollaboratorPersistenceService.findCollaboratorIds(demand.getId()).contains(authorization.currentActor().getUserId());
        var active = workflowTemplatePersistenceService.findActiveWorkflow(jdVersionId);
        List<WorkflowDetailsDTO> drafts = new ArrayList<>();
        Instant cursorTime = null;
        String cursorId = null;
        while (true) {
            var rows = workflowTemplatePersistenceService.findWorkflowTemplates(jdVersionId, cursorTime, cursorId, 100);
            var draftRows = rows.stream().filter(item -> WorkflowTemplateStatus.DRAFT.matches(item.getStatus())).toList();
            var steps = workflowStepsByTemplate(draftRows);
            draftRows.stream().map(template -> details(template, steps.getOrDefault(template.getId(), List.of()))).forEach(drafts::add);
            if (rows.size() < 100) break;
            cursorTime = rows.getLast().getCreateTime(); cursorId = rows.getLast().getId();
        }
        var readiness = new RecruitmentReadinessDTO(jd.getId(), jdVersionId, version.getTitle(), jd.getDemandId(),
                version.getRecruitmentType() == null ? jd.getRecruitmentType() : version.getRecruitmentType(), !jd.isDeleted() && allowed, active == null ? null : active.getId(),
                active == null ? null : details(active),
                List.copyOf(drafts), standardSteps(version.getRecruitmentType() == null ? jd.getRecruitmentType() : version.getRecruitmentType()).stream().map(WorkflowStepCode::getCode).toList());
        readiness.setJdCode(jd.getCode());
        readiness.setCanManageWorkflow(!jd.isDeleted() && (authorization.currentActor().hasCompanyWideScope()
                || Objects.equals(jd.getOwnerUserId(), authorization.currentActor().getUserId())
                || demand != null && demandProcessingAllowed(demand)));
        readiness.setClientId(version.getRecruitmentType() == null ? jd.getClientId() : version.getClientId());
        var client = readiness.getClientId() == null ? null : persistence.findClient(readiness.getClientId());
        readiness.setClientName(client == null ? null : client.getName());
        int occupiedHeadcount = persistence.countOccupiedHeadcount(jd.getId());
        readiness.setHeadcount(jd.getHeadcount());
        readiness.setOccupiedHeadcount(occupiedHeadcount);
        readiness.setRemainingHeadcount(jd.getHeadcount() == null ? null
                : Math.max(0, jd.getHeadcount() - occupiedHeadcount));
        return readiness;
    }

    @Transactional
    public RecruitmentReadinessDTO activateStandardWorkflow(String jdVersionId, String reason, String key) {
        JdEntity demand = requireWorkflowManager(jdVersionId);
        require(reason, "reason", 500);
        persistence.lockWorkflowTemplates(jdVersionId);
        if (workflowTemplatePersistenceService.findActiveWorkflow(jdVersionId) != null) return recruitmentReadiness(jdVersionId);
        if (persistence.findMaxWorkflowVersionNo(jdVersionId) > 0) {
            throw PlatformExceptions.conflict("WORKFLOW_CONFIGURATION_EXISTS", "已存在流程配置，请明确选择并激活已有草稿");
        }
        WorkflowDTO workflow = new WorkflowDTO();
        workflow.setRecruitmentType(demand.getRecruitmentType());
        workflow.setSteps(standardSteps(demand.getRecruitmentType()).stream().map(code -> new WorkflowStepDraft(code, null)).toList());
        WorkflowDetailsDTO created = createWorkflowTemplate(jdVersionId, workflow, key);
        VersionReasonDTO activation = new VersionReasonDTO();
        activation.setVersion(created.getTemplate().getVersion()); activation.setReason(reason);
        activateWorkflowTemplate(created.getTemplate().getId(), activation);
        return recruitmentReadiness(jdVersionId);
    }

    @Transactional
    public JdEntity bindSourceJdToDemand(String jdId, String demandId, String reason, String rawKey) {
        requireAdministrator();
        JdEntity jd = requireJd(jdId);
        authorization.requireAccess("recruitment.manage", target(jd));
        DemandEntity demand = requireDemand(demandId);
        authorization.requireAccess("recruitment.manage", target(demand));
        requireDemandProcessor(demand);
        String cleanReason = require(reason, "reason", 500);
        String key = IdempotencyKey.fromHttpHeader(rawKey);
        String requestFingerprint = fingerprint(jdId, demandId);
        JdEntity previous = replay(key, "JD_DEMAND_BIND", requestFingerprint, persistence::findJd);
        if (previous != null) return previous;
        if (Objects.equals(jd.getDemandId(), demandId)) return jd;
        if (jd.getDemandId() != null) throw PlatformExceptions.conflict("JD_ALREADY_BOUND", "此 JD 已关联其他正式需求");
        if (!Objects.equals(jd.getOwnerUserId(), authorization.currentActor().getUserId()))
            throw PlatformExceptions.forbidden("JD_BINDING_FORBIDDEN", "仅来源 JD 归属 HR 可以关联正式需求");
        if (jdEntityPersistenceService.countJdsForDemand(demandId) > 0)
            throw PlatformExceptions.conflict("DEMAND_JD_ALREADY_EXISTS", "目标需求已有 JD，请选择尚未关联 JD 的需求");
        var actor = authorization.currentActor();
        Instant now = clock.instant();
        try {
            if (jdEntityPersistenceService.bindDemand(jdId, demandId, actor.getUserId(), now) == 0)
                throw PlatformExceptions.conflict("JD_BINDING_CONFLICT", "JD 关联已变化，请刷新后重试");
        } catch (DuplicateKeyException duplicate) {
            throw PlatformExceptions.conflict("DEMAND_JD_ALREADY_EXISTS", "目标需求已关联 JD，请刷新后重试");
        }
        remember(actor.getUserId(), key, "JD_DEMAND_BIND", requestFingerprint, jdId, now);
        audit("JD_DEMAND_BOUND", actor.getUserId(), "JD", jdId, cleanReason, now);
        return requireJd(jdId);
    }

    private static List<WorkflowStepCode> standardSteps(String type) {
        return RecruitmentType.EXTERNAL.matches(type)
                ? List.of(WorkflowStepCode.CANDIDATE_COMMUNICATION, WorkflowStepCode.DECISION, WorkflowStepCode.OFFER)
                : List.of(WorkflowStepCode.CANDIDATE_COMMUNICATION, WorkflowStepCode.INTERVIEW_ROUND_1, WorkflowStepCode.DECISION, WorkflowStepCode.OFFER);
    }

    private JdEntity requireWorkflowManager(String jdVersionId) {
        var jd = requireJdForVersion(jdVersionId, "recruitment.manage");
        if (!authorization.currentActor().hasCompanyWideScope() && (!Objects.equals(jd.getOwnerUserId(), authorization.currentActor().getUserId())
                && (jd.getDemandId() == null || !demandProcessingAllowed(requireDemand(jd.getDemandId())))))
            throw PlatformExceptions.forbidden("JD_WORKFLOW_FORBIDDEN", "仅负责人、明确协作人或管理员可以配置招聘流程");
        if (jd.isDeleted()) throw PlatformExceptions.conflict("JD_DELETED", "JD 已删除");
        return jd;
    }

    private void requireDemandProcessor(DemandEntity demand) {
        if (!demandProcessingAllowed(demand)) throw PlatformExceptions.forbidden("RECRUITMENT_PROCESSING_FORBIDDEN", "仅归属 HR 或需求协作人可以配置招聘流程");
    }

    private boolean demandProcessingAllowed(DemandEntity demand) {
        var actor = authorization.currentActor();
        String actorId = actor.getUserId();
        return actor.hasCompanyWideScope() || Objects.equals(demand.getOwnerUserId(), actorId)
                || actor.getManagedUserIds().contains(demand.getOwnerUserId())
                || demandCollaboratorPersistenceService.findCollaboratorIds(demand.getId()).contains(actorId);
    }

    private JdEntity requireJdForVersion(String jdVersionId, String permission) {
        var version = jdVersionPersistenceService.findJdVersion(jdVersionId);
        if (version == null) throw PlatformExceptions.notFound("JD_VERSION_NOT_FOUND", "JD 版本不存在");
        var jd = requireJd(version.getJdId());
        authorization.requireAccess(permission, target(jd));
        if (version.getRecruitmentType() != null) jd.setRecruitmentType(version.getRecruitmentType());
        return jd;
    }

    /**
     * 新增步骤列表。
     */
    private void insertSteps(String templateId, List<ValidatedWorkflowStepDTO> steps, Instant now) {
        List<WorkflowStepEntity> entities = new ArrayList<>(steps.size());
        for (var step : steps) {
            WorkflowStepEntity entity = new WorkflowStepEntity();
            entity.setId(ids.newId());
            entity.setTemplateVersionId(templateId);
            entity.setStepCode(step.getCode().name());
            entity.setSequenceNo(step.getSequenceNo());
            entity.setCandidateOptional(step.isCandidateOptional());
            entity.setDefaultFollowUpChannel(step.getDefaultFollowUpChannel());
            entity.setCreateTime(now);
            entities.add(entity);
        }
        if (!demandWorkflowStepPersistenceService.insertWorkflowSteps(entities)) {
            throw new IllegalStateException("招聘流程步骤批量写入失败");
        }
    }

    private WorkflowDetailsDTO details(WorkflowTemplateEntity template) {
        return details(template, demandWorkflowStepPersistenceService.findWorkflowSteps(template.getId()));
    }

    /** 按当前页所需模板批量读取，分组时保留模板内的步骤顺序。 */
    private Map<String, List<WorkflowStepEntity>> workflowStepsByTemplate(List<WorkflowTemplateEntity> templates) {
        if (templates.isEmpty()) return Map.of();
        return demandWorkflowStepPersistenceService.findWorkflowStepsByTemplateIds(
                templates.stream().map(WorkflowTemplateEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(WorkflowStepEntity::getTemplateVersionId));
    }

    private static WorkflowDetailsDTO details(WorkflowTemplateEntity template, List<WorkflowStepEntity> steps) {
        WorkflowDetailsDTO details = new WorkflowDetailsDTO();
        details.setTemplate(template);
        details.setSteps(steps);
        return details;
    }

    /**
     * 校验客户、项目与职位的归属关系并返回规范化层级。
     */
    private ValidatedDemandHierarchy validateHierarchy(
            String clientId, String projectId, String positionId) {
        ClientEntity client = requireActiveClient(clientId);
        authorization.requireAccess("demand.manage", target(client));
        ProjectEntity project = requireProjectForClient(projectId, client.getId(), true);
        PositionEntity position = requirePosition(positionId);
        if (!DemandActivationStatus.ACTIVE.matches(position.getStatus())) {
            throw PlatformExceptions.unprocessable("POSITION_NOT_AVAILABLE", "停用岗位不能用于新需求");
        }
        if (!client.getId().equals(position.getClientId())) {
            throw PlatformExceptions.unprocessable("DEMAND_HIERARCHY_MISMATCH", "岗位不属于所选客户");
        }
        if (!Objects.equals(project == null ? null : project.getId(), position.getProjectId())) {
            throw PlatformExceptions.unprocessable("DEMAND_HIERARCHY_MISMATCH", "项目、岗位与客户组合不一致");
        }
        return new ValidatedDemandHierarchy(
                client.getId(), project == null ? null : project.getId(), position.getId());
    }

    private ProjectEntity requireProjectForClient(String projectId, String clientId, boolean active) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        ProjectEntity project = requireProject(projectId);
        if (!clientId.equals(project.getClientId())) {
            throw PlatformExceptions.unprocessable("PROJECT_CLIENT_MISMATCH", "项目不属于所选客户");
        }
        if (active && !DemandActivationStatus.ACTIVE.matches(project.getStatus())) {
            throw PlatformExceptions.unprocessable("PROJECT_NOT_AVAILABLE", "停用项目不能用于新业务");
        }
        return project;
    }

    /**
     * 校验需求负责人及归属部门是否在当前操作人的可分配范围内。
     */
    private void requireAssignableOwner(
            CurrentActorSnapshot actor, String ownerUserId, String departmentId) {
        if (actor.hasCompanyWideScope()) {
            return;
        }
        if (!ownerUserId.equals(actor.getUserId()) && !actor.getManagedUserIds().contains(ownerUserId)) {
            throw new PlatformException(
                    HttpStatus.FORBIDDEN, "OWNER_ASSIGNMENT_FORBIDDEN", "只能把需求分配给本人或管理范围内人员", false);
        }
        if (!actor.affiliatedDepartmentIds().contains(departmentId)) {
            throw new PlatformException(
                    HttpStatus.FORBIDDEN, "OWNER_DEPARTMENT_FORBIDDEN", "需求归属部门超出可见范围", false);
        }
    }

    /**
     * 替换协作人列表。
     */
    private void replaceCollaborators(
            String demandId, Set<String> collaborators, String actorId, Instant now) {
        demandCollaboratorPersistenceService.deleteCollaborators(demandId);
        if (!demandCollaboratorPersistenceService.insertCollaborators(
                demandId, List.copyOf(collaborators), actorId, now)) {
            throw new IllegalStateException("招聘需求协作人批量写入失败");
        }
    }

    private static Set<String> collaboratorIds(List<String> values, String ownerUserId) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) {
            if (values.size() > MAX_COLLABORATORS_PER_DEMAND) {
                throw PlatformExceptions.unprocessable(
                        "COLLABORATOR_LIMIT_EXCEEDED",
                        "协作人最多 " + MAX_COLLABORATORS_PER_DEMAND + " 人");
            }
            values.stream()
                    .map(value -> RequiredIdentifier.require(value, "collaboratorUserId"))
                    .forEach(result::add);
        }
        result.remove(ownerUserId);
        return Collections.unmodifiableSet(result);
    }

    private ClientEntity requireClient(String id) {
        ClientEntity value = findClientById(id);
        if (value == null) {
            throw PlatformExceptions.notFound("CLIENT_NOT_FOUND", "客户不存在");
        }
        return value;
    }

    private ClientEntity findClientById(String id) {
        return persistence.findClient(id);
    }

    private ClientEntity requireActiveClient(String id) {
        ClientEntity client = requireClient(id);
        if (!DemandActivationStatus.ACTIVE.matches(client.getStatus())) {
            throw PlatformExceptions.unprocessable("CLIENT_NOT_AVAILABLE", "停用客户不能用于新业务");
        }
        return client;
    }

    private ProjectEntity requireProject(String id) {
        ProjectEntity value = projectPersistenceService.findProject(id);
        if (value == null) {
            throw PlatformExceptions.notFound("PROJECT_NOT_FOUND", "客户项目不存在");
        }
        return value;
    }

    private PositionEntity requirePosition(String id) {
        PositionEntity value = positionPersistenceService.findPosition(id);
        if (value == null) {
            throw PlatformExceptions.notFound("POSITION_NOT_FOUND", "客户岗位不存在");
        }
        return value;
    }

    /**
     * 校验并获取招聘需求。
     */
    private DemandEntity requireDemand(String id) {
        DemandEntity value = demandEntityPersistenceService.findDemand(id);
        if (value == null) {
            throw PlatformExceptions.notFound("DEMAND_NOT_FOUND", "招聘需求不存在");
        }
        return value;
    }

    private JdEntity requireJd(String id) {
        JdEntity value = persistence.findJd(id);
        if (value == null) {
            throw PlatformExceptions.notFound("JD_NOT_FOUND", "JD 不存在");
        }
        return value;
    }

    private WorkflowTemplateEntity requireWorkflow(String id) {
        WorkflowTemplateEntity value = workflowTemplatePersistenceService.findWorkflowTemplate(id);
        if (value == null) {
            throw PlatformExceptions.notFound("WORKFLOW_TEMPLATE_NOT_FOUND", "流程模板不存在");
        }
        return value;
    }

    private AuthorizationTarget target(ClientEntity client) {
        return new AuthorizationTarget(
                "CLIENT", client.getId(), client.getOwnerUserId(), client.getOwnerDepartmentId(), Set.of());
    }

    private AuthorizationTarget target(DemandEntity demand) {
        return new AuthorizationTarget(
                "DEMAND",
                demand.getId(),
                demand.getOwnerUserId(),
                demand.getOwnerDepartmentId(),
                Set.copyOf(demandCollaboratorPersistenceService.findCollaboratorIds(demand.getId())));
    }

    private AuthorizationTarget target(JdEntity jd) {
        return new AuthorizationTarget(
                "JD",
                jd.getId(),
                jd.getOwnerUserId(),
                jd.getOwnerDepartmentId(), jd.getDemandId() == null ? Set.of()
                        : Set.copyOf(demandCollaboratorPersistenceService.findCollaboratorIds(jd.getDemandId())));
    }

    private <T> T replay(String key, String action, String fingerprint, Function<String, T> loader) {
        CurrentActorSnapshot actor = authorization.currentActor();
        IdempotencyRecordEntity existing = demandIdempotencyPersistenceService.findIdempotency(actor.getUserId(), key);
        if (existing == null) {
            return null;
        }
        if (!action.equals(existing.getActionCode())
                || !fingerprint.equals(existing.getRequestFingerprint())) {
            throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "同一 Idempotency-Key 不能用于不同请求");
        }
        T result = loader.apply(existing.getTargetId());
        if (result == null) {
            throw PlatformExceptions.conflict("IDEMPOTENCY_RESULT_MISSING", "幂等请求的原结果已不可用");
        }
        return result;
    }

    private void remember(
            String actorId, String key, String action, String fingerprint, String targetId, Instant now) {
        try {
            demandIdempotencyPersistenceService.insertIdempotency(actorId, key, action, fingerprint, targetId, now);
        } catch (DuplicateKeyException duplicate) {
            throw PlatformExceptions.conflict("IDEMPOTENCY_CONFLICT", "请求正在处理或幂等键已使用，请重试以读取原结果");
        }
    }

    private void audit(
            String event,
            String actorId,
            String targetType,
            String targetId,
            String reason,
            Instant now) {
        demandAuditEventPersistenceService.insertAudit(ids.newId(), event, actorId, targetType, targetId, reason, now);
    }

    private static String fingerprint(String... values) {
        return Sha256Utils.fingerprintStrings("<null>", 0, values);
    }

    private static String trimNullable(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String result = value.trim();
        if (result.length() > maxLength) {
            throw PlatformExceptions.unprocessable("FIELD_TOO_LONG", "字段超过最大长度 " + maxLength);
        }
        return result;
    }

    private static String recruitmentType(String value) {
        String normalized = CommonUtils.upperOrEmpty(value);
        if (!RecruitmentType.supports(normalized)) {
            throw PlatformExceptions.unprocessable("RECRUITMENT_TYPE_INVALID", "招聘类型只允许 INTERNAL 或 EXTERNAL");
        }
        return normalized;
    }

    private static int headcount(int value) {
        if (value < 1 || value > 10000) {
            throw PlatformExceptions.unprocessable("HEADCOUNT_INVALID", "招聘人数必须在 1～10000 之间");
        }
        return value;
    }

    private static Instant time(StableCursor cursor) {
        return cursor == null ? null : cursor.getSortTime();
    }

    private static String id(StableCursor cursor) {
        return cursor == null ? null : cursor.getId();
    }

    private static String actorScope(
            String operation, CurrentActorSnapshot actor, Object... filters) {
        Object[] values = new Object[filters.length + 5];
        values[0] = actor.getUserId();
        values[1] = actor.hasCompanyWideScope();
        values[2] = actor.getOperationalManagedUserIds();
        values[3] = actor.visibleDepartmentIds();
        values[4] = actor.getPermissions();
        System.arraycopy(filters, 0, values, 5, filters.length);
        return CursorScopes.scope(operation, values);
    }

    /**
     * 使用需求目录的默认值和上限解析分页大小。
     */
    private static int limit(Integer requested) {
        return PageLimits.resolve(requested, DEFAULT_PAGE_LIMIT, MAX_PAGE_LIMIT);
    }

}
