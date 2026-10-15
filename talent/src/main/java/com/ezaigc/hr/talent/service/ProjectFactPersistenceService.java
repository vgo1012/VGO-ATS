package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.ProjectFact;
import java.util.List;

/** ProjectFact 实体的持久化服务。 */
public interface ProjectFactPersistenceService extends IService<ProjectFact> {

  /** 批量新增项目事实。 */
  boolean insertProjectFacts(List<ProjectFact> facts);

  /** 查询项目列表。 */
  List<ProjectFact> findProjects(String id);
}
