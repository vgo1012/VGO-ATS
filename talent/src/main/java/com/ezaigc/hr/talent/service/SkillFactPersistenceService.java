package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.SkillFact;
import java.util.List;

/** SkillFact 实体的持久化服务。 */
public interface SkillFactPersistenceService extends IService<SkillFact> {

  /** 批量新增技能事实。 */
  boolean insertSkillFacts(List<SkillFact> facts);

  /** 查询技能。 */
  List<SkillFact> findSkills(String id);
}
