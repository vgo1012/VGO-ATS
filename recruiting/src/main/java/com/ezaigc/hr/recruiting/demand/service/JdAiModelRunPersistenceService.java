package com.ezaigc.hr.recruiting.demand.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdAiModelRunEntity;
import java.time.Instant;

/** JdAiModelRunEntity 实体的持久化服务。 */
public interface JdAiModelRunPersistenceService extends IService<JdAiModelRunEntity> {

  /** 仅终结精确任务和版本绑定的运行中记录，保留历史验证及输出元数据。 */
  int terminateWithoutWorker(String taskId, String versionId, String errorCode, Instant now, String actorId);

  /** 新增模型运行。 */
  int insertModelRun(
      String id,
      String versionId,
      String taskId,
      String schemaVersion,
      String promptVersion,
      Instant now);
}
