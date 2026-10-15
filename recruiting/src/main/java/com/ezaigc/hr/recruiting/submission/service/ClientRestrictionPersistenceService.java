package com.ezaigc.hr.recruiting.submission.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientRestriction;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** ClientRestriction 实体的持久化服务。 */
public interface ClientRestrictionPersistenceService extends IService<ClientRestriction> {

  /** 批量查询有黑名单标签来源事实的招聘流程，包含已停用的历史标签。 */
  Set<String> applicationsWithBlacklistRecords(Set<String> applicationIds);

  /** 新增限制。 */
  int insertRestriction(
      String id,
      String clientId,
      String candidateId,
      String submissionId,
      String type,
      String status,
      String intent,
      Instant expiresAt,
      String detail,
      String source,
      Instant occurredAt,
      String correctsId,
      String actorId,
      Instant now);

  ClientRestriction restriction(String id);

  ClientRestriction revision(String correctsRecordId, String actorId);

  List<ClientRestriction> restrictions(String candidateId, String clientId, int limit);
}
