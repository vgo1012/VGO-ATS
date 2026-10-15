package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.ResumeJdScoreBinding;
import java.util.List;

public interface ResumeJdScoreBindingPersistenceService extends IService<ResumeJdScoreBinding> {
  List<ResumeJdScoreBinding> findForCandidates(List<String> candidateIds);
  List<ResumeJdScoreBinding> findByOperation(String operationId);
}
