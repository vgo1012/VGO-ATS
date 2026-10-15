package com.ezaigc.hr.identity.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.identity.domain.entity.HandoverItemRecord;
import com.ezaigc.hr.identity.mapper.HandoverItemMapper;
import com.ezaigc.hr.identity.service.HandoverItemPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** HandoverItemRecord 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class HandoverItemPersistenceServiceImpl
    extends ServiceImpl<HandoverItemMapper, HandoverItemRecord>
    implements HandoverItemPersistenceService {

  /** 新增交接条目。 */
  @Override
  public int insertHandoverItem(
      String id,
      String batchId,
      String resourceType,
      String resourceId,
      String status,
      boolean mandatory,
      String errorCode,
      String errorMessage,
      String sourceUserId,
      String targetUserId,
      String ownershipHistoryId,
      Instant processedAt) {
    HandoverItemRecord item = new HandoverItemRecord();
    item.setId(id);
    item.setBatchId(batchId);
    item.setResourceType(resourceType);
    item.setResourceId(resourceId);
    item.setStatus(status);
    item.setMandatory(mandatory);
    item.setErrorCode(errorCode);
    item.setErrorMessage(errorMessage);
    item.setFromOwnerUserId(sourceUserId);
    item.setToOwnerUserId(targetUserId);
    item.setOwnershipHistoryId(ownershipHistoryId);
    item.setProcessedAt(processedAt);
    return baseMapper.insert(item);
  }

  /** 查询交接条目列表。 */
  @Override
  public List<HandoverItemRecord> findHandoverItems(String batchId) {
    return baseMapper.selectList(
        Wrappers.lambdaQuery(HandoverItemRecord.class)
            .eq(HandoverItemRecord::getBatchId, batchId)
            .orderByAsc(HandoverItemRecord::getResourceType, HandoverItemRecord::getResourceId));
  }
}
