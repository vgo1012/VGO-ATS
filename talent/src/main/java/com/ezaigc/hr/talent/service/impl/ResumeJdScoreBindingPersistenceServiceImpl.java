package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.ResumeJdScoreBinding;
import com.ezaigc.hr.talent.mapper.ResumeJdScoreBindingMapper;
import com.ezaigc.hr.talent.service.ResumeJdScoreBindingPersistenceService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ResumeJdScoreBindingPersistenceServiceImpl
    extends ServiceImpl<ResumeJdScoreBindingMapper, ResumeJdScoreBinding>
    implements ResumeJdScoreBindingPersistenceService {
  @Override
  public List<ResumeJdScoreBinding> findForCandidates(List<String> candidateIds) {
    return candidateIds.isEmpty() ? List.of() : baseMapper.findForCandidates(candidateIds);
  }

  @Override
  public List<ResumeJdScoreBinding> findByOperation(String operationId) {
    return baseMapper.findByOperation(operationId);
  }
}
