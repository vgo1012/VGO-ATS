package com.ezaigc.hr.talent.application.manager;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.request.RequiredIdentifier;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.domain.response.ResumeVersionFactSummaryResponse;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import java.util.List;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 由真实版本归属执行授权；申请引用的历史版本不替换为候选人当前版本。 */
@Component
public class ResumeVersionFactSummaryApplicationManager {
  @Resource private AuthorizationService authorization;
  @Resource private ResumeVersionPersistenceService versions;
  @Resource private ResumeVersionReadAccess readAccess;
  @Resource private ResumeListFactApplicationManager facts;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<ResumeVersionFactSummaryResponse> summaries(List<String> resumeVersionIds) {
    authorization.currentActor();
    if (resumeVersionIds == null || resumeVersionIds.size() > 100) {
      throw PlatformExceptions.unprocessable("TOO_MANY_RESUME_VERSIONS", "简历版本摘要每次最多100项");
    }
    var ids = resumeVersionIds.stream().map(id -> RequiredIdentifier.require(id, "resumeVersionId")).distinct().toList();
    if (ids.isEmpty()) return List.of();
    var authorizedVersions = readAccess.readableVersions(ids, versions.listByIds(ids));
    var authorized = authorizedVersions.stream().map(ResumeVersion::getId).toList();
    if (authorized.isEmpty()) return List.of();
    var summaries = facts.summaries(authorized);
    return authorized.stream().map(id -> new ResumeVersionFactSummaryResponse(id, summaries.get(id))).toList();
  }
}
