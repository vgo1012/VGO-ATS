package com.ezaigc.hr.recruiting.submission.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientSubmission;
import com.ezaigc.hr.recruiting.submission.mapper.SubmissionSqlOperations;
import java.time.Instant;
import java.util.List;

/**
 * 客户推荐持久化服务契约，定义业务数据的读写边界。
 *
 * <p>Persistence boundary used by submission managers and same-JVM adapters.
 */
public interface SubmissionPersistenceService
    extends IService<ClientSubmission>, SubmissionSqlOperations {
  /** 新增客户推荐。 */
  int insertSubmission(
      String id,
      String applicationId,
      String candidateId,
      String demandId,
      String clientId,
      String projectId,
      String positionId,
      String jdId,
      String jdVersionId,
      String resumeVersionId,
      String ownerUserId,
      String departmentId,
      String actorId,
      Instant now);

  ClientSubmission find(String id);
  String findCandidateId(String id);

  List<ClientSubmission> list(
      String applicationId,
      String clientId,
      String jdId,
      String deliveryStatus,
      String processStatus,
      String ownerUserId,
      Instant cursorTime,
      String cursorId,
      int limit);

  /** 标记发送。 */
  int markSent(String id, long version, String actorId, Instant now);

  /** 更新处理。 */
  int updateProcess(String id, String status, long version, String actorId, Instant now);

  int withdraw(String id, String reason, String actorId, long version, Instant now);
}
