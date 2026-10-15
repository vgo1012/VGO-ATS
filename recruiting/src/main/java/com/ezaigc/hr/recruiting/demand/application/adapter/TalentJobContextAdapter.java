package com.ezaigc.hr.recruiting.demand.application.adapter;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandCollaboratorEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdVersionPersistenceService;
import com.ezaigc.hr.talent.api.JobContextQuery;
import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class TalentJobContextAdapter implements JobContextQuery {
  @Resource private AuthorizationService authorization;
  @Resource private DemandQueryService demands;
  @Resource private JdVersionPersistenceService versions;
  @Resource private JdEntityPersistenceService jds;
  @Resource private DemandEntityPersistenceService demandRows;
  @Resource private DemandCollaboratorPersistenceService collaborators;

  @Override
  @Transactional(readOnly = true)
  public Context requireVisibleVersion(String id) {
    var snapshot = demands.requireJdVersion(id);
    authorization.requireAccess("demand.read", target(snapshot));
    if (snapshot.isDeleted()) throw PlatformExceptions.conflict("JD_DELETED", "JD 已删除，不能用于新业务");
    var version = versions.findJdVersion(id);
    return new Context(snapshot.getJdId(), id, version.getTitle(),
        jds.getById(snapshot.getJdId()).getCode(), version.getVersionNo());
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void requireVersionForNewUse(String id) {
    var snapshot = demands.requireJdVersionForNewUse(id);
    authorization.requireAccess("demand.read", target(snapshot));
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Context> visibleVersions(Collection<String> ids) {
    return visibleVersions(ids, Set.of());
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Context> visibleVersions(Collection<String> ids, Collection<String> sharedJdVersionIds) {
    if (ids.size() > MAX_VERSIONS_PER_BATCH) throw PlatformExceptions.unprocessable("TOO_MANY_JDS", "岗位上下文最多1000项");
    Map<String, Context> result = new LinkedHashMap<>();
    var unique = ids.stream().filter(Objects::nonNull).distinct().toList();
    if (unique.isEmpty()) return result;
    var versionRows = versions.listByIds(unique);
    if (versionRows.isEmpty()) return result;
    var jdRows =
        jds.listByIds(versionRows.stream().map(JdVersionEntity::getJdId).distinct().toList());
    var codes = jdRows.stream().filter(jd -> jd.getCode() != null)
        .collect(Collectors.toMap(JdEntity::getId, JdEntity::getCode));
    var demandIds =
        jdRows.stream().map(JdEntity::getDemandId).filter(Objects::nonNull).distinct().toList();
    var byDemand =
        (demandIds.isEmpty() ? List.<DemandEntity>of() : demandRows.listByIds(demandIds))
            .stream().collect(Collectors.toMap(DemandEntity::getId, row -> row));
    var byCollaborators =
        collaborators.findForDemands(demandIds).stream()
            .collect(
                Collectors.groupingBy(
                    DemandCollaboratorEntity::getDemandId,
                    Collectors.mapping(DemandCollaboratorEntity::getUserId, Collectors.toSet())));
    var targets = new LinkedHashMap<String, AuthorizationTarget>();
    for (var jd : jdRows) {
      var demand = byDemand.get(jd.getDemandId());
      if (jd.isDeleted() || jd.getDemandId() != null && demand == null) continue;
      targets.put(
          jd.getId(),
          new AuthorizationTarget(
              demand == null ? "JD" : "DEMAND",
              demand == null ? jd.getId() : demand.getId(),
              jd.getOwnerUserId() != null ? jd.getOwnerUserId() : demand == null ? jd.getCreateId() : demand.getOwnerUserId(),
              jd.getOwnerDepartmentId() != null ? jd.getOwnerDepartmentId() : demand == null ? jd.getSourceOwnerDepartmentId() : demand.getOwnerDepartmentId(),
              demand == null ? Set.of() : byCollaborators.getOrDefault(demand.getId(), Set.of())));
    }
    var accessible =
        authorization.accessibleObjectIds(
            authorization.currentActor(), "demand.read", targets.values());
    for (var version : versionRows) {
      var target = targets.get(version.getJdId());
      if (sharedJdVersionIds.contains(version.getId()) || target != null && accessible.contains(target.getObjectId()))
        result.put(
            version.getId(),
            new Context(
                version.getJdId(), version.getId(), version.getTitle(), codes.get(version.getJdId()), version.getVersionNo()));
    }
    return result;
  }

  private AuthorizationTarget target(JdVersionSnapshot jd) {
    return new AuthorizationTarget(
        jd.getDemandId() == null ? "JD" : "DEMAND",
        jd.getDemandId() == null ? jd.getJdId() : jd.getDemandId(),
        jd.getOwnerUserId(),
        jd.getOwnerDepartmentId(), jd.getCollaboratorUserIds());
  }
}
