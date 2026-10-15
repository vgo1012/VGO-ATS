package com.ezaigc.hr.recruiting.submission.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.submission.domain.entity.SendSnapshot;
import com.ezaigc.hr.recruiting.submission.mapper.SendSnapshotMapper;
import com.ezaigc.hr.recruiting.submission.service.SendSnapshotPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** SendSnapshot 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class SendSnapshotPersistenceServiceImpl
    extends ServiceImpl<SendSnapshotMapper, SendSnapshot>
    implements SendSnapshotPersistenceService {

  /** 新增发送快照。 */
  @Override
  public int insertSendSnapshot(
      String id,
      String submissionId,
      String resumeVersionId,
      String recipient,
      String channel,
      String fileName,
      String sha256,
      String actorId,
      Instant now) {
    SendSnapshot snapshot = new SendSnapshot();
    snapshot.setId(id);
    snapshot.setSubmissionId(submissionId);
    snapshot.setResumeVersionId(resumeVersionId);
    snapshot.setRecipient(recipient);
    snapshot.setChannel(channel);
    snapshot.setFileName(fileName);
    snapshot.setContentSha256(sha256);
    snapshot.setSentByUserId(actorId);
    snapshot.setSentAt(now);
    return baseMapper.insert(snapshot);
  }

  @Override
  public SendSnapshot snapshot(String submissionId) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(SendSnapshot.class).eq(SendSnapshot::getSubmissionId, submissionId));
  }
}
