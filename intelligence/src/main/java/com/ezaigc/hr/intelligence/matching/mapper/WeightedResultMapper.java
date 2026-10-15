package com.ezaigc.hr.intelligence.matching.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.intelligence.matching.domain.entity.CandidateMatchingStateRow;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightedResult;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunStatus;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WeightedResultMapper extends BaseMapper<WeightedResult> {
  @SpecialSql("按确定岗位版本反查成功评分候选人事实，游标有界；共享授权由调用方实时回查人才模块")
  @Select({"""
      <script>
      SELECT DISTINCT w.candidate_id
      FROM matching_weighted_result w JOIN matching_match_run r ON r.id=w.match_run_id
      """,
      "WHERE r.jd_version_id=#{jdVersionId} AND r.status IN ('" + MatchRunStatus.Codes.SUCCEEDED
          + "','" + MatchRunStatus.Codes.PARTIAL + "')",
      """
        AND w.stable_rank IS NOT NULL
      <if test="afterId != null">AND w.candidate_id &gt; #{afterId}</if>
      ORDER BY w.candidate_id LIMIT #{limit}
      </script>
      """})
  List<String> candidateIdsForJdVersion(String jdVersionId, String afterId, int limit);

  @SpecialSql("按候选人和岗位读取最近成功筛选；省略归属人仅供应用层已实时核验的共享候选人集合")
  @Select({
    """
    <script>
    SELECT candidate_id,resume_version_id,jd_version_id,match_run_id FROM (
      SELECT w.candidate_id,w.resume_version_id,r.jd_version_id,r.id AS match_run_id,
        ROW_NUMBER() OVER(PARTITION BY w.candidate_id,w.resume_version_id,r.jd_version_id ORDER BY r.create_time DESC,r.id DESC) AS rn
      FROM matching_weighted_result w JOIN matching_match_run r ON r.id=w.match_run_id
    """,
    "WHERE <if test='ownerId != null'>r.owner_user_id=#{ownerId} AND </if> r.status IN ('"
        + MatchRunStatus.Codes.SUCCEEDED
        + "','"
        + MatchRunStatus.Codes.PARTIAL
        + "') AND w.candidate_id IN",
    """
      <foreach collection="candidateIds" item="id" open="(" separator="," close=")">#{id}</foreach>
    ) completed WHERE rn=1
    </script>
    """
  })
  List<CandidateMatchingStateRow> findCandidateMatchingStates(
      @Param("candidateIds") List<String> candidateIds, @Param("ownerId") String ownerId);

  @SpecialSql("稳定排名必须按运行范围在单条 CASE 语句中原子批量更新")
  @Update({
    "<script>",
    "UPDATE matching_weighted_result",
    "SET stable_rank = CASE id",
    "<foreach collection='rankedResults' item='result'>",
    "WHEN #{result.id} THEN #{result.stableRank}",
    "</foreach>",
    "END,",
    "update_id = #{updateId},",
    "update_time = #{now},",
    "version = version + 1",
    "WHERE match_run_id = #{runId}",
    "AND id IN",
    "<foreach collection='rankedResults' item='result' open='(' separator=',' close=')'>",
    "#{result.id}",
    "</foreach>",
    "</script>"
  })
  int updateRanks(
      @Param("runId") String runId,
      @Param("rankedResults") List<WeightedResult> rankedResults,
      @Param("updateId") String updateId,
      @Param("now") Instant now);
}
