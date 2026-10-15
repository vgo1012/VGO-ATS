package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.WorkFact;
import java.util.List;

/** WorkFact 实体的持久化服务。 */
public interface WorkFactPersistenceService extends IService<WorkFact> {

  /** 批量新增工作事实。 */
  boolean insertWorkFacts(List<WorkFact> facts);

  /** 查询工作。 */
  List<WorkFact> findWork(String id);
}
