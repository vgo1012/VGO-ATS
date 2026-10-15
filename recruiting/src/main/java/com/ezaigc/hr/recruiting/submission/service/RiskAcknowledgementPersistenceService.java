package com.ezaigc.hr.recruiting.submission.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.submission.domain.entity.RiskAcknowledgement;
import java.time.Instant;
import java.util.List;

/** RiskAcknowledgement 实体的持久化服务。 */
public interface RiskAcknowledgementPersistenceService extends IService<RiskAcknowledgement> {

  int insertRiskAck(
      String id, String submissionId, String riskCode, String reason, String actorId, Instant now);

  List<RiskAcknowledgement> riskAcks(String submissionId);
}
