package com.ezaigc.hr.recruiting.recruitment.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Decision;
import java.time.Instant;
import java.util.List;

/** Decision 实体的持久化服务。 */
public interface DecisionPersistenceService extends IService<Decision> {

  /** 新增人工决定。 */
  int insertDecision(
      String id,
      String applicationId,
      String recordType,
      String decision,
      String reason,
      String correctsId,
      String clientSubmissionId,
      String actorId,
      Instant now);

  /** 查询决定列表。 */
  List<Decision> findDecisions(String applicationId);

  /** 查询人工决定。 */
  Decision findDecision(String applicationId, String id);
}
