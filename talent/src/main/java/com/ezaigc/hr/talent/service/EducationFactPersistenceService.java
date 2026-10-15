package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.EducationFact;
import java.util.List;

/** EducationFact 实体的持久化服务。 */
public interface EducationFactPersistenceService extends IService<EducationFact> {

  /** 批量新增教育事实。 */
  boolean insertEducationFacts(List<EducationFact> facts);

  /** 查询教育经历。 */
  List<EducationFact> findEducation(String id);
}
