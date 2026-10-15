package com.ezaigc.hr.talent.service.impl;

import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_RESUME_SKILLS;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_FACT_WRITE_BATCH_SIZE;
import static com.ezaigc.hr.talent.service.support.FactBatchValidation.validateBatch;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.SkillFact;
import com.ezaigc.hr.talent.mapper.SkillFactMapper;
import com.ezaigc.hr.talent.service.SkillFactPersistenceService;
import java.util.List;
import com.ezaigc.hr.talent.service.support.FactOrderMetadata;
import org.springframework.stereotype.Service;

/** SkillFact 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class SkillFactPersistenceServiceImpl extends ServiceImpl<SkillFactMapper, SkillFact>
    implements SkillFactPersistenceService {

  @Override
  public boolean insertSkillFacts(List<SkillFact> facts) {
    validateBatch(facts, MAX_RESUME_SKILLS, "skill facts");
    return facts.isEmpty() || saveBatch(facts, RESUME_FACT_WRITE_BATCH_SIZE);
  }

  /** 查询技能。 */
  @Override
  public List<SkillFact> findSkills(String id) {
    return FactOrderMetadata.restore(baseMapper.selectList(
        Wrappers.lambdaQuery(SkillFact.class)
            .eq(SkillFact::getFactSnapshotId, id)
            .orderByAsc(SkillFact::getSkillName, SkillFact::getItemKey)));
  }
}
