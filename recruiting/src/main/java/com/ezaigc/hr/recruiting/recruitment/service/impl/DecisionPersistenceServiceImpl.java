package com.ezaigc.hr.recruiting.recruitment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Decision;
import com.ezaigc.hr.recruiting.recruitment.mapper.DecisionMapper;
import com.ezaigc.hr.recruiting.recruitment.service.DecisionPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** Decision 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class DecisionPersistenceServiceImpl extends ServiceImpl<DecisionMapper, Decision>
    implements DecisionPersistenceService {

  /** 新增人工决定。 */
  @Override
  public int insertDecision(
      String id,
      String applicationId,
      String recordType,
      String decision,
      String reason,
      String correctsId,
      String clientSubmissionId,
      String actorId,
      Instant now) {
    Decision record = new Decision();
    record.setId(id);
    record.setApplicationId(applicationId);
    record.setRecordType(recordType);
    record.setDecision(decision);
    record.setReason(reason);
    record.setCorrectsRecordId(correctsId);
    record.setClientSubmissionId(clientSubmissionId);
    record.setDecidedByUserId(actorId);
    record.setDecidedAt(now);
    record.setCreateTime(now);
    return affected(this.save(record));
  }

  /** 查询决定列表。 */
  @Override
  public List<Decision> findDecisions(String applicationId) {
    return this.list(
        Wrappers.lambdaQuery(Decision.class)
            .eq(Decision::getApplicationId, applicationId)
            .orderByAsc(Decision::getCreateTime, Decision::getId));
  }

  /** 查询人工决定。 */
  @Override
  public Decision findDecision(String applicationId, String id) {
    return this.getOne(
        Wrappers.lambdaQuery(Decision.class)
            .eq(Decision::getApplicationId, applicationId)
            .eq(Decision::getId, id));
  }

  private static int affected(boolean changed) {
    return changed ? 1 : 0;
  }
}
