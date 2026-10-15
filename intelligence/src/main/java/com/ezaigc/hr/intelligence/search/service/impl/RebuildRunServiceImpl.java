package com.ezaigc.hr.intelligence.search.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.search.domain.entity.RebuildRun;
import com.ezaigc.hr.intelligence.search.enums.SearchRebuildStatus;
import com.ezaigc.hr.intelligence.search.mapper.RebuildRunMapper;
import com.ezaigc.hr.intelligence.search.service.RebuildRunService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** 重建运行业务服务实现。 */
@Service
public class RebuildRunServiceImpl extends ServiceImpl<RebuildRunMapper, RebuildRun>
    implements RebuildRunService {

  @Override public RebuildRun currentForUpdate(String id) { return baseMapper.currentForUpdate(id); }

  @Override
  public RebuildRun findSucceededTarget(String targetIndex) {
    return getOne(Wrappers.<RebuildRun>lambdaQuery()
        .eq(RebuildRun::getTargetIndex, targetIndex)
        .eq(RebuildRun::getStatus, SearchRebuildStatus.SUCCEEDED.getCode()).last("LIMIT 2"));
  }

  @Override
  public boolean finishApproved(String id, String taskId, String targetIndex, long count,
      String actorId, Instant now) {
    return update(Wrappers.<RebuildRun>lambdaUpdate()
        .set(RebuildRun::getStatus, SearchRebuildStatus.SUCCEEDED.getCode())
        .set(RebuildRun::getProjectedCount, count)
        .set(RebuildRun::getErrorCode, null)
        .set(RebuildRun::getFinishedAt, now)
        .set(RebuildRun::getUpdateId, actorId)
        .set(RebuildRun::getUpdateTime, now)
        .setIncrBy(RebuildRun::getVersion, 1)
        .eq(RebuildRun::getId, id).eq(RebuildRun::getTaskId, taskId)
        .eq(RebuildRun::getTargetIndex, targetIndex)
        .eq(RebuildRun::getStatus, SearchRebuildStatus.RUNNING.getCode()));
  }

  @Override
  public RebuildRun findByTaskId(String taskId) {
    return getOne(
        Wrappers.<RebuildRun>lambdaQuery()
            .eq(RebuildRun::getTaskId, taskId)
            .last("LIMIT 1"));
  }

  @Override
  public boolean start(String id, String targetIndex, String updateId, Instant startedAt) {
    return update(
        Wrappers.<RebuildRun>lambdaUpdate()
            .set(RebuildRun::getStatus, SearchRebuildStatus.RUNNING.getCode())
            .set(RebuildRun::getTargetIndex, targetIndex)
            .set(RebuildRun::getProjectedCount, 0)
            .set(RebuildRun::getErrorCode, null)
            .set(RebuildRun::getFinishedAt, null)
            .set(RebuildRun::getStartedAt, startedAt)
            .set(RebuildRun::getUpdateId, updateId)
            .set(RebuildRun::getUpdateTime, startedAt)
            .setIncrBy(RebuildRun::getVersion, 1)
            .eq(RebuildRun::getId, id)
            .ne(RebuildRun::getTargetIndex, targetIndex)
            .in(
                RebuildRun::getStatus,
                SearchRebuildStatus.QUEUED.getCode(),
                SearchRebuildStatus.FAILED.getCode(),
                SearchRebuildStatus.RUNNING.getCode()));
  }

  @Override
  public boolean terminateWithoutWorker(String id, String taskId, SearchRebuildStatus status,
      String errorCode, String updateId, Instant finishedAt) {
    if (status != SearchRebuildStatus.FAILED && status != SearchRebuildStatus.CANCELLED) {
      throw new IllegalArgumentException("workerless completion requires a failed or cancelled rebuild");
    }
    return update(Wrappers.<RebuildRun>lambdaUpdate()
        .set(RebuildRun::getStatus, status.getCode())
        .set(RebuildRun::getErrorCode, status == SearchRebuildStatus.FAILED ? errorCode : null)
        .set(RebuildRun::getFinishedAt, finishedAt)
        .set(RebuildRun::getUpdateId, updateId)
        .set(RebuildRun::getUpdateTime, finishedAt)
        .setIncrBy(RebuildRun::getVersion, 1)
        .eq(RebuildRun::getId, id)
        .eq(RebuildRun::getTaskId, taskId)
        .in(RebuildRun::getStatus, status == SearchRebuildStatus.CANCELLED
            ? new String[] {SearchRebuildStatus.QUEUED.getCode(), SearchRebuildStatus.RUNNING.getCode(),
                SearchRebuildStatus.FAILED.getCode()}
            : new String[] {SearchRebuildStatus.QUEUED.getCode(), SearchRebuildStatus.RUNNING.getCode()}));
  }

  @Override
  public boolean finish(
      String id,
      SearchRebuildStatus status,
      long projectedCount,
      String errorCode,
      String updateId,
      Instant finishedAt) {
    return update(
        Wrappers.<RebuildRun>lambdaUpdate()
            .set(RebuildRun::getStatus, status.getCode())
            .set(RebuildRun::getProjectedCount, projectedCount)
            .set(RebuildRun::getErrorCode, errorCode)
            .set(RebuildRun::getFinishedAt, finishedAt)
            .set(RebuildRun::getUpdateId, updateId)
            .set(RebuildRun::getUpdateTime, finishedAt)
            .setIncrBy(RebuildRun::getVersion, 1)
            .eq(RebuildRun::getId, id)
            .in(RebuildRun::getStatus, status == SearchRebuildStatus.CANCELLED
                ? new String[] {SearchRebuildStatus.QUEUED.getCode(), SearchRebuildStatus.FAILED.getCode(),
                    SearchRebuildStatus.RUNNING.getCode()}
                : new String[] {SearchRebuildStatus.RUNNING.getCode()}));
  }
}
