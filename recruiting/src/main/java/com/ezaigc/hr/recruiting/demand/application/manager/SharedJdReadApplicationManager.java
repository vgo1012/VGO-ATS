package com.ezaigc.hr.recruiting.demand.application.manager;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.SharedCandidateJdQuery;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** 只开放共享候选人已绑定的冻结 JD，不能借此列出其他岗位版本。 */
@Component
public class SharedJdReadApplicationManager {
  @Resource private ApplicationPersistenceService applications;
  @Resource @Lazy private CandidateLibraryQueryService library;
  @Resource @Lazy private SharedCandidateJdQuery materials;

  public boolean isShared(String jdVersionId) {
    if (materials.hasSharedCandidate(jdVersionId)) return true;
    String afterId = null;
    while (true) {
      var rows = applications.page(new Page<Application>(1, 200, false),
          Wrappers.<Application>lambdaQuery().select(Application::getId, Application::getCandidateId)
              .eq(Application::getJdVersionId, jdVersionId)
              .gt(afterId != null, Application::getId, afterId).orderByAsc(Application::getId)).getRecords();
      if (rows.isEmpty()) return false;
      if (!library.eligibleCandidateIds(rows.stream().map(Application::getCandidateId).distinct().toList()).isEmpty()) return true;
      if (rows.size() < 200) return false;
      afterId = rows.getLast().getId();
    }
  }
}
