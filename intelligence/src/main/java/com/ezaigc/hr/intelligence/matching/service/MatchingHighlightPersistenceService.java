package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchingHighlight;
import java.util.List;

/** MatchingHighlight 实体的持久化服务。 */
public interface MatchingHighlightPersistenceService extends IService<MatchingHighlight> {

  /** 批量新增单个基础评价的亮点；空集合视为无需写入。 */
  boolean insertHighlightBatch(List<MatchingHighlight> highlights);
}
