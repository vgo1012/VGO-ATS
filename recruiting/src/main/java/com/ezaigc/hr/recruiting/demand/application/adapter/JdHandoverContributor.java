package com.ezaigc.hr.recruiting.demand.application.adapter;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.HandoverResource;
import com.ezaigc.hr.identity.api.HandoverResourceContributor;
import com.ezaigc.hr.identity.api.HandoverTransferResult;
import com.ezaigc.hr.identity.api.IdentityDirectoryService;
import com.ezaigc.hr.recruiting.demand.service.DemandAuditEventPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandOwnershipHistoryPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdEntityPersistenceService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JdHandoverContributor implements HandoverResourceContributor {
  static final int PREVIEW_LIMIT = 100;

  @Resource private JdEntityPersistenceService jds;
  @Resource private DemandCatalogPersistenceService catalog;
  @Resource private DemandOwnershipHistoryPersistenceService ownershipHistory;
  @Resource private DemandAuditEventPersistenceService auditEvents;
  @Resource private IdentityDirectoryService directory;
  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;

  @Override
  public String resourceType() {
    return "JD";
  }

  @Override
  public List<HandoverResource> preview(String sourceUserId) {
    var ownedJds = jds.findPrivateOwnedJds(sourceUserId, PREVIEW_LIMIT + 1);
    if (ownedJds.size() > PREVIEW_LIMIT) {
      throw PlatformExceptions.conflict(
          "HANDOVER_PREVIEW_LIMIT_EXCEEDED", "私有 JD 超过单次交接上限 100，请先分批处理");
    }
    return ownedJds.stream()
        .map(jd -> new HandoverResource(resourceType(), jd.getId(), jd.getTitle(), true, true, null))
        .toList();
  }

  @Override
  @Transactional
  public HandoverTransferResult transfer(
      String resourceId,
      String sourceUserId,
      String targetUserId,
      String actorUserId,
      String reason) {
    var jd = catalog.findJdForUpdate(resourceId);
    if (jd == null || jd.isDeleted()
        || !sourceUserId.equals(jd.getOwnerUserId())) {
      return HandoverTransferResult.failure(
          "JD_NOT_TRANSFERABLE", "JD 不存在、已删除或负责人已变化");
    }
    var target = directory.activeUser(targetUserId);
    Instant now = clock.instant();
    if (jds.transferPrivateOwner(
            resourceId,
            sourceUserId,
            target.userId(),
            target.homeDepartmentId(),
            jd.getVersion(),
            actorUserId,
            now)
        != 1) {
      return HandoverTransferResult.failure("JD_STATE_CHANGED", "JD 归属状态已变化");
    }
    String historyId = ids.newId();
    if (ownershipHistory.insertOwnershipHistory(
            historyId,
            "JD",
            resourceId,
            sourceUserId,
            target.userId(),
            jd.getOwnerDepartmentId(),
            target.homeDepartmentId(),
            reason,
            actorUserId,
            now)
        != 1) {
      throw new IllegalStateException("JD 归属历史写入失败");
    }
    if (auditEvents.insertAudit(
            ids.newId(), "JD_OWNERSHIP_CHANGED", actorUserId, "JD", resourceId, reason, now)
        != 1) {
      throw new IllegalStateException("JD 交接审计写入失败");
    }
    return HandoverTransferResult.success(historyId);
  }
}
