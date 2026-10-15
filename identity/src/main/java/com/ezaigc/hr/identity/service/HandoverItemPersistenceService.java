package com.ezaigc.hr.identity.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.identity.domain.entity.HandoverItemRecord;
import java.time.Instant;
import java.util.List;

/**
 * HandoverItemRecord 实体的持久化服务。
 */
public interface HandoverItemPersistenceService extends IService<HandoverItemRecord> {

    /**
     * 新增交接条目。
     */
    int insertHandoverItem(
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
            Instant processedAt);

    /**
     * 查询交接条目列表。
     */
    List<HandoverItemRecord> findHandoverItems(String batchId);
}
