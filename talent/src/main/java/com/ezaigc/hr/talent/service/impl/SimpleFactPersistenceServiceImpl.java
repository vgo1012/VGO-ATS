package com.ezaigc.hr.talent.service.impl;

import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_RESUME_SIMPLE_FACTS;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_FACT_WRITE_BATCH_SIZE;
import static com.ezaigc.hr.talent.service.support.FactBatchValidation.validateBatch;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.SimpleFact;
import com.ezaigc.hr.talent.mapper.SimpleFactMapper;
import com.ezaigc.hr.talent.service.SimpleFactPersistenceService;
import java.util.List;
import com.ezaigc.hr.talent.service.support.FactOrderMetadata;
import org.springframework.stereotype.Service;

/** SimpleFact 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class SimpleFactPersistenceServiceImpl extends ServiceImpl<SimpleFactMapper, SimpleFact>
    implements SimpleFactPersistenceService {

  @Override
  public boolean insertSimpleFacts(List<SimpleFact> facts) {
    validateBatch(facts, MAX_RESUME_SIMPLE_FACTS, "simple facts");
    return facts.isEmpty() || saveBatch(facts, RESUME_FACT_WRITE_BATCH_SIZE);
  }

  @Override
  public List<SimpleFact> findSimple(String id) {
    return FactOrderMetadata.restore(baseMapper.selectList(
        Wrappers.lambdaQuery(SimpleFact.class)
            .eq(SimpleFact::getFactSnapshotId, id)
            .orderByAsc(SimpleFact::getFactType, SimpleFact::getItemKey)));
  }
}
