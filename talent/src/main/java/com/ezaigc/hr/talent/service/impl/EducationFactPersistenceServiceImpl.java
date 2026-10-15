package com.ezaigc.hr.talent.service.impl;

import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_RESUME_EDUCATIONS;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_FACT_WRITE_BATCH_SIZE;
import static com.ezaigc.hr.talent.service.support.FactBatchValidation.validateBatch;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.EducationFact;
import com.ezaigc.hr.talent.mapper.EducationFactMapper;
import com.ezaigc.hr.talent.service.EducationFactPersistenceService;
import java.util.List;
import com.ezaigc.hr.talent.service.support.FactOrderMetadata;
import org.springframework.stereotype.Service;

/** EducationFact 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class EducationFactPersistenceServiceImpl
    extends ServiceImpl<EducationFactMapper, EducationFact>
    implements EducationFactPersistenceService {

  @Override
  public boolean insertEducationFacts(List<EducationFact> facts) {
    validateBatch(facts, MAX_RESUME_EDUCATIONS, "education facts");
    return facts.isEmpty() || saveBatch(facts, RESUME_FACT_WRITE_BATCH_SIZE);
  }

  /** 查询教育经历。 */
  @Override
  public List<EducationFact> findEducation(String id) {
    return FactOrderMetadata.restore(baseMapper.selectList(
        Wrappers.lambdaQuery(EducationFact.class)
            .eq(EducationFact::getFactSnapshotId, id)
            .orderByDesc(EducationFact::getStartDate)
            .orderByAsc(EducationFact::getItemKey)));
  }
}
