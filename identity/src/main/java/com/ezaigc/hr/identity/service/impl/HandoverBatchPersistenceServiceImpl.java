package com.ezaigc.hr.identity.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.identity.domain.entity.HandoverBatchRecord;
import com.ezaigc.hr.identity.mapper.HandoverBatchMapper;
import com.ezaigc.hr.identity.service.HandoverBatchPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * HandoverBatchRecord 实体的 MyBatis-Plus 持久化实现。
 */
@Service
public class HandoverBatchPersistenceServiceImpl
        extends ServiceImpl<HandoverBatchMapper, HandoverBatchRecord>
        implements HandoverBatchPersistenceService {

    /**
     * 查询交接操作人幂等。
     */
    @Override
    public HandoverBatchRecord findHandoverByIdempotency(String actorId, String idempotencyKey) {
        return baseMapper.selectOne(
                Wrappers.lambdaQuery(HandoverBatchRecord.class)
                        .eq(HandoverBatchRecord::getRequestedByUserId, actorId)
                        .eq(HandoverBatchRecord::getIdempotencyKey, idempotencyKey));
    }

    @Override
    public HandoverBatchRecord findHandoverById(String id) {
        return baseMapper.selectById(id);
    }

    /**
     * 新增交接批次。
     */
    @Override
    public int insertHandoverBatch(
            String id,
            String sourceUserId,
            String targetUserId,
            String selectionMode,
            String status,
            int totalCount,
            int unresolvedCount,
            String reason,
            String actorId,
            Instant startedAt,
            Instant completedAt,
            String idempotencyKey) {
        HandoverBatchRecord batch = new HandoverBatchRecord();
        batch.setId(id);
        batch.setSourceUserId(sourceUserId);
        batch.setTargetUserId(targetUserId);
        batch.setSelectionMode(selectionMode);
        batch.setStatus(status);
        batch.setTotalCount(totalCount);
        batch.setSucceededCount(0);
        batch.setFailedCount(0);
        batch.setUnresolvedCount(unresolvedCount);
        batch.setReason(reason);
        batch.setRequestedByUserId(actorId);
        batch.setStartedAt(startedAt);
        batch.setCompletedAt(completedAt);
        batch.setVersion(0L);
        batch.setIdempotencyKey(idempotencyKey);
        return baseMapper.insert(batch);
    }
}
