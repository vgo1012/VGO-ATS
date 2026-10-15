package com.ezaigc.hr.recruiting.recruitment.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Communication;
import java.time.Instant;
import java.util.List;

/** Communication 实体的持久化服务。 */
public interface CommunicationPersistenceService extends IService<Communication> {

  /** 新增沟通。 */
  int insertCommunication(
      String id,
      String applicationId,
      String channel,
      String summary,
      String result,
      String nextPlan,
      Instant communicatedAt,
      String actorId,
      Instant now);

  /** 查询沟通列表。 */
  List<Communication> findCommunications(String applicationId);

  /** 按主键当前锁读，供幂等重放读取并发提交的原结果。 */
  Communication findByIdForUpdate(String id);
}
