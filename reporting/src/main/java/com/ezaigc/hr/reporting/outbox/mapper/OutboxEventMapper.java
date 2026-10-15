package com.ezaigc.hr.reporting.outbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.reporting.outbox.domain.entity.OutboxEventEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEventEntity> {
  @SpecialSql("人工恢复发布任务通过不可变恢复记录引用原信封，保持消息ID和原工作绑定")
  @Select("SELECT e.id,e.work_task_id,e.work_task_type,e.event_type,e.event_version,e.aggregate_type,e.aggregate_id,"
      + "e.occurred_at,e.trace_id,e.actor_user_id,e.owner_user_id,e.owner_department_id,e.ext,e.remark,"
      + "e.create_id,e.create_name,e.create_time,e.update_id,e.update_name,e.update_time,e.version"
      + " FROM reporting_outbox_event e JOIN reporting_search_task_recovery r ON r.outbox_event_id=e.id"
      + " WHERE r.id=#{recoveryTaskId}")
  OutboxEventEntity findForRecoveryTask(String recoveryTaskId);
  @SpecialSql("Outbox 按发布任务和业务任务双唯一键幂等保存，随后以当前读校验实际信封")
  @Insert("INSERT IGNORE INTO reporting_outbox_event(id,work_task_id,work_task_type,event_type,event_version,"
      + "aggregate_type,aggregate_id,occurred_at,trace_id,actor_user_id,owner_user_id,owner_department_id,"
      + "create_id,create_time,update_id,update_time,version) VALUES(#{id},#{workTaskId},#{workTaskType},#{eventType},"
      + "#{eventVersion},#{aggregateType},#{aggregateId},#{occurredAt},#{traceId},#{actorUserId},#{ownerUserId},"
      + "#{ownerDepartmentId},#{createId},#{createTime},#{updateId},#{updateTime},#{version})")
  int insertIgnore(OutboxEventEntity event);

  @SpecialSql("重复业务请求使用 Outbox 当前读，避免事务快照漏掉竞争者已经提交的唯一信封")
  @Select("SELECT id,work_task_id,work_task_type,event_type,event_version,aggregate_type,aggregate_id,occurred_at,"
      + "trace_id,actor_user_id,owner_user_id,owner_department_id,ext,remark,create_id,create_name,create_time,"
      + "update_id,update_name,update_time,version FROM reporting_outbox_event WHERE id=#{id} FOR UPDATE")
  OutboxEventEntity findForUpdate(String id);
}
