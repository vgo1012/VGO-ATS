package com.ezaigc.hr.identity.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.identity.domain.entity.WecomSyncStateEntity;
import com.ezaigc.hr.identity.mapper.WecomSyncStateMapper;
import com.ezaigc.hr.identity.service.WecomSyncStatePersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class WecomSyncStatePersistenceServiceImpl extends ServiceImpl<WecomSyncStateMapper, WecomSyncStateEntity>
        implements WecomSyncStatePersistenceService {
    public void ensure(String corpId) { baseMapper.ensure(corpId); }
    public WecomSyncStateEntity lock(String corpId) { return baseMapper.lock(corpId); }
    public Instant databaseNow() { return baseMapper.databaseNow(); }
}
