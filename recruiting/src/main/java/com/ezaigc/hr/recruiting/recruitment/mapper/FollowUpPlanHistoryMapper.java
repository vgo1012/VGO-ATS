package com.ezaigc.hr.recruiting.recruitment.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.FollowUpPlanHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import org.apache.ibatis.annotations.Param;
import java.time.Instant;
import java.util.List;
@Mapper
public interface FollowUpPlanHistoryMapper extends BaseMapper<FollowUpPlanHistory> {
  @SpecialSql("按活动读取预约审计历史，使用时间及 ID 稳定分页")
  @Select("""
      <script>
      SELECT h.id, h.plan_id, h.action, h.before_snapshot, h.after_snapshot, h.reason,
             h.version, h.create_id, h.create_name, h.create_time, h.update_id, h.update_name, h.update_time, h.ext, h.remark
      FROM recruitment_follow_up_plan_history h
      JOIN recruitment_follow_up_plan p ON p.id = h.plan_id
      WHERE p.application_id = #{applicationId}
      <if test="cursorTime != null">
        AND (h.create_time &lt; #{cursorTime} OR (h.create_time = #{cursorTime} AND h.id &lt; #{cursorId}))
      </if>
      ORDER BY h.create_time DESC, h.id DESC LIMIT #{limit}
      </script>
      """)
  List<FollowUpPlanHistory> findHistory(@Param("applicationId") String applicationId,
      @Param("cursorTime") Instant cursorTime, @Param("cursorId") String cursorId, @Param("limit") int limit);
}
