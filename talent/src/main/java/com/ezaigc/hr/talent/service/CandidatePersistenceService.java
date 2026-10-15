package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import java.time.Instant;

/** Candidate 实体的持久化服务。 */
public interface CandidatePersistenceService extends IService<Candidate> {

  /** 调用方持有候选人行锁；包含当前及终态历史。 */
  boolean hasMergeProtectedHistory(String candidateId);
  String lockCandidate(String id);

  /** 完整候选人当前读，不追随合并别名；调用方负责状态和权限。 */
  Candidate findCandidateForUpdate(String id);

  int updateLibraryReview(String id, long version, String ownerId, String ext, Instant now);

  int reclaimFromLibrary(
      String id,
      long version,
      String ownerId,
      String departmentId,
      String ext,
      String actorId,
      Instant now);

  int markReturnedToShared(
      String id, long version, String ext, String actorId, Instant now);

  /** 新增候选人。 */
  int insertCandidate(
      String id,
      String name,
      String email,
      String phone,
      String ownerId,
      String departmentId,
      String actorId,
      Instant now);

  /** 查询候选人。 */
  Candidate findCandidate(String id);

  /** 更新候选人。 */
  int updateCandidate(
      String id,
      String name,
      String email,
      String phone,
      String ownerId,
      String departmentId,
      long version,
      String actorId,
      Instant now);

  /** 删除候选人。 */
  int deleteCandidate(String id, long version, String actorId, Instant now);

  /** 标记候选人被合并。 */
  int markCandidateMerged(String id, long version, String actorId, Instant now);
}
