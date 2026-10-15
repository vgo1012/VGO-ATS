package com.ezaigc.hr.recruiting.submission.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.submission.domain.entity.RiskAcknowledgement;
import com.ezaigc.hr.recruiting.submission.mapper.RiskAcknowledgementMapper;
import com.ezaigc.hr.recruiting.submission.service.RiskAcknowledgementPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** RiskAcknowledgement 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class RiskAcknowledgementPersistenceServiceImpl
    extends ServiceImpl<RiskAcknowledgementMapper, RiskAcknowledgement>
    implements RiskAcknowledgementPersistenceService {

  @Override
  public int insertRiskAck(
      String id, String submissionId, String riskCode, String reason, String actorId, Instant now) {
    RiskAcknowledgement acknowledgement = new RiskAcknowledgement();
    acknowledgement.setId(id);
    acknowledgement.setSubmissionId(submissionId);
    acknowledgement.setRiskCode(riskCode);
    acknowledgement.setReason(reason);
    acknowledgement.setAcknowledgedByUserId(actorId);
    acknowledgement.setCreateTime(now);
    return baseMapper.insert(acknowledgement);
  }

  @Override
  public List<RiskAcknowledgement> riskAcks(String submissionId) {
    return baseMapper.selectList(
        Wrappers.lambdaQuery(RiskAcknowledgement.class)
            .eq(RiskAcknowledgement::getSubmissionId, submissionId)
            .orderByAsc(RiskAcknowledgement::getCreateTime, RiskAcknowledgement::getId));
  }
}
