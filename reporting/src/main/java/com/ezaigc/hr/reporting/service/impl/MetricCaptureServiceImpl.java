package com.ezaigc.hr.reporting.service.impl;

import static com.ezaigc.hr.common.core.constant.PlatformActorConstants.SYSTEM_ACTOR_ID;
import static com.ezaigc.hr.common.core.constant.PlatformIdConstants.MIN_ID;

import com.ezaigc.hr.reporting.domain.dto.MetricProjectionStatusDTO;
import com.ezaigc.hr.reporting.domain.entity.MetricCaptureCheckpointEntity;
import com.ezaigc.hr.reporting.enums.MetricCaptureLane;
import com.ezaigc.hr.reporting.enums.MetricEventType;
import com.ezaigc.hr.reporting.mapper.MetricCaptureCheckpointMapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.reporting.service.MetricCaptureService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 持久水位与指标事件由调用方的同一事务提交，补偿不修改已保存的历史事实。 */
@Service
public class MetricCaptureServiceImpl extends ServiceImpl<MetricCaptureCheckpointMapper, MetricCaptureCheckpointEntity>
    implements MetricCaptureService {
  @Resource private Clock clock;

  @Override
  public int captureNext(MetricEventType source, MetricCaptureLane lane, int limit) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(lane, "lane");
    if (limit < 1 || limit > 1000) throw new IllegalArgumentException("metric capture batch size must be between 1 and 1000");
    Instant now = clock.instant();
    String name = source.getCode() + ':' + lane.getCode();
    baseMapper.insertIfAbsent(name, source.getCode(), lane.getCode(), MIN_ID, SYSTEM_ACTOR_ID, now);
    var checkpoint = Objects.requireNonNull(baseMapper.lock(name), "metric checkpoint was not initialized");
    if (checkpoint.getCycleUpperId() == null) {
      if (lane == MetricCaptureLane.RECONCILIATION) checkpoint.setLastSourceId(MIN_ID);
      // 固定本周期上界，避免持续新增记录让历史补偿永远无法完成。
      checkpoint.setCycleUpperId(Objects.requireNonNullElse(upperId(source), MIN_ID));
      checkpoint.setCycleStartedAt(now);
    }
    List<String> ids = nextIds(source, checkpoint.getLastSourceId(), checkpoint.getCycleUpperId(), limit);
    if (!ids.isEmpty()) {
      captureRange(source, ids);
      checkpoint.setLastSourceId(ids.getLast());
    }
    if (ids.size() < limit || checkpoint.getLastSourceId().compareTo(checkpoint.getCycleUpperId()) >= 0) {
      // 使用周期起点作为保守的完整扫描水位，而不是最后一批或 GET 时间。
      checkpoint.setLastCompletedAt(checkpoint.getCycleStartedAt());
      checkpoint.setCycleUpperId(null);
      checkpoint.setCycleStartedAt(null);
    }
    if (baseMapper.advance(checkpoint, SYSTEM_ACTOR_ID, now) != 1) {
      throw new IllegalStateException("metric capture checkpoint changed");
    }
    return ids.size();
  }

  @Override
  public MetricProjectionStatusDTO status() {
    var rows = baseMapper.checkpoints();
    return new MetricProjectionStatusDTO(completedAt(rows, MetricCaptureLane.INCREMENTAL),
        completedAt(rows, MetricCaptureLane.RECONCILIATION));
  }

  private static Instant completedAt(List<MetricCaptureCheckpointEntity> rows, MetricCaptureLane lane) {
    Instant oldest = null;
    for (var source : MetricEventType.values()) {
      var completed = rows.stream().filter(row -> source.getCode().equals(row.getSourceCode())
          && lane.getCode().equals(row.getLane())).map(MetricCaptureCheckpointEntity::getLastCompletedAt)
          .filter(Objects::nonNull).findFirst().orElse(null);
      if (completed == null) return null;
      if (oldest == null || completed.isBefore(oldest)) oldest = completed;
    }
    return oldest;
  }

  private String upperId(MetricEventType source) {
    return switch (source) {
      case RESUME_INGESTED -> baseMapper.upperResumeIngestedId();
      case RESUME_PARSED -> baseMapper.upperResumeParsedId();
      case APPLICATION_CREATED -> baseMapper.upperApplicationsId();
      case HUMAN_SCREENED -> baseMapper.upperHumanScreenedId();
      case SUBMISSION_SENT -> baseMapper.upperSubmissionsId();
      case INTERVIEW_PASSED -> baseMapper.upperInterviewPassedId();
      case ONBOARDED -> baseMapper.upperOnboardedId();
    };
  }

  private List<String> nextIds(MetricEventType source, String afterId, String upperId, int limit) {
    return switch (source) {
      case RESUME_INGESTED -> baseMapper.nextResumeIngestedIds(afterId, upperId, limit);
      case RESUME_PARSED -> baseMapper.nextResumeParsedIds(afterId, upperId, limit);
      case APPLICATION_CREATED -> baseMapper.nextApplicationsIds(afterId, upperId, limit);
      case HUMAN_SCREENED -> baseMapper.nextHumanScreenedIds(afterId, upperId, limit);
      case SUBMISSION_SENT -> baseMapper.nextSubmissionsIds(afterId, upperId, limit);
      case INTERVIEW_PASSED -> baseMapper.nextInterviewPassedIds(afterId, upperId, limit);
      case ONBOARDED -> baseMapper.nextOnboardedIds(afterId, upperId, limit);
    };
  }

  private void captureRange(MetricEventType source, List<String> ids) {
    switch (source) {
      case RESUME_INGESTED -> baseMapper.captureResumeIngestedRange(ids);
      case RESUME_PARSED -> baseMapper.captureResumeParsedRange(ids);
      case APPLICATION_CREATED -> baseMapper.captureApplicationsRange(ids);
      case HUMAN_SCREENED -> baseMapper.captureHumanScreenedRange(ids);
      case SUBMISSION_SENT -> baseMapper.captureSubmissionsRange(ids);
      case INTERVIEW_PASSED -> baseMapper.captureInterviewPassedRange(ids);
      case ONBOARDED -> baseMapper.captureOnboardedRange(ids);
    }
  }
}
