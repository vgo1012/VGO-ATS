package com.ezaigc.hr.recruiting.demand.service;
import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.demand.domain.entity.MailSourceMessageEntity;

public interface MailSourceMessagePersistenceService extends IService<MailSourceMessageEntity> {
  void insertIfAbsent(MailSourceMessageEntity row);
  MailSourceMessageEntity findExisting(String actorId, String messageHash);

}
