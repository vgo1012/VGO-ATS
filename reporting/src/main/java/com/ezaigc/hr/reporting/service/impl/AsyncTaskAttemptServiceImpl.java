package com.ezaigc.hr.reporting.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.reporting.domain.entity.AsyncTaskAttemptEntity;
import com.ezaigc.hr.reporting.mapper.AsyncTaskAttemptMapper;
import com.ezaigc.hr.reporting.service.AsyncTaskAttemptService;
import java.util.List;
import org.springframework.stereotype.Service;
import java.time.Instant;

/** 异步任务尝试业务服务实现。 */
@Service
public class AsyncTaskAttemptServiceImpl
    extends ServiceImpl<AsyncTaskAttemptMapper, AsyncTaskAttemptEntity>
    implements AsyncTaskAttemptService {

  /** 新增尝试。 */
  @Override
  public int insertAttempt(String attemptId, String taskId, Instant now) {
    return baseMapper.insertAttempt(attemptId, taskId, now);
  }

  /** 标记成功。 */
  @Override
  public int markSucceeded(String taskId, int attemptNo, Instant now) {
    return baseMapper.markSucceeded(taskId, attemptNo, now);
  }

  /** 标记失败。 */
  @Override
  public int markFailed(
      String taskId, int attemptNo, String code, String detail, Instant now) {
    return baseMapper.markFailed(taskId, attemptNo, code, detail, now);
  }

  /** 标记已取消。 */
  @Override
  public int markCancelled(String taskId, int attemptNo, Instant now) {
    return baseMapper.markCancelled(taskId, attemptNo, now);
  }

  @Override
  public List<AsyncTaskAttemptEntity> findByTaskId(String taskId) {
    return list(
        Wrappers.<AsyncTaskAttemptEntity>lambdaQuery()
            .eq(AsyncTaskAttemptEntity::getTaskId, taskId)
            .orderByAsc(AsyncTaskAttemptEntity::getAttemptNo));
  }
}
