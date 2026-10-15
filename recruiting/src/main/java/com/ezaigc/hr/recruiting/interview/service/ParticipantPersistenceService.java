package com.ezaigc.hr.recruiting.interview.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.interview.domain.entity.Participant;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Participant 实体的持久化服务。 */
public interface ParticipantPersistenceService extends IService<Participant> {

  /** 批量新增参与者；空列表视为无须写入。 */
  boolean insertParticipants(String interviewId, List<String> userIds, String role, Instant now);

  /** 删除参与者列表。 */
  int deleteParticipants(String interviewId);

  /** 查询参与者列表。 */
  List<Participant> findParticipants(String interviewId);

  List<Participant> findParticipantsByInterviewIds(Collection<String> interviewIds);

  /** 通知事务持有相关面试行锁后，用当前读获取参与人，避免采用等待锁之前的旧快照。 */
  List<Participant> lockParticipantsByInterviewIds(Collection<String> interviewIds);

}
