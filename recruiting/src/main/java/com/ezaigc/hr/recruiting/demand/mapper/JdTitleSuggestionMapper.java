package com.ezaigc.hr.recruiting.demand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.recruiting.demand.domain.dto.JdTitleSuggestionOptionDTO;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdTitleSuggestionEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface JdTitleSuggestionMapper extends BaseMapper<JdTitleSuggestionEntity> {
  @SpecialSql("同名提案全局唯一；重复保存 JD 不生成第二条待审核记录")
  @Insert("""
      INSERT INTO demand_jd_title_suggestion (id, name, status, submitted_by_user_id, create_id, update_id, create_time, update_time)
      VALUES (#{id}, #{name}, #{pendingStatus}, #{actorId}, #{actorId}, #{actorId}, #{now}, #{now})
      ON DUPLICATE KEY UPDATE id = id
      """)
  int insertProposal(@Param("id") String id, @Param("name") String name,
      @Param("actorId") String actorId, @Param("now") Instant now,
      @Param("pendingStatus") String pendingStatus);

  @SpecialSql("仅公开已审核名称和本人仍在使用的 JD 名称；其他人的提案与 JD 不可见")
  @Select("""
      SELECT name, 'SHARED' AS scope, NULL AS review_status
      FROM demand_jd_title_suggestion
      WHERE status = #{approvedStatus} AND (#{query} IS NULL OR name LIKE CONCAT('%', #{query}, '%'))
      UNION ALL
      SELECT own.name, 'MINE' AS scope,
             CASE WHEN proposal.submitted_by_user_id = #{actorId} THEN proposal.status ELSE NULL END AS review_status
      FROM (SELECT DISTINCT title AS name FROM demand_jd
            WHERE owner_user_id = #{actorId} AND deleted = FALSE AND status = #{activeJdStatus}
              AND (#{query} IS NULL OR title LIKE CONCAT('%', #{query}, '%'))) own
      LEFT JOIN demand_jd_title_suggestion proposal ON proposal.name = own.name
      WHERE proposal.id IS NULL OR proposal.status <> #{approvedStatus}
      ORDER BY scope DESC, name LIMIT 100
      """)
  List<JdTitleSuggestionOptionDTO> listOptions(@Param("actorId") String actorId,
      @Param("query") String query, @Param("approvedStatus") String approvedStatus,
      @Param("activeJdStatus") String activeJdStatus);

  @SpecialSql("管理员审核队列按创建时间排序，最多返回一百条")
  @Select("""
      SELECT id, name, status, submitted_by_user_id, reviewed_by_user_id, reviewed_at, rejection_reason,
             version, ext, remark, create_id, create_name, create_time, update_id, update_name, update_time
      FROM demand_jd_title_suggestion WHERE status = #{status}
      ORDER BY create_time, id LIMIT 100
      """)
  List<JdTitleSuggestionEntity> listReviews(String status);

  @SpecialSql("审核只能从待审核态且匹配技术版本推进一次，阻止并发覆盖")
  @Update("""
      UPDATE demand_jd_title_suggestion
      SET status = #{status}, reviewed_by_user_id = #{actorId}, reviewed_at = #{now},
          rejection_reason = #{reason}, update_id = #{actorId}, update_time = #{now}, version = version + 1
      WHERE id = #{id} AND status = #{pendingStatus} AND version = #{version}
      """)
  int review(@Param("id") String id, @Param("status") String status,
      @Param("reason") String reason, @Param("version") long version,
      @Param("actorId") String actorId, @Param("now") Instant now,
      @Param("pendingStatus") String pendingStatus);
}
