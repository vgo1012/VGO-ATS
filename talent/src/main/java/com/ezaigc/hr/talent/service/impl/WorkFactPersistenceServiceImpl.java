package com.ezaigc.hr.talent.service.impl;

import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_RESUME_WORK_EXPERIENCES;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_FACT_WRITE_BATCH_SIZE;
import static com.ezaigc.hr.talent.service.support.FactBatchValidation.validateBatch;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.WorkFact;
import com.ezaigc.hr.talent.mapper.WorkFactMapper;
import com.ezaigc.hr.talent.service.WorkFactPersistenceService;
import java.util.List;
import com.ezaigc.hr.talent.service.support.FactOrderMetadata;
import org.springframework.stereotype.Service;

/** WorkFact 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class WorkFactPersistenceServiceImpl extends ServiceImpl<WorkFactMapper, WorkFact>
    implements WorkFactPersistenceService {

  @Override
  public boolean insertWorkFacts(List<WorkFact> facts) {
    validateBatch(facts, MAX_RESUME_WORK_EXPERIENCES, "work facts");
    return facts.isEmpty() || saveBatch(facts, RESUME_FACT_WRITE_BATCH_SIZE);
  }

  /** 查询工作。 */
  @Override
  public List<WorkFact> findWork(String id) {
    return FactOrderMetadata.restore(baseMapper.selectList(
        Wrappers.lambdaQuery(WorkFact.class)
            .eq(WorkFact::getFactSnapshotId, id)
            .orderByDesc(WorkFact::getStartDate)
            .orderByAsc(WorkFact::getItemKey)));
  }
}
