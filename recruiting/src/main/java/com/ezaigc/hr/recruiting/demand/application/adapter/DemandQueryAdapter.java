package com.ezaigc.hr.recruiting.demand.application.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.recruiting.demand.api.ActiveWorkflowSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdClientSummary;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.recruiting.demand.domain.entity.ClientEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandCollaboratorEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandWorkflowStepPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdVersionPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.WorkflowTemplatePersistenceService;
import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemandQueryAdapter implements DemandQueryService {

  @Resource private DemandCatalogPersistenceService persistence;
  @Resource private DemandCollaboratorPersistenceService demandCollaboratorPersistenceService;
  @Resource private DemandEntityPersistenceService demandEntityPersistenceService;
  @Resource private DemandWorkflowStepPersistenceService demandWorkflowStepPersistenceService;
  @Resource private JdVersionPersistenceService jdVersionPersistenceService;
  @Resource private JdEntityPersistenceService jdEntityPersistenceService;
  @Resource private WorkflowTemplatePersistenceService workflowTemplatePersistenceService;

  @Override
  @Transactional(readOnly = true)
  public JdVersionSnapshot requireJdVersion(String jdVersionId) {
    JdVersionEntity version = jdVersionPersistenceService.findJdVersion(jdVersionId);
    if (version == null) {
      throw new PlatformException(HttpStatus.NOT_FOUND, "JD_VERSION_NOT_FOUND", "JD 版本不存在", false);
    }
    JdEntity jd = persistence.findJd(version.getJdId());
    return snapshot(version, jd);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, JdVersionSnapshot> requireJdVersions(Collection<String> jdVersionIds) {
    List<String> ids = requireBatchIds(jdVersionIds);
    if (ids.isEmpty()) {
      return Map.of();
    }

    Map<String, JdVersionEntity> versions =
        jdVersionPersistenceService
            .list(
                Wrappers.<JdVersionEntity>lambdaQuery()
                    .select(
                        JdVersionEntity::getId,
                        JdVersionEntity::getJdId,
                        JdVersionEntity::getRecruitmentType,
                        JdVersionEntity::getClientId)
                    .in(JdVersionEntity::getId, ids))
            .stream()
            .collect(Collectors.toMap(JdVersionEntity::getId, Function.identity()));
    for (String id : ids) {
      if (!versions.containsKey(id)) {
        throw new PlatformException(
            HttpStatus.NOT_FOUND, "JD_VERSION_NOT_FOUND", "JD 版本不存在", false);
      }
    }

    List<String> jdIds =
        ids.stream().map(id -> versions.get(id).getJdId()).distinct().toList();
    Map<String, JdEntity> jds =
        jdEntityPersistenceService
            .list(
                Wrappers.<JdEntity>lambdaQuery()
                    .select(
                        JdEntity::getId,
                        JdEntity::getDemandId,
                        JdEntity::getRecruitmentType,
                        JdEntity::getClientId,
                        JdEntity::getOwnerUserId,
                        JdEntity::getOwnerDepartmentId,
                        JdEntity::getCreateId,
                        JdEntity::getSourceOwnerDepartmentId,
                        JdEntity::isPublicVisible,
                        JdEntity::isDeleted)
                    .in(JdEntity::getId, jdIds))
            .stream()
            .collect(Collectors.toMap(JdEntity::getId, Function.identity()));
    for (String jdId : jdIds) {
      if (!jds.containsKey(jdId)) {
        throw new PlatformException(HttpStatus.NOT_FOUND, "JD_NOT_FOUND", "JD 不存在", false);
      }
    }

    List<String> demandIds =
        jdIds.stream()
            .map(jds::get)
            .map(JdEntity::getDemandId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<String, DemandEntity> demands = loadDemands(demandIds);
    // 与 findJd 的 CASE/COALESCE 投影一致，需求创建的 JD 可未物化归属和客户字段。
    for (JdEntity jd : jds.values()) {
      DemandEntity demand = jd.getDemandId() == null ? null : demands.get(jd.getDemandId());
      if (jd.getOwnerUserId() == null) {
        jd.setOwnerUserId(demand != null && demand.getOwnerUserId() != null
            ? demand.getOwnerUserId() : jd.getCreateId());
      }
      if (jd.getOwnerDepartmentId() == null) {
        jd.setOwnerDepartmentId(demand != null && demand.getOwnerDepartmentId() != null
            ? demand.getOwnerDepartmentId() : jd.getSourceOwnerDepartmentId());
      }
      if (jd.getRecruitmentType() == null) {
        jd.setClientId(demand == null ? null : demand.getClientId());
      }
    }
    List<String> existingDemandIds = demandIds.stream().filter(demands::containsKey).toList();
    Map<String, Set<String>> collaborators = loadCollaborators(existingDemandIds);

    List<String> clientIds =
        ids.stream()
            .map(versions::get)
            .map(version -> clientId(version, jds.get(version.getJdId())))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<String, ClientEntity> clients = loadClients(clientIds);

    Map<String, JdVersionSnapshot> snapshots = new LinkedHashMap<>();
    for (String id : ids) {
      JdVersionEntity version = versions.get(id);
      JdEntity jd = jds.get(version.getJdId());
      DemandEntity demand = jd.getDemandId() == null ? null : demands.get(jd.getDemandId());
      Set<String> collaboratorIds =
          demand == null ? Set.of() : collaborators.getOrDefault(demand.getId(), Set.of());
      String clientId = clientId(version, jd);
      snapshots.put(
          id,
          snapshot(
              version,
              jd,
              demand,
              collaboratorIds,
              clientId == null ? null : clients.get(clientId)));
    }
    return Collections.unmodifiableMap(snapshots);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, String> clientIdsForVersions(Collection<String> jdVersionIds) {
    Map<String, String> clients = new HashMap<>();
    loadVersionSummaries(jdVersionIds).forEach((id, summary) -> clients.put(id, summary.getClientId()));
    return Collections.unmodifiableMap(clients);
  }

  private Map<String, JdClientSummary> loadVersionSummaries(Collection<String> jdVersionIds) {
    if (jdVersionIds == null || jdVersionIds.size() > 200) throw new IllegalArgumentException("JD client batch exceeds 200");
    var ids = jdVersionIds.stream().distinct().toList();
    if (ids.isEmpty()) return Map.of();
    var versions = jdVersionPersistenceService.list(Wrappers.<JdVersionEntity>lambdaQuery()
        .select(JdVersionEntity::getId, JdVersionEntity::getJdId, JdVersionEntity::getRecruitmentType,
            JdVersionEntity::getClientId, JdVersionEntity::getTitle)
        .in(JdVersionEntity::getId, ids));
    if (versions.size() != ids.size()) throw new PlatformException(HttpStatus.NOT_FOUND, "JD_VERSION_NOT_FOUND", "JD 版本不存在", false);
    var jds = jdEntityPersistenceService.list(Wrappers.<JdEntity>lambdaQuery()
        .select(JdEntity::getId, JdEntity::getClientId, JdEntity::getCode)
        .in(JdEntity::getId, versions.stream().map(JdVersionEntity::getJdId).distinct().toList()))
        .stream().collect(Collectors.toMap(JdEntity::getId, Function.identity()));
    Map<String, JdClientSummary> summaries = new HashMap<>();
    for (var version : versions) {
      var jd = jds.get(version.getJdId());
      if (jd == null) throw new PlatformException(HttpStatus.NOT_FOUND, "JD_NOT_FOUND", "JD 不存在", false);
      String clientId = version.getRecruitmentType() != null ? version.getClientId() : jd.getClientId();
      summaries.put(version.getId(), new JdClientSummary(clientId, null, version.getTitle(), jd.getCode()));
    }
    return summaries;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, JdClientSummary> clientSummariesForVersions(Collection<String> jdVersionIds) {
    var summaries = loadVersionSummaries(jdVersionIds);
    if (summaries.isEmpty()) return Map.of();
    Set<String> clientIds = summaries.values().stream().map(JdClientSummary::getClientId)
        .filter(Objects::nonNull).collect(Collectors.toSet());
    Map<String, ClientEntity> clients = clientIds.isEmpty() ? Map.of()
        : persistence.list(Wrappers.<ClientEntity>lambdaQuery()
            .select(ClientEntity::getId, ClientEntity::getName).in(ClientEntity::getId, clientIds))
            .stream().collect(Collectors.toMap(ClientEntity::getId, Function.identity()));
    summaries.replaceAll((versionId, summary) -> {
      ClientEntity client = summary.getClientId() == null ? null : clients.get(summary.getClientId());
      return new JdClientSummary(summary.getClientId(), client == null ? null : client.getName(),
          summary.getJdTitle(), summary.getJdCode());
    });
    return Collections.unmodifiableMap(summaries);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public JdVersionSnapshot requireJdVersionForNewUse(String jdVersionId) {
    var version = jdVersionPersistenceService.findJdVersion(jdVersionId);
    if (version == null) throw new PlatformException(HttpStatus.NOT_FOUND, "JD_VERSION_NOT_FOUND", "JD 版本不存在", false);
    // 不可在锁后复用 findJd 的一致性快照；权限元数据和删除标记全部来自当前锁定结果。
    var current = persistence.findJdForUpdate(version.getJdId());
    if (current == null) throw new PlatformException(HttpStatus.NOT_FOUND, "JD_NOT_FOUND", "JD 不存在", false);
    if (current.isDeleted()) throw new PlatformException(HttpStatus.CONFLICT, "JD_DELETED", "JD 已删除，不能用于新业务", false);
    return snapshot(version, current);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public JdVersionSnapshot lockJdForHeadcount(String jdVersionId) {
    var version = jdVersionPersistenceService.findJdVersion(jdVersionId);
    if (version == null) throw new PlatformException(HttpStatus.NOT_FOUND, "JD_VERSION_NOT_FOUND", "JD 版本不存在", false);
    var current = persistence.findJdForUpdate(version.getJdId());
    if (current == null) throw new PlatformException(HttpStatus.NOT_FOUND, "JD_NOT_FOUND", "JD 不存在", false);
    return snapshot(version, current);
  }

  private JdVersionSnapshot snapshot(JdVersionEntity version, JdEntity jd) {
    if (jd == null) throw new PlatformException(HttpStatus.NOT_FOUND, "JD_NOT_FOUND", "JD 不存在", false);
    DemandEntity demand = jd.getDemandId() == null ? null : demandEntityPersistenceService.findDemand(jd.getDemandId());
    Set<String> collaboratorIds =
        demand == null
            ? Set.of()
            : Set.copyOf(demandCollaboratorPersistenceService.findCollaboratorIds(demand.getId()));
    String clientId = clientId(version, jd);
    ClientEntity client = clientId == null ? null : persistence.findClient(clientId);
    return snapshot(version, jd, demand, collaboratorIds, client);
  }

  private JdVersionSnapshot snapshot(
      JdVersionEntity version,
      JdEntity jd,
      DemandEntity demand,
      Set<String> collaboratorIds,
      ClientEntity client) {
    var snapshot = new JdVersionSnapshot(version.getId(), jd.getId(), jd.getDemandId(),
        version.getRecruitmentType() != null ? version.getRecruitmentType()
            : jd.getRecruitmentType() != null ? jd.getRecruitmentType() : demand == null ? "INTERNAL" : demand.getRecruitmentType(),
        jd.getOwnerUserId(), jd.getOwnerDepartmentId(),
        Set.copyOf(collaboratorIds));
    // 新版本的空客户表示明确内部招聘，不能用逻辑 JD 当前客户覆盖历史。
    snapshot.setClientId(clientId(version, jd));
    snapshot.setClientName(client == null ? null : client.getName());
    snapshot.setPublicVisible(false);
    snapshot.setDeleted(jd.isDeleted());
    snapshot.setHeadcount(jd.getHeadcount());
    return snapshot;
  }

  private List<String> requireBatchIds(Collection<String> jdVersionIds) {
    if (jdVersionIds == null || jdVersionIds.size() > 200) {
      throw new IllegalArgumentException("JD version snapshot batch is invalid");
    }
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    for (String id : jdVersionIds) {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("JD version snapshot batch is invalid");
      }
      ids.add(id);
    }
    return List.copyOf(ids);
  }

  private Map<String, DemandEntity> loadDemands(List<String> demandIds) {
    if (demandIds.isEmpty()) {
      return Map.of();
    }
    return demandEntityPersistenceService
        .list(
            Wrappers.<DemandEntity>lambdaQuery()
                .select(DemandEntity::getId, DemandEntity::getRecruitmentType,
                    DemandEntity::getClientId, DemandEntity::getOwnerUserId,
                    DemandEntity::getOwnerDepartmentId)
                .in(DemandEntity::getId, demandIds))
        .stream()
        .collect(Collectors.toMap(DemandEntity::getId, Function.identity()));
  }

  private Map<String, Set<String>> loadCollaborators(List<String> demandIds) {
    if (demandIds.isEmpty()) {
      return Map.of();
    }
    Map<String, Set<String>> collaborators = new HashMap<>();
    for (DemandCollaboratorEntity collaborator :
        demandCollaboratorPersistenceService.findForDemands(demandIds)) {
      collaborators
          .computeIfAbsent(collaborator.getDemandId(), ignored -> new LinkedHashSet<>())
          .add(collaborator.getUserId());
    }
    return collaborators;
  }

  private Map<String, ClientEntity> loadClients(List<String> clientIds) {
    if (clientIds.isEmpty()) {
      return Map.of();
    }
    return persistence
        .list(
            Wrappers.<ClientEntity>lambdaQuery()
                .select(ClientEntity::getId, ClientEntity::getName)
                .in(ClientEntity::getId, clientIds))
        .stream()
        .collect(Collectors.toMap(ClientEntity::getId, Function.identity()));
  }

  private String clientId(JdVersionEntity version, JdEntity jd) {
    return version.getRecruitmentType() != null ? version.getClientId() : jd.getClientId();
  }

  @Override
  @Transactional(readOnly = true)
  public JdVersionSnapshot requireCurrentJdVersionForDemand(String demandId) {
    JdVersionEntity version = persistence.findCurrentJdVersionForDemand(demandId);
    if (version == null) {
      throw new PlatformException(
          HttpStatus.NOT_FOUND, "JD_VERSION_NOT_FOUND", "招聘需求尚无 JD 版本", false);
    }
    return requireJdVersion(version.getId());
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasActiveWorkflow(String jdVersionId) {
    requireJdVersion(jdVersionId);
    return workflowTemplatePersistenceService.countActiveWorkflow(jdVersionId) > 0;
  }

  @Override
  @Transactional(readOnly = true)
  public ActiveWorkflowSnapshot requireActiveWorkflow(String jdVersionId) {
    var jd = requireJdVersion(jdVersionId);
    var template = workflowTemplatePersistenceService.findActiveWorkflow(jdVersionId);
    if (template == null) {
      throw new PlatformException(
          HttpStatus.UNPROCESSABLE_CONTENT, "ACTIVE_WORKFLOW_REQUIRED", "JD 尚未激活招聘流程模板", false);
    }
    var steps =
        demandWorkflowStepPersistenceService.findWorkflowSteps(template.getId()).stream()
            .map(
                step ->
                    new ActiveWorkflowSnapshot.Step(
                        step.getId(),
                        step.getStepCode(),
                        step.getSequenceNo(),
                        step.getCandidateOptional(),
                        step.getDefaultFollowUpChannel()))
            .toList();
    if (steps.isEmpty()) {
      throw new PlatformException(
          HttpStatus.UNPROCESSABLE_CONTENT, "ACTIVE_WORKFLOW_INVALID", "生效流程模板没有步骤", false);
    }
    return new ActiveWorkflowSnapshot(
        template.getId(), jd.getJdVersionId(), template.getRecruitmentType(), steps);
  }

  @Override
  @Transactional(readOnly = true)
  public SubmissionDemandContext requireSubmissionContextForJdVersion(String jdVersionId) {
    var jd = requireJdVersion(jdVersionId);
    var legacy = jd.getDemandId() == null ? null : requireSubmissionContext(jd.getDemandId());
    return new SubmissionDemandContext(jd.getDemandId(), jd.getClientId(),
        legacy == null ? null : legacy.projectId(), legacy == null ? null : legacy.positionId(),
        jd.getRecruitmentType(), jd.getOwnerUserId(), jd.getOwnerDepartmentId());
  }

  @Override
  @Transactional(readOnly = true)
  public SubmissionDemandContext requireSubmissionContext(String demandId) {
    DemandEntity demand = demandEntityPersistenceService.findDemand(demandId);
    if (demand == null) {
      throw new PlatformException(HttpStatus.NOT_FOUND, "DEMAND_NOT_FOUND", "招聘需求不存在", false);
    }
    return new SubmissionDemandContext(
        demand.getId(),
        demand.getClientId(),
        demand.getProjectId(),
        demand.getPositionId(),
        demand.getRecruitmentType(),
        demand.getOwnerUserId(),
        demand.getOwnerDepartmentId());
  }
}
