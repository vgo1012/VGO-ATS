package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.SimpleFact;
import java.util.List;

/** SimpleFact 实体的持久化服务。 */
public interface SimpleFactPersistenceService extends IService<SimpleFact> {

  /** 批量新增简单事实。 */
  boolean insertSimpleFacts(List<SimpleFact> facts);

  List<SimpleFact> findSimple(String id);
}
