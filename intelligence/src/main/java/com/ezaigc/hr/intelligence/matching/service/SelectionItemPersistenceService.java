package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.SelectionItem;
import java.util.List;

/** SelectionItem 实体的持久化服务。 */
public interface SelectionItemPersistenceService extends IService<SelectionItem> {

  /** 批量新增候选选择条目，单批最多 1000 条。 */
  boolean insertSelectionItems(List<SelectionItem> items);

  /** 查询选择条目列表。 */
  List<SelectionItem> findSelectionItems(String id);
}
