package com.ezaigc.hr.recruiting.demand.application.manager;

import static com.ezaigc.hr.common.web.request.RequiredText.require;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.domain.dto.VersionReasonDTO;
import com.ezaigc.hr.recruiting.demand.domain.entity.ClientEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandAuditEventPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 删除误建且尚未被业务引用的客户，保留已有业务及审计历史。 */
@Service
public class ClientDeletionApplicationManager {
  @Resource private AuthorizationService authorization;
  @Resource private DemandCatalogPersistenceService persistence;
  @Resource private DemandAuditEventPersistenceService audit;
  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;

  @Transactional
  public void deleteClient(String id, VersionReasonDTO command) {
    CurrentActorSnapshot actor = authorization.currentActor();
    if (!actor.isSuperAdmin()) {
      throw PlatformExceptions.forbidden("ADMIN_REQUIRED", "公共资料仅由管理员维护");
    }
    authorization.requirePermission("demand.manage");
    ClientEntity client = persistence.findClient(id);
    if (client == null) {
      throw PlatformExceptions.notFound("CLIENT_NOT_FOUND", "客户不存在");
    }
    authorization.requireAccess("demand.manage", new AuthorizationTarget(
        "CLIENT", id, client.getOwnerUserId(), client.getOwnerDepartmentId(), Set.of()));
    String reason = require(command.getReason(), "reason", 500);
    try {
      // 版本条件和外键限制由同一条 DELETE 原子执行，不能先查引用再无条件删除。
      if (persistence.deleteClient(id, command.getVersion()) == 0) {
        throw PlatformExceptions.versionConflict("CLIENT_VERSION_CONFLICT");
      }
    } catch (DataIntegrityViolationException exception) {
      throw PlatformExceptions.conflict("CLIENT_IN_USE",
          "客户已被项目、岗位、招聘需求、JD 或推荐记录引用，不能删除，请改为停用");
    }
    audit.insertAudit(ids.newId(), "CLIENT_DELETED", actor.getUserId(), "CLIENT", id,
        reason, clock.instant());
  }
}
