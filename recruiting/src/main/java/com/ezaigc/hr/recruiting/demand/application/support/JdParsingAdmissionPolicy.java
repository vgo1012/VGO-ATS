package com.ezaigc.hr.recruiting.demand.application.support;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 创建和重试解析共享当前 JD 生命周期与操作人授权检查。 */
@Service
public class JdParsingAdmissionPolicy {
  @Resource private DemandQueryService queries;
  @Resource private AuthorizationService authorization;

  @Transactional(propagation = Propagation.MANDATORY)
  public JdVersionSnapshot requireUserAccess(String versionId) {
    JdVersionSnapshot context = queries.requireJdVersionForNewUse(versionId);
    authorization.requireAccess("demand.manage", new AuthorizationTarget(
        context.getDemandId() == null ? "JD" : "DEMAND",
        context.getDemandId() == null ? context.getJdId() : context.getDemandId(),
        context.getOwnerUserId(), context.getOwnerDepartmentId(), context.getCollaboratorUserIds()));
    if (context.isDeleted()) {
      throw new PlatformException(HttpStatus.CONFLICT, "JD_DELETED", "JD 已删除，不能重新解析", false);
    }
    return context;
  }
}
