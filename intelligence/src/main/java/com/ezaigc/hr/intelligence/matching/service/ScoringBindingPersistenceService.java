package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.ScoringBinding;
import java.time.Instant;
import com.ezaigc.hr.intelligence.matching.domain.dto.ScoringBindingDTO;

public interface ScoringBindingPersistenceService extends IService<ScoringBinding> {
  ScoringBindingDTO binding(String jdVersionId);
  void bindLegacy(String jdVersionId, String planVersionId, String actorId, Instant now);
  int insertBinding(String jdVersionId, String planVersionId, String actorId, Instant now);
  int updateBinding(String jdVersionId, String planVersionId, long version, String actorId, Instant now);
}
