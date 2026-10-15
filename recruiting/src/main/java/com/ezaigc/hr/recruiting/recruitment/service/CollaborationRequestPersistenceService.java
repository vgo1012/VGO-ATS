package com.ezaigc.hr.recruiting.recruitment.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.CollaborationRequest;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** CollaborationRequest 实体的持久化服务。 */
public interface CollaborationRequestPersistenceService extends IService<CollaborationRequest> {

  CollaborationRequest activeCollaboration(String lockId, String userId);

  /** 批量查询指定用户在一组跟进锁上的有效协作请求。 */
  List<CollaborationRequest> activeCollaborations(Collection<String> lockIds, String userId);

  List<CollaborationRequest> collaborations(String candidateId, int limit);

  int cancelCollaborations(String lockId, String actorId, String reason, Instant now);
}
