package com.ezaigc.hr.recruiting.demand.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.demand.domain.entity.ProjectEntity;
import java.time.Instant;
import java.util.List;

/** ProjectEntity 实体的持久化服务。 */
public interface ProjectPersistenceService extends IService<ProjectEntity> {

  /** 查询项目列表。 */
  List<ProjectEntity> findProjects(
      String clientId, String status, Instant cursorTime, String cursorId, int limit);

  /** 查询项目。 */
  ProjectEntity findProject(String id);

  /** 统计项目编码。 */
  int countProjectCode(String clientId, String code);

  /** 新增项目。 */
  int insertProject(
      String id, String clientId, String code, String name, String actorId, Instant now);

  /** 更新项目。 */
  int updateProject(String id, String name, long version, String actorId, Instant now);

  /** 停用项目。 */
  int disableProject(String id, long version, String actorId, Instant now);
}
