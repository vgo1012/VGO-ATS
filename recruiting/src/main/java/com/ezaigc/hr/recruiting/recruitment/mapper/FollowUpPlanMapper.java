package com.ezaigc.hr.recruiting.recruitment.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.FollowUpPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import java.time.Instant;
import java.util.List;
@Mapper
public interface FollowUpPlanMapper extends BaseMapper<FollowUpPlan> {
  @SpecialSql("预约日历按固定 JD 客户、招聘类型与重叠时间筛选，使用稳定游标")
  @Select("""
      SELECT p.id,p.application_id,p.starts_at,p.ends_at,p.channel,p.note,p.status,p.result_reason,p.version,
             p.create_id,p.create_name,p.create_time,p.update_id,p.update_name,p.update_time,p.ext,p.remark
        FROM recruitment_follow_up_plan p JOIN recruitment_application a ON a.id=p.application_id
        JOIN demand_jd_version jv ON jv.id=a.jd_version_id JOIN demand_jd j ON j.id=a.jd_id
       WHERE (#{to} IS NULL OR p.starts_at < #{to}) AND (#{from} IS NULL OR p.ends_at > #{from})
         AND (#{status} IS NULL OR p.status=#{status})
         AND (#{applicationId} IS NULL OR p.application_id=#{applicationId})
         AND (#{jdId} IS NULL OR a.jd_id=#{jdId})
         AND (#{jdVersionId} IS NULL OR a.jd_version_id=#{jdVersionId})
         AND (#{recruitmentType} IS NULL OR a.recruitment_type=#{recruitmentType})
         AND (#{clientId} IS NULL OR (CASE WHEN jv.recruitment_type IS NULL THEN j.client_id ELSE jv.client_id END)=#{clientId})
         AND (#{cursorTime} IS NULL OR p.starts_at < #{cursorTime} OR (p.starts_at=#{cursorTime} AND p.id < #{cursorId}))
       ORDER BY p.starts_at DESC,p.id DESC LIMIT #{limit}
      """)
  List<FollowUpPlan> findRange(Instant from, Instant to, String applicationId, String jdId, String jdVersionId, String recruitmentType, String clientId, String status, Instant cursorTime, String cursorId, int limit);
}
