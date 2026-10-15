package com.ezaigc.hr.recruiting.recruitment.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.ApplicationCollaborator;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** ApplicationCollaborator 实体的持久化服务。 */
public interface ApplicationCollaboratorPersistenceService
    extends IService<ApplicationCollaborator> {

  /** 批量新增协作人；空列表视为无须写入。 */
  boolean insertCollaborators(
      String applicationId, List<String> userIds, String actorId, Instant now);

  /** 删除协作人列表。 */
  int deleteCollaborators(String applicationId);

  /** 查询协作人列表。 */
  List<String> findCollaborators(String applicationId);

  /** 已持有 Application 行锁时，当前读取协作人用于权限判定。 */
  List<String> findCollaboratorsForUpdate(String applicationId);

  List<ApplicationCollaborator> findCollaboratorsByApplicationIds(
      Collection<String> applicationIds);
}
