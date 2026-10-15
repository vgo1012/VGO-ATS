package com.ezaigc.hr.reporting.service.impl;

import static com.ezaigc.hr.common.core.constant.PlatformActorConstants.SYSTEM_ACTOR_ID;
import static com.ezaigc.hr.common.core.constant.PlatformIdConstants.MIN_ID;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.reporting.domain.entity.AuditCaptureCheckpointEntity;
import com.ezaigc.hr.reporting.enums.AuditCaptureLane;
import com.ezaigc.hr.reporting.enums.AuditCaptureSource;
import com.ezaigc.hr.reporting.mapper.AuditCaptureCheckpointMapper;
import com.ezaigc.hr.reporting.service.AuditCaptureService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 审计投影增量采集服务实现。 */
@Service
public class AuditCaptureServiceImpl
    extends ServiceImpl<AuditCaptureCheckpointMapper, AuditCaptureCheckpointEntity>
    implements AuditCaptureService {

  @Resource private Clock clock;

  /** 在单个短事务中采集一个来源通道的下一批事件。 */
  @Override
  public int captureNext(AuditCaptureSource source, AuditCaptureLane lane, int limit) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(lane, "lane");
    if (limit < 1 || limit > MAX_BATCH_SIZE) {
      throw new IllegalArgumentException("audit capture batch size must be between 1 and 1000");
    }

    Instant now = clock.instant();
    String checkpointName = source.checkpointName(lane);
    baseMapper.insertIfAbsent(
        checkpointName, source.getCode(), lane.getCode(), MIN_ID, SYSTEM_ACTOR_ID, now);
    AuditCaptureCheckpointEntity checkpoint = baseMapper.lock(checkpointName);
    if (checkpoint == null) {
      throw new IllegalStateException("audit capture checkpoint was not initialized");
    }

    List<String> ids = nextIds(source, checkpoint.getLastSourceId(), limit);
    if (ids.isEmpty()) {
      restartReconciliationIfComplete(checkpoint, lane, now);
      return 0;
    }

    String throughId = ids.getLast();
    captureRange(source, checkpoint.getLastSourceId(), throughId);
    if (baseMapper.advance(
            checkpointName,
            throughId,
            ids.size(),
            checkpoint.getVersion(),
            SYSTEM_ACTOR_ID,
            now)
        != 1) {
      throw new IllegalStateException("audit capture checkpoint changed");
    }
    return ids.size();
  }

  private void restartReconciliationIfComplete(
      AuditCaptureCheckpointEntity checkpoint, AuditCaptureLane lane, Instant now) {
    if (lane != AuditCaptureLane.RECONCILIATION
        || MIN_ID.equals(checkpoint.getLastSourceId())) {
      return;
    }
    if (baseMapper.reset(
            checkpoint.getCheckpointName(),
            MIN_ID,
            checkpoint.getVersion(),
            SYSTEM_ACTOR_ID,
            now)
        != 1) {
      throw new IllegalStateException("audit reconciliation checkpoint changed");
    }
  }

  private List<String> nextIds(AuditCaptureSource source, String afterId, int limit) {
    if (source == AuditCaptureSource.IDENTITY) {
      return baseMapper.nextIdentityIds(afterId, limit);
    }
    if (source == AuditCaptureSource.DEMAND) {
      return baseMapper.nextDemandIds(afterId, limit);
    }
    if (source == AuditCaptureSource.MATCHING) {
      return baseMapper.nextMatchingIds(afterId, limit);
    }
    if (source == AuditCaptureSource.TALENT) {
      return baseMapper.nextTalentIds(afterId, limit);
    }
    if (source == AuditCaptureSource.RECRUITMENT_STAGE) {
      return baseMapper.nextRecruitmentStageIds(afterId, limit);
    }
    if (source == AuditCaptureSource.RECRUITMENT_DECISION) {
      return baseMapper.nextRecruitmentDecisionIds(afterId, limit);
    }
    if (source == AuditCaptureSource.INTERVIEW) {
      return baseMapper.nextInterviewIds(afterId, limit);
    }
    if (source == AuditCaptureSource.SUBMISSION_SEND) {
      return baseMapper.nextSubmissionSendIds(afterId, limit);
    }
    throw new IllegalArgumentException("unsupported audit capture source: " + source);
  }

  private void captureRange(AuditCaptureSource source, String afterId, String throughId) {
    if (source == AuditCaptureSource.IDENTITY) {
      baseMapper.captureIdentityRange(afterId, throughId);
    } else if (source == AuditCaptureSource.DEMAND) {
      baseMapper.captureDemandRange(afterId, throughId);
    } else if (source == AuditCaptureSource.MATCHING) {
      baseMapper.captureMatchingRange(afterId, throughId);
    } else if (source == AuditCaptureSource.TALENT) {
      baseMapper.captureTalentRange(afterId, throughId);
    } else if (source == AuditCaptureSource.RECRUITMENT_STAGE) {
      baseMapper.captureRecruitmentStageRange(afterId, throughId);
    } else if (source == AuditCaptureSource.RECRUITMENT_DECISION) {
      baseMapper.captureRecruitmentDecisionRange(afterId, throughId);
    } else if (source == AuditCaptureSource.INTERVIEW) {
      baseMapper.captureInterviewRange(afterId, throughId);
    } else if (source == AuditCaptureSource.SUBMISSION_SEND) {
      baseMapper.captureSubmissionSendRange(afterId, throughId);
    } else {
      throw new IllegalArgumentException("unsupported audit capture source: " + source);
    }
  }
}
