package com.ezaigc.hr.identity.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.identity.domain.entity.WecomSyncBatchEntity;
import com.ezaigc.hr.identity.mapper.WecomSyncBatchMapper;
import com.ezaigc.hr.identity.service.WecomSyncBatchPersistenceService;
import org.springframework.stereotype.Service;

@Service
public class WecomSyncBatchPersistenceServiceImpl extends ServiceImpl<WecomSyncBatchMapper, WecomSyncBatchEntity>
        implements WecomSyncBatchPersistenceService {}
