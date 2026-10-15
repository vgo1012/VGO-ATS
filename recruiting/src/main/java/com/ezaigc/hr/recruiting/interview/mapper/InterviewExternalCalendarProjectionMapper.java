package com.ezaigc.hr.recruiting.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewExternalCalendarProjection;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InterviewExternalCalendarProjectionMapper
        extends BaseMapper<InterviewExternalCalendarProjection>, InterviewExternalCalendarProjectionSqlOperations {
    @Override
    @SpecialSql("未知创建只在存在已确认回执时恢复；联表条件不能由单表 Wrapper 表达")
    @Select("""
        SELECT p.id,p.interview_id,p.provider,p.desired_type,p.active_type,p.status,p.operation_revision,
               p.title,p.description,p.admin_user_id,p.invitee_user_ids_json,p.additional_invitee_user_ids_json,
               p.start_at,p.end_at,p.timezone_id,p.location_text,p.reminder_seconds,p.waiting_room_enabled,
               p.allow_enter_before_host,p.enter_mute_enabled,p.external_object_id,p.meeting_code,p.meeting_link,
               p.remote_status,p.attempt_count,p.next_attempt_at,p.lease_token,p.lease_until,p.create_intent_id,
               p.last_error_code,p.last_error_detail,p.last_synced_at,p.ext,p.remark,p.create_id,p.create_name,
               p.create_time,p.update_id,p.update_name,p.update_time,p.version
        FROM interview_external_calendar_projection p
        WHERE (p.status IN (#{pending},#{failed}) AND p.next_attempt_at <= #{now}
               AND (p.lease_until IS NULL OR p.lease_until <= #{now}))
           OR (p.status=#{processing} AND p.lease_until <= #{now})
           OR (p.status=#{unknown} AND EXISTS (
               SELECT 1 FROM interview_external_calendar_attempt a
               WHERE a.id=p.create_intent_id AND a.projection_id=p.id AND a.operation_type=#{createOperation}
                 AND a.status IN (#{succeededAttempt},#{failedAttempt})))
        ORDER BY p.next_attempt_at,p.id LIMIT #{limit}
        """)
    List<InterviewExternalCalendarProjection> findDue(@Param("now") Instant now, @Param("limit") int limit,
            @Param("pending") String pending, @Param("failed") String failed, @Param("processing") String processing,
            @Param("unknown") String unknown, @Param("succeededAttempt") String succeededAttempt,
            @Param("failedAttempt") String failedAttempt, @Param("createOperation") String createOperation);
}
