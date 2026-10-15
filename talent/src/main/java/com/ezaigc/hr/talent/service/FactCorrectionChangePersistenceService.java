package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.FactCorrectionChange;
import java.util.List;

/** 字段级纠错明细的持久化边界。 */
public interface FactCorrectionChangePersistenceService extends IService<FactCorrectionChange> {

  /** 批量新增同一纠错记录的变更明细；空集合视为无需写入。 */
  boolean insertCorrectionChanges(List<FactCorrectionChange> changes);
}
