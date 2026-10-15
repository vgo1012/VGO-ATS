package com.ezaigc.hr.recruiting.demand.application.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ezaigc.hr.common.infrastructure.json.JsonNodeValues;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdFactQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData.ControlledFacts;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData.FulfillmentFacts;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData.Language;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData.ProfessionalFacts;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData.Role;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData.Skill;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdFactSnapshotEntity;
import com.ezaigc.hr.recruiting.demand.service.JdParsingPersistenceService;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class JdFactQueryAdapter implements JdFactQueryService {

  @Resource private AuthorizationService authorization;

  @Resource private DemandQueryService demandQuery;

  @Resource private JdParsingPersistenceService persistence;
  @Resource private ObjectMapper json;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public JdFactSnapshotData currentFacts(String jdVersionId) {
    JdFactSnapshotEntity snapshot = requireCurrentSnapshot(jdVersionId);
    requireAccess(
        snapshot, demandQuery.requireJdVersionForNewUse(snapshot.getJdVersionId()));
    return map(snapshot);
  }

  @Override
  @Transactional(readOnly = true)
  public void requireCurrentFactsReadAccess(String jdVersionId) {
    JdFactSnapshotEntity snapshot = requireCurrentSnapshot(jdVersionId);
    requireAccess(snapshot, demandQuery.requireJdVersion(snapshot.getJdVersionId()));
  }

  @Override
  @Transactional(readOnly = true)
  public void requireCurrentFactsReadAccessBatch(Collection<String> jdVersionIds) {
    if (jdVersionIds == null || jdVersionIds.size() > MAX_READ_ACCESS_BATCH_SIZE
        || jdVersionIds.stream().anyMatch(id -> id == null || id.isBlank())) {
      throw new IllegalArgumentException("JD read access batch requires at most 200 nonblank IDs");
    }
    var ids = jdVersionIds.stream().distinct().toList();
    if (ids.isEmpty()) return;
    var currentIds = persistence.list(Wrappers.<JdFactSnapshotEntity>lambdaQuery()
        .select(JdFactSnapshotEntity::getJdVersionId)
        .eq(JdFactSnapshotEntity::getCurrentMarker, "Y")
        .in(JdFactSnapshotEntity::getJdVersionId, ids)).stream()
        .map(JdFactSnapshotEntity::getJdVersionId).collect(Collectors.toSet());
    if (!currentIds.containsAll(ids)) {
      throw new PlatformException(
          HttpStatus.CONFLICT, "JD_FACTS_REQUIRED", "JD 当前版本尚无有效事实快照", true);
    }
    var contexts = demandQuery.requireJdVersions(ids);
    for (String id : ids) {
      if (contexts.get(id).isDeleted()) {
        throw new PlatformException(
            HttpStatus.CONFLICT, "JD_DELETED", "JD 已删除，不能发起新匹配", false);
      }
    }
    var actor = authorization.currentActor();
    var targets = ids.stream().map(id -> {
      var context = contexts.get(id);
      return new AuthorizationTarget("JD_VERSION", id, context.getOwnerUserId(),
          context.getOwnerDepartmentId(), context.getCollaboratorUserIds());
    }).toList();
    if (!authorization.accessibleObjectIds(actor, "demand.read", targets).containsAll(ids)) {
      throw PlatformExceptions.forbidden("DATA_SCOPE_FORBIDDEN", "无权访问该业务对象");
    }
  }

  @Override
  public JdFactSnapshotData factsBySnapshotId(String snapshotId) {
    JdFactSnapshotEntity snapshot = persistence.findSnapshot(snapshotId);
    if (snapshot == null) {
      throw new PlatformException(
          HttpStatus.NOT_FOUND, "JD_FACT_SNAPSHOT_NOT_FOUND", "JD 事实快照不存在", false);
    }
    return map(snapshot);
  }

  private JdFactSnapshotEntity requireCurrentSnapshot(String jdVersionId) {
    JdFactSnapshotEntity snapshot = persistence.findCurrent(jdVersionId);
    if (snapshot == null) {
      throw new PlatformException(
          HttpStatus.CONFLICT, "JD_FACTS_REQUIRED", "JD 当前版本尚无有效事实快照", true);
    }
    return snapshot;
  }

  private void requireAccess(JdFactSnapshotEntity snapshot, JdVersionSnapshot context) {
    String jdVersionId = snapshot.getJdVersionId();
    if (context.isDeleted()) {
      throw new PlatformException(
          HttpStatus.CONFLICT, "JD_DELETED", "JD 已删除，不能发起新匹配", false);
    }
    authorization.requireAccess(
        "demand.read",
        new AuthorizationTarget(
            "JD_VERSION",
            jdVersionId,
            context.getOwnerUserId(),
            context.getOwnerDepartmentId(), context.getCollaboratorUserIds()));
  }

  private JdFactSnapshotData map(JdFactSnapshotEntity snapshot) {
    String jdVersionId = snapshot.getJdVersionId();
    try {
      JsonNode root = json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .readTree(snapshot.getFactsJson());
      JsonNode professional = root.get("professional");
      JsonNode controlled = root.get("controlledConditions");
      JsonNode fulfillment = root.get("fulfillmentConditions");
      return new JdFactSnapshotData(
          snapshot.getId(),
          jdVersionId,
          snapshot.getSnapshotVersion(),
          new ProfessionalFacts(
              objects(professional, "coreSkills", Skill.class),
              objects(professional, "roleExperiences", Role.class),
              JsonNodeValues.decimal(professional, "minimumYears"),
              JsonNodeValues.decimal(professional, "preferredYears"),
              JsonNodeValues.strings(professional, "responsibilities"),
              JsonNodeValues.strings(professional, "industryTags"),
              JsonNodeValues.strings(professional, "projectTags")),
          new ControlledFacts(
              JsonNodeValues.integer(controlled, "ageMin"),
              JsonNodeValues.integer(controlled, "ageMax"),
              JsonNodeValues.text(controlled, "sex"),
              JsonNodeValues.strings(controlled, "educations"),
              JsonNodeValues.strings(controlled, "credentials"),
              objects(controlled, "languages", Language.class)),
          new FulfillmentFacts(
              JsonNodeValues.strings(fulfillment, "locations"),
              JsonNodeValues.text(fulfillment, "availability"),
              JsonNodeValues.decimal(fulfillment, "salaryMin"),
              JsonNodeValues.decimal(fulfillment, "salaryMax"),
              JsonNodeValues.text(fulfillment, "salaryPeriod")));
    } catch (Exception ex) {
      throw new PlatformException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "JD_FACT_SNAPSHOT_INVALID",
          "已持久化的 JD 事实快照无法读取",
          false,
          ex);
    }
  }

  private <T> List<T> objects(JsonNode node, String field, Class<T> targetType) {
    return JsonNodeValues.objects(json, node, field, targetType);
  }
}
