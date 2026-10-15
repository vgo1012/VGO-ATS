package com.ezaigc.hr.reporting.outbox.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.reporting.outbox.domain.entity.OutboxEventEntity;

/** Outbox 不可变事件存取，发布状态由共享任务 API 管理。 */
public interface OutboxEventService extends IService<OutboxEventEntity> {
  OutboxEventEntity findByWorkTaskId(String workTaskId);

  /** 恢复发布任务引用原信封，不复制或改变原 eventId。 */
  OutboxEventEntity findForRecoveryTask(String recoveryTaskId);

  /** 应用服务在工作任务、发布任务和业务事实共用的短事务内调用。 */
  OutboxEventEntity saveIfAbsent(OutboxEventEntity event);
}
