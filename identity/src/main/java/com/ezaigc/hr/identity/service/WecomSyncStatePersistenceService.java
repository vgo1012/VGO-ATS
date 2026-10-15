package com.ezaigc.hr.identity.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.identity.domain.entity.WecomSyncStateEntity;
import java.time.Instant;

public interface WecomSyncStatePersistenceService extends IService<WecomSyncStateEntity> {
    void ensure(String corpId);
    WecomSyncStateEntity lock(String corpId);
    Instant databaseNow();
}
