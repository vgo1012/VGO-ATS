package com.ezaigc.hr.recruiting.recruitment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Communication;
import com.ezaigc.hr.recruiting.recruitment.mapper.CommunicationMapper;
import com.ezaigc.hr.recruiting.recruitment.service.CommunicationPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** Communication 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class CommunicationPersistenceServiceImpl
    extends ServiceImpl<CommunicationMapper, Communication>
    implements CommunicationPersistenceService {

  /** 新增沟通。 */
  @Override
  public int insertCommunication(
      String id,
      String applicationId,
      String channel,
      String summary,
      String result,
      String nextPlan,
      Instant communicatedAt,
      String actorId,
      Instant now) {
    Communication communication = new Communication();
    communication.setId(id);
    communication.setApplicationId(applicationId);
    communication.setChannel(channel);
    communication.setSummary(summary);
    communication.setResult(result);
    communication.setNextPlan(nextPlan);
    communication.setCommunicatedAt(communicatedAt);
    communication.setRecordedByUserId(actorId);
    communication.setCreateTime(now);
    return affected(this.save(communication));
  }

  /** 查询沟通列表。 */
  @Override
  public List<Communication> findCommunications(String applicationId) {
    return this.list(
        Wrappers.lambdaQuery(Communication.class)
            .eq(Communication::getApplicationId, applicationId)
            .orderByAsc(Communication::getCommunicatedAt, Communication::getId));
  }

  private static int affected(boolean changed) {
    return changed ? 1 : 0;
  }

  @Override
  public Communication findByIdForUpdate(String id) {
    return getOne(Wrappers.lambdaQuery(Communication.class)
        .eq(Communication::getId, id).last("FOR UPDATE"));
  }
}
