package com.ezaigc.hr.talent.service.impl;

import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_RESUME_PROJECT_EXPERIENCES;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_FACT_WRITE_BATCH_SIZE;
import static com.ezaigc.hr.talent.service.support.FactBatchValidation.validateBatch;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.ProjectFact;
import com.ezaigc.hr.talent.mapper.ProjectFactMapper;
import com.ezaigc.hr.talent.service.ProjectFactPersistenceService;
import java.util.List;
import com.ezaigc.hr.talent.service.support.FactOrderMetadata;
import org.springframework.stereotype.Service;

/** ProjectFact 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class ProjectFactPersistenceServiceImpl extends ServiceImpl<ProjectFactMapper, ProjectFact>
    implements ProjectFactPersistenceService {

  @Override
  public boolean insertProjectFacts(List<ProjectFact> facts) {
    validateBatch(facts, MAX_RESUME_PROJECT_EXPERIENCES, "project facts");
    return facts.isEmpty() || saveBatch(facts, RESUME_FACT_WRITE_BATCH_SIZE);
  }

  /** 查询项目列表。 */
  @Override
  public List<ProjectFact> findProjects(String id) {
    return FactOrderMetadata.restore(baseMapper.selectList(
        Wrappers.lambdaQuery(ProjectFact.class)
            .eq(ProjectFact::getFactSnapshotId, id)
            .orderByDesc(ProjectFact::getStartDate)
            .orderByAsc(ProjectFact::getItemKey)));
  }
}
