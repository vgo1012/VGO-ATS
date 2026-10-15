package com.ezaigc.hr.reporting.outbox.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.reporting.outbox.domain.entity.SearchTaskRecoveryEntity;

public interface SearchTaskRecoveryService extends IService<SearchTaskRecoveryEntity> {
  SearchTaskRecoveryEntity findByOriginalTaskId(String originalTaskId);
}
