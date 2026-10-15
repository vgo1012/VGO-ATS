package com.ezaigc.hr.recruiting.demand.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandCollaboratorEntity;
import java.time.Instant;
import java.util.List;

/** DemandCollaboratorEntity 实体的持久化服务。 */
public interface DemandCollaboratorPersistenceService extends IService<DemandCollaboratorEntity> {

  List<String> findCollaboratorIds(String demandId);

  /** 多岗位工作列表批量读取协作人，避免按候选人逐行查询。 */
  List<DemandCollaboratorEntity> findForDemands(List<String> demandIds);

  /** 批量新增协作人；空列表视为无须写入。 */
  boolean insertCollaborators(String demandId, List<String> userIds, String actorId, Instant now);

  /** 删除协作人列表。 */
  int deleteCollaborators(String demandId);
}
