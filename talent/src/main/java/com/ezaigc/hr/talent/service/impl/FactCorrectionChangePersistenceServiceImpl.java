package com.ezaigc.hr.talent.service.impl;

import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_FACT_CORRECTION_CHANGES;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_FACT_WRITE_BATCH_SIZE;
import static com.ezaigc.hr.talent.service.support.FactBatchValidation.validateBatch;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.FactCorrectionChange;
import com.ezaigc.hr.talent.mapper.FactCorrectionChangeMapper;
import com.ezaigc.hr.talent.service.FactCorrectionChangePersistenceService;
import java.util.List;
import org.springframework.stereotype.Service;

/** 字段级纠错明细的 MyBatis-Plus 批量持久化实现。 */
@Service
public class FactCorrectionChangePersistenceServiceImpl
    extends ServiceImpl<FactCorrectionChangeMapper, FactCorrectionChange>
    implements FactCorrectionChangePersistenceService {

  @Override
  public boolean insertCorrectionChanges(List<FactCorrectionChange> changes) {
    validateBatch(changes, MAX_FACT_CORRECTION_CHANGES, "fact correction changes");
    return changes.isEmpty() || saveBatch(changes, RESUME_FACT_WRITE_BATCH_SIZE);
  }
}
