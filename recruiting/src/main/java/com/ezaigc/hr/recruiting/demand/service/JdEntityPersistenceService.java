package com.ezaigc.hr.recruiting.demand.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import java.time.Instant;
import java.util.List;

/** JdEntity 实体的持久化服务。 */
public interface JdEntityPersistenceService extends IService<JdEntity> {
  /** 调用方持有写事务；部门首次使用时分配两位码，已发代码永久保留；用尽返回 null。 */
  String departmentCode(String departmentId);

  /** 调用方持有写事务；按创建时部门、岗位和地区分配不可回收的编号。 */
  String nextCode(String departmentCode, String jobTypeCode, String workRegionCode);

  void lockCodeAllocation();


  int updateCatalog(JdEntity jd, long expectedVersion);

  int updateRecruitmentMetadata(
      String id,
      int headcount,
      int recruitmentCycleDays,
      String ownerUserId,
      String ownerDepartmentId,
      boolean contentShared,
      long expectedVersion,
      String actorId,
      Instant now);

  int softDelete(String id, long version, String actorId, Instant now);

  int bindDemand(String jdId, String demandId, String actorId, Instant now);

  int countJdsForDemand(String demandId);

  String findJdIdByDemand(String demandId);

  JdEntity findSourceJd(String ownerUserId, String fingerprint);

  int updateJdVersionNo(String id, String title, int versionNo, String actorId, Instant now);

  List<JdEntity> findPrivateOwnedJds(String ownerUserId, int limit);

  int transferPrivateOwner(
      String id,
      String sourceUserId,
      String targetUserId,
      String targetDepartmentId,
      int version,
      String actorUserId,
      Instant now);
}
