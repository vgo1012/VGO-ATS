package com.ezaigc.hr.recruiting.demand.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandEntity;
import java.time.Instant;

/** DemandEntity 实体的持久化服务。 */
public interface DemandEntityPersistenceService extends IService<DemandEntity> {

  /** 查询招聘需求。 */
  DemandEntity findDemand(String id);

  /** 统计招聘需求编码。 */
  int countDemandCode(String code);

  /** 新增招聘需求。 */
  int insertDemand(
      String id,
      String code,
      String title,
      String clientId,
      String projectId,
      String positionId,
      String recruitmentType,
      int headcount,
      String description,
      String ownerUserId,
      String ownerDepartmentId,
      String actorId,
      Instant now);

  /** 更新招聘需求。 */
  int updateDemand(
      String id,
      String title,
      String clientId,
      String projectId,
      String positionId,
      String recruitmentType,
      int headcount,
      String description,
      String ownerUserId,
      String ownerDepartmentId,
      long version,
      String actorId,
      Instant now);

  int transitionDemand(
      String id, String fromStatus, String toStatus, long version, String actorId, Instant now);

  /** 删除招聘需求。 */
  int deleteDemand(String id, long version);
}
