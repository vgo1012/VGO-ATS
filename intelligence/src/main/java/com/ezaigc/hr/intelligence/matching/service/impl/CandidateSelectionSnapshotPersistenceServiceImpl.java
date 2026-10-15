package com.ezaigc.hr.intelligence.matching.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.matching.domain.entity.CandidateSelectionSnapshot;
import com.ezaigc.hr.intelligence.matching.mapper.CandidateSelectionSnapshotMapper;
import com.ezaigc.hr.intelligence.matching.service.CandidateSelectionSnapshotPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** CandidateSelectionSnapshot 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class CandidateSelectionSnapshotPersistenceServiceImpl
    extends ServiceImpl<CandidateSelectionSnapshotMapper, CandidateSelectionSnapshot>
    implements CandidateSelectionSnapshotPersistenceService {

  /** 新增选择。 */
  @Override
  public int insertSelection(
      String id, String jdVersionId, String actorId, int count, String hash, Instant now) {
    CandidateSelectionSnapshot snapshot = new CandidateSelectionSnapshot();
    snapshot.setId(id);
    snapshot.setJdVersionId(jdVersionId);
    snapshot.setRequestedByUserId(actorId);
    snapshot.setItemCount(count);
    snapshot.setSelectionHash(hash);
    snapshot.setCreateTime(now);
    return baseMapper.insert(snapshot);
  }
}
