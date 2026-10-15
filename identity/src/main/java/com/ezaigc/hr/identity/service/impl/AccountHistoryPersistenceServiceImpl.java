package com.ezaigc.hr.identity.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.identity.domain.entity.AccountHistory;
import com.ezaigc.hr.identity.mapper.AccountHistoryMapper;
import com.ezaigc.hr.identity.service.AccountHistoryPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * AccountHistory 实体的 MyBatis-Plus 持久化实现。
 */
@Service
public class AccountHistoryPersistenceServiceImpl
        extends ServiceImpl<AccountHistoryMapper, AccountHistory>
        implements AccountHistoryPersistenceService {

    /**
     * 新增账户历史。
     */
    @Override
    public int insertAccountHistory(
            String id,
            String userId,
            String changeType,
            String beforeSnapshot,
            String afterSnapshot,
            String reason,
            String actorId,
            Instant now,
            long accountVersion) {
        AccountHistory history = new AccountHistory();
        history.setId(id);
        history.setUserId(userId);
        history.setChangeType(changeType);
        history.setBeforeSnapshot(beforeSnapshot);
        history.setAfterSnapshot(afterSnapshot);
        history.setReason(reason);
        history.setActorUserId(actorId);
        history.setOccurredAt(now);
        history.setAccountVersion(accountVersion);
        return baseMapper.insert(history);
    }
}
