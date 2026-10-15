package com.ezaigc.hr.reporting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.reporting.domain.entity.AsyncTaskAttemptEntity;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AsyncTaskAttemptMapper extends BaseMapper<AsyncTaskAttemptEntity> {

  @SpecialSql("从任务当前尝试号原子生成对应尝试记录")
  @Insert(
      "INSERT INTO"
          + " reporting_async_task_attempt(id,task_id,attempt_no,status,started_at,create_id,create_time,update_id,update_time)"
          + " SELECT #{attemptId},id,attempt_count,'"
          + AsyncTaskStatus.Codes.RUNNING
          + "',#{now},'"
          + PlatformActorConstants.SYSTEM_ACTOR_ID
          + "',#{now},'"
          + PlatformActorConstants.SYSTEM_ACTOR_ID
          + "',#{now} FROM reporting_async_task WHERE"
          + " id=#{taskId}")
  /** 新增尝试。 */
  int insertAttempt(String attemptId, String taskId, Instant now);

  /** 更新指定业务记录。 */
  @SpecialSql("通过尝试号栅栏原子完成运行中尝试")
  @Update(
      "UPDATE reporting_async_task_attempt SET status='"
          + AsyncTaskStatus.Codes.SUCCEEDED
          + "',finished_at=#{now},update_id='"
          + PlatformActorConstants.SYSTEM_ACTOR_ID
          + "',update_time=#{now},version=version+1 WHERE task_id=#{taskId}"
          + " AND attempt_no=#{attemptNo} AND status='"
          + AsyncTaskStatus.Codes.RUNNING
          + "'")
  /** 标记成功。 */
  int markSucceeded(String taskId, int attemptNo, Instant now);

  /** 更新指定业务记录。 */
  @SpecialSql("通过尝试号栅栏原子记录运行中尝试失败")
  @Update(
      "UPDATE reporting_async_task_attempt SET status='"
          + AsyncTaskStatus.Codes.FAILED
          + "',error_code=#{code},safe_error_detail=#{detail},finished_at=#{now},update_id='"
          + PlatformActorConstants.SYSTEM_ACTOR_ID
          + "',update_time=#{now},version=version+1"
          + " WHERE task_id=#{taskId} AND attempt_no=#{attemptNo} AND status='"
          + AsyncTaskStatus.Codes.RUNNING
          + "'")
  /** 标记失败。 */
  int markFailed(String taskId, int attemptNo, String code, String detail, Instant now);

  /** 更新指定业务记录。 */
  @SpecialSql("通过尝试号栅栏原子取消运行中尝试")
  @Update(
      "UPDATE reporting_async_task_attempt SET status='"
          + AsyncTaskStatus.Codes.CANCELLED
          + "',finished_at=#{now},update_id='"
          + PlatformActorConstants.SYSTEM_ACTOR_ID
          + "',update_time=#{now},version=version+1 WHERE task_id=#{taskId}"
          + " AND attempt_no=#{attemptNo} AND status='"
          + AsyncTaskStatus.Codes.RUNNING
          + "'")
  /** 标记已取消。 */
  int markCancelled(String taskId, int attemptNo, Instant now);
}
