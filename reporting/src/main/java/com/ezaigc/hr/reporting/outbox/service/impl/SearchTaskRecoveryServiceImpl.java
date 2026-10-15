package com.ezaigc.hr.reporting.outbox.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.reporting.outbox.domain.entity.SearchTaskRecoveryEntity;
import com.ezaigc.hr.reporting.outbox.mapper.SearchTaskRecoveryMapper;
import com.ezaigc.hr.reporting.outbox.service.SearchTaskRecoveryService;
import org.springframework.stereotype.Service;

@Service
public class SearchTaskRecoveryServiceImpl extends ServiceImpl<SearchTaskRecoveryMapper, SearchTaskRecoveryEntity>
    implements SearchTaskRecoveryService {
  @Override
  public SearchTaskRecoveryEntity findByOriginalTaskId(String originalTaskId) {
    return getOne(Wrappers.<SearchTaskRecoveryEntity>lambdaQuery()
        .eq(SearchTaskRecoveryEntity::getOriginalTaskId, originalTaskId));
  }
}
