package com.ezaigc.hr.reporting.outbox.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ezaigc.hr.reporting.outbox.domain.entity.OutboxEventEntity;
import com.ezaigc.hr.reporting.outbox.mapper.OutboxEventMapper;
import com.ezaigc.hr.reporting.outbox.service.OutboxEventService;
import org.springframework.stereotype.Service;

@Service
public class OutboxEventServiceImpl extends ServiceImpl<OutboxEventMapper, OutboxEventEntity>
    implements OutboxEventService {
  @Override
  public OutboxEventEntity findForRecoveryTask(String recoveryTaskId) {
    return baseMapper.findForRecoveryTask(recoveryTaskId);
  }

  @Override
  public OutboxEventEntity findByWorkTaskId(String workTaskId) {
    return getOne(Wrappers.<OutboxEventEntity>lambdaQuery()
        .eq(OutboxEventEntity::getWorkTaskId, workTaskId));
  }

  @Override
  public OutboxEventEntity saveIfAbsent(OutboxEventEntity event) {
    int changed = baseMapper.insertIgnore(event);
    if (changed < 0 || changed > 1) throw new IllegalStateException("unexpected outbox insert count");
    OutboxEventEntity saved = baseMapper.findForUpdate(event.getId());
    if (saved == null) throw new IllegalStateException("outbox event was not persisted");
    return saved;
  }
}
