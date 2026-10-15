package com.ezaigc.hr.intelligence.matching.service.impl;

import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_MATCH_RUN_RESUMES;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.matching.domain.entity.SelectionItem;
import com.ezaigc.hr.intelligence.matching.mapper.SelectionItemMapper;
import com.ezaigc.hr.intelligence.matching.service.SelectionItemPersistenceService;
import java.util.List;
import org.springframework.stereotype.Service;

/** SelectionItem 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class SelectionItemPersistenceServiceImpl
    extends ServiceImpl<SelectionItemMapper, SelectionItem>
    implements SelectionItemPersistenceService {

  private static final int JDBC_BATCH_SIZE = 200;

  /** 批量新增候选选择条目。 */
  @Override
  public boolean insertSelectionItems(List<SelectionItem> items) {
    if (items == null || items.isEmpty() || items.size() > MAX_MATCH_RUN_RESUMES) {
      throw new IllegalArgumentException("selection item batch size must be between 1 and 1000");
    }
    return saveBatch(items, JDBC_BATCH_SIZE);
  }

  /** 查询选择条目列表。 */
  @Override
  public List<SelectionItem> findSelectionItems(String id) {
    return baseMapper.selectList(
        Wrappers.lambdaQuery(SelectionItem.class)
            .eq(SelectionItem::getSelectionSnapshotId, id)
            .orderByAsc(SelectionItem::getSequenceNo));
  }
}
