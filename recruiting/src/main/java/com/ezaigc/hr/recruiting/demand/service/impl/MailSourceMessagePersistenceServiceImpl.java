package com.ezaigc.hr.recruiting.demand.service.impl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.demand.domain.entity.MailSourceMessageEntity;
import com.ezaigc.hr.recruiting.demand.mapper.MailSourceMessageMapper;
import com.ezaigc.hr.recruiting.demand.service.MailSourceMessagePersistenceService;
import org.springframework.stereotype.Service;

@Service
public class MailSourceMessagePersistenceServiceImpl extends ServiceImpl<MailSourceMessageMapper,MailSourceMessageEntity> implements MailSourceMessagePersistenceService {
  public void insertIfAbsent(MailSourceMessageEntity row) { baseMapper.insertIfAbsent(row); }
  public MailSourceMessageEntity findExisting(String actorId, String messageHash) {
    return baseMapper.selectOne(Wrappers.lambdaQuery(MailSourceMessageEntity.class).eq(MailSourceMessageEntity::getCreateId,actorId).eq(MailSourceMessageEntity::getMessageHash,messageHash).last("FOR UPDATE"));
  }

}
