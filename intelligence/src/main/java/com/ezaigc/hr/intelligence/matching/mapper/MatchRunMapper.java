package com.ezaigc.hr.intelligence.matching.mapper;

import java.util.List;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobResultSummaryRow;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobRow;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobResultRow;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobBasisRow;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchJobQuery;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRun;
import com.ezaigc.hr.intelligence.matching.constant.MatchingConstants;
import com.ezaigc.hr.intelligence.matching.constant.AiScoringConstants;
import java.math.BigDecimal;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDate;

@Mapper
public interface MatchRunMapper extends BaseMapper<MatchRun>, MatchRunSqlOperations {
  String SCOPED = """
      WITH pending AS (
        SELECT a.id,a.candidate_id,a.resume_version_id,a.jd_version_id,a.default_match_run_id,a.version,a.update_time,
          candidate.owner_user_id AS candidate_owner_user_id
        FROM matching_screening_admission a JOIN talent_candidate candidate ON candidate.id=a.candidate_id
        WHERE a.returned_at IS NULL AND NOT EXISTS (
          SELECT 1 FROM recruitment_application application WHERE application.resume_version_id=a.resume_version_id)
      ), visible_runs AS (
        SELECT r.*,CASE
          <if test="companyScope">WHEN TRUE THEN 1</if>
          <if test="!companyScope">WHEN (r.owner_user_id=#{actorId}
            <if test="!managedUserIds.isEmpty()">OR r.owner_user_id IN
              <foreach collection="managedUserIds" item="user" open="(" separator="," close=")">#{user}</foreach>
            </if>
            <if test="!departmentIds.isEmpty()">OR r.owner_department_id IN
              <foreach collection="departmentIds" item="department" open="(" separator="," close=")">#{department}</foreach>
            </if>) THEN 1</if>
          ELSE 0 END AS full_access
        FROM matching_match_run r
      ), scoped AS (
        SELECT r.id,r.jd_version_id,r.jd_fact_snapshot_id,r.plan_version_id,r.criterion_plan_version_id,
          r.weight_profile_version_id,r.selected_criterion_ids_json,r.sort_mode,r.selection_snapshot_id,
          r.status,r.total_count,r.succeeded_count,r.failed_count,r.create_time,r.update_time,r.version,r.scoring_version,
          r.full_access
        FROM visible_runs r
        WHERE 1=1
        AND ((r.run_purpose='COMPANY_DEFAULT' AND EXISTS (
          SELECT 1 FROM pending admission
          WHERE admission.default_match_run_id=r.id))
        OR (r.run_purpose='CUSTOM' AND EXISTS (
          SELECT 1 FROM matching_candidate_selection_item item
          WHERE item.selection_snapshot_id=r.selection_snapshot_id AND EXISTS (
            SELECT 1 FROM pending admission
            WHERE admission.candidate_id=item.candidate_id
              AND admission.resume_version_id=item.resume_version_id
              AND admission.jd_version_id=r.jd_version_id))))
        <if test="jdVersionId != null">AND r.jd_version_id=#{jdVersionId}</if>
        AND (r.full_access=1 OR EXISTS (SELECT 1 FROM pending admission
          WHERE admission.candidate_owner_user_id=#{actorId}
            AND admission.jd_version_id=r.jd_version_id
            AND ((r.run_purpose='COMPANY_DEFAULT' AND admission.default_match_run_id=r.id)
              OR (r.run_purpose='CUSTOM' AND EXISTS (
                SELECT 1 FROM matching_candidate_selection_item item
                WHERE item.selection_snapshot_id=r.selection_snapshot_id
                  AND item.candidate_id=admission.candidate_id
                  AND item.resume_version_id=admission.resume_version_id)))))
      )
      """;

  // JSON 窗口对条件集合排序，无 GROUP_CONCAT 长度截断；偏好 ID 不参与可比性。
  // CTE 列传入 JSON_TABLE 时显式保留 JSON 类型，规避 MySQL 8.0 的参数解析错误 1210。
  String BASIS = SCOPED + """
      , selected_values AS (
        SELECT r.id,JSON_ARRAYAGG(c.criterion_id) OVER (
          PARTITION BY r.id ORDER BY c.criterion_id
          ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS selected_json,
          ROW_NUMBER() OVER (PARTITION BY r.id ORDER BY c.criterion_id) AS position_no
        FROM scoped r LEFT JOIN JSON_TABLE(CAST(r.selected_criterion_ids_json AS JSON),
          '$[*]' COLUMNS(criterion_id VARCHAR(26) PATH '$')) c ON TRUE
      ), personal_values AS (
        SELECT r.id,JSON_ARRAYAGG(p.criterion_json) OVER (
          PARTITION BY r.id ORDER BY CAST(p.criterion_json AS CHAR)
          ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS personal_json,
          ROW_NUMBER() OVER (PARTITION BY r.id ORDER BY CAST(p.criterion_json AS CHAR)) AS position_no
        FROM scoped r JOIN matching_weight_profile_version w ON w.id=r.weight_profile_version_id
        LEFT JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(w.screening_settings_json,'$.criteria'),JSON_ARRAY()),
          '$[*]' COLUMNS(criterion_json JSON PATH '$')) p ON TRUE
      ), basis AS (
        SELECT r.id,r.jd_version_id,r.plan_version_id,r.weight_profile_version_id,r.sort_mode,
          r.selection_snapshot_id,r.status,r.create_time,r.update_time,r.version,r.full_access,
          SHA2(CAST(JSON_ARRAY(r.jd_version_id,r.jd_fact_snapshot_id,r.plan_version_id,
            r.criterion_plan_version_id,w.skills_weight,w.role_weight,w.years_weight,
            w.responsibility_weight,w.industry_project_weight,
            s.selected_json,p.personal_json,r.sort_mode,r.scoring_version) AS CHAR),256) AS basis_key
        FROM scoped r JOIN matching_weight_profile_version w ON w.id=r.weight_profile_version_id
        JOIN selected_values s ON s.id=r.id AND s.position_no=1
        JOIN personal_values p ON p.id=r.id AND p.position_no=1
      )
      """;

  @SpecialSql("跨运行岗位汇总先授权，分别计算人数和次数，避免联表放大计数")
  @Select("<script>" + SCOPED + """
      , scoped_counts AS (
        SELECT r.*,
          CASE WHEN r.full_access=1 THEN r.total_count ELSE
            (SELECT COUNT(DISTINCT i.candidate_id) FROM matching_candidate_selection_item i
              JOIN pending a ON a.candidate_id=i.candidate_id AND a.resume_version_id=i.resume_version_id
                AND a.jd_version_id=r.jd_version_id
              WHERE i.selection_snapshot_id=r.selection_snapshot_id AND a.candidate_owner_user_id=#{actorId})
          END AS visible_total_count,
          CASE WHEN r.full_access=1 THEN r.succeeded_count ELSE
            (SELECT COUNT(DISTINCT w.candidate_id) FROM matching_weighted_result w
              JOIN pending a ON a.candidate_id=w.candidate_id AND a.resume_version_id=w.resume_version_id
                AND a.jd_version_id=r.jd_version_id
              WHERE w.match_run_id=r.id AND w.stable_rank IS NOT NULL
                AND a.candidate_owner_user_id=#{actorId})
          END AS visible_succeeded_count
        FROM scoped r
      ), ranked_runs AS (
        SELECT id,jd_version_id,ROW_NUMBER() OVER(PARTITION BY jd_version_id ORDER BY create_time DESC,id DESC) AS position_no
        FROM scoped_counts
      ), summaries AS (
        SELECT jd_version_id,MAX(create_time) AS last_run_at,COUNT(*) AS run_count,
          SUM(visible_total_count) AS evaluation_count,SUM(visible_succeeded_count) AS succeeded_count,
          SUM(CASE WHEN full_access=1 THEN failed_count
            ELSE GREATEST(visible_total_count-visible_succeeded_count,0) END) AS failed_count,
          SUM(CASE WHEN status IN (#{queuedStatus},#{runningStatus}) THEN 1 ELSE 0 END) AS active_run_count
        FROM scoped_counts GROUP BY jd_version_id
      ), page_jobs AS (
        SELECT s.jd_version_id,r.id AS latest_run_id,s.last_run_at,s.run_count,s.evaluation_count,
          s.succeeded_count,s.failed_count,s.active_run_count
        FROM summaries s JOIN ranked_runs r ON r.jd_version_id=s.jd_version_id AND r.position_no=1
        <if test="beforeTime != null">
          WHERE (s.last_run_at &lt; #{beforeTime} OR (s.last_run_at=#{beforeTime} AND s.jd_version_id &lt; #{beforeId}))
        </if>
        ORDER BY s.last_run_at DESC,s.jd_version_id DESC LIMIT #{limit}
      )
      SELECT j.jd_version_id,j.latest_run_id,j.last_run_at,j.run_count,j.evaluation_count,
        j.succeeded_count,j.failed_count,j.active_run_count,
        (SELECT COUNT(DISTINCT i.candidate_id) FROM scoped r
          JOIN matching_candidate_selection_item i ON i.selection_snapshot_id=r.selection_snapshot_id
          JOIN pending a ON a.candidate_id=i.candidate_id AND a.resume_version_id=i.resume_version_id AND a.jd_version_id=r.jd_version_id
          WHERE r.jd_version_id=j.jd_version_id
            AND (r.full_access=1 OR a.candidate_owner_user_id=#{actorId})) AS candidate_count,
        (SELECT COUNT(DISTINCT w.candidate_id) FROM scoped r
          JOIN matching_weighted_result w ON w.match_run_id=r.id
          JOIN pending a ON a.candidate_id=w.candidate_id AND a.resume_version_id=w.resume_version_id AND a.jd_version_id=r.jd_version_id
          WHERE r.jd_version_id=j.jd_version_id AND r.status IN (#{succeededStatus},#{partialStatus})
            AND w.stable_rank IS NOT NULL
            AND (r.full_access=1 OR a.candidate_owner_user_id=#{actorId})) AS scored_candidate_count
      FROM page_jobs j ORDER BY j.last_run_at DESC,j.jd_version_id DESC
      </script>
      """)
  List<MatchJobRow> findJobs(MatchJobQuery query);

  @SpecialSql("冻结规则分组忽略同内容偏好/权重版本ID，条件数组规范排序，保留规则和事实版本隔离")
  @Select("<script>" + BASIS + """
      , ranked_basis AS (
        SELECT id,jd_version_id,basis_key,plan_version_id,weight_profile_version_id,sort_mode,create_time,
          COUNT(*) OVER(PARTITION BY basis_key) AS run_count,
          ROW_NUMBER() OVER(PARTITION BY basis_key ORDER BY create_time DESC,id DESC) AS position_no
        FROM basis
      ), page_basis AS (
        SELECT id,jd_version_id,basis_key,plan_version_id,weight_profile_version_id,sort_mode,create_time,run_count
        FROM ranked_basis WHERE position_no=1
        <if test="beforeTime != null">
          AND (create_time &lt; #{beforeTime} OR (create_time=#{beforeTime} AND id &lt; #{beforeId}))
        </if>
        ORDER BY create_time DESC,id DESC LIMIT #{limit}
      )
      SELECT p.id AS basis_run_id,p.jd_version_id,p.basis_key,p.plan_version_id,p.weight_profile_version_id,
        p.sort_mode,p.create_time AS last_run_at,p.run_count,
        wv.skills_weight,wv.role_weight,wv.years_weight,wv.responsibility_weight,wv.industry_project_weight,
        sr.selected_criterion_ids_json,wv.screening_settings_json AS personal_screening_settings_json,
        (SELECT COUNT(DISTINCT i.candidate_id) FROM basis b
          JOIN matching_candidate_selection_item i ON i.selection_snapshot_id=b.selection_snapshot_id
          JOIN pending a ON a.candidate_id=i.candidate_id AND a.resume_version_id=i.resume_version_id AND a.jd_version_id=b.jd_version_id
          WHERE b.basis_key=p.basis_key
            AND (b.full_access=1 OR a.candidate_owner_user_id=#{actorId})) AS candidate_count,
        (SELECT COUNT(DISTINCT w.candidate_id) FROM basis b
          JOIN matching_weighted_result w ON w.match_run_id=b.id
          JOIN pending a ON a.candidate_id=w.candidate_id AND a.resume_version_id=w.resume_version_id AND a.jd_version_id=b.jd_version_id
          WHERE b.basis_key=p.basis_key AND b.status IN (#{succeededStatus},#{partialStatus})
            AND w.stable_rank IS NOT NULL
            AND (b.full_access=1 OR a.candidate_owner_user_id=#{actorId})) AS scored_candidate_count
      FROM page_basis p JOIN scoped sr ON sr.id=p.id
        JOIN matching_weight_profile_version wv ON wv.id=p.weight_profile_version_id
      ORDER BY p.create_time DESC,p.id DESC
      </script>
      """)
  List<MatchJobBasisRow> findJobBases(MatchJobQuery query);

  String LATEST_RESULTS = BASIS + """
      , latest AS (
        SELECT w.id,w.match_run_id,w.base_evaluation_id,w.candidate_id,w.resume_version_id,
          w.professional_score,w.bonus_score,w.reference_score,w.must_tier,w.stable_rank,w.result_json,
          w.create_time,w.update_time,b.sort_mode,
          ROW_NUMBER() OVER(PARTITION BY w.candidate_id ORDER BY b.create_time DESC,b.id DESC,w.id DESC) AS candidate_position
        FROM basis b JOIN matching_weighted_result w ON w.match_run_id=b.id
        JOIN pending a ON a.candidate_id=w.candidate_id AND a.resume_version_id=w.resume_version_id AND a.jd_version_id=b.jd_version_id
        WHERE b.basis_key=(SELECT basis_key FROM basis WHERE id=#{basisRunId})
          AND b.status IN (#{succeededStatus},#{partialStatus}) AND w.stable_rank IS NOT NULL
          AND (b.full_access=1 OR a.candidate_owner_user_id=#{actorId})

      )
      """;

  @SpecialSql("可见同依据终态运行先按候选人选最新成功结果，再按MUST和得分排序并保留原证据来源")
  @Select("<script>" + LATEST_RESULTS + """
      , ranked AS (
        SELECT id,match_run_id,base_evaluation_id,candidate_id,resume_version_id,professional_score,
          bonus_score,reference_score,must_tier,stable_rank,result_json,create_time,update_time,
          ROW_NUMBER() OVER(ORDER BY CASE must_tier WHEN #{passTier} THEN 3 WHEN #{unknownTier} THEN 2 ELSE 1 END DESC,
            CASE sort_mode WHEN #{professionalSort} THEN professional_score
              WHEN #{bonusSort} THEN bonus_score ELSE reference_score END DESC,candidate_id) AS workspace_rank
        FROM latest WHERE candidate_position=1
      )
      SELECT id,match_run_id,base_evaluation_id,candidate_id,resume_version_id,professional_score,
        bonus_score,reference_score,must_tier,stable_rank,result_json,create_time,update_time,workspace_rank
      FROM ranked WHERE workspace_rank &gt; #{afterRank} ORDER BY workspace_rank LIMIT #{limit}
      </script>
      """)
  List<MatchJobResultRow> findJobResults(MatchJobQuery query);

  @SpecialSql("与结果页共用授权及候选人去重集合，五维完整性优先于分数，不受分页影响")
  @Select("<script>" + LATEST_RESULTS + """
      , classified AS (
        SELECT CASE WHEN (
          SELECT COUNT(DISTINCT dimension.code)
          FROM JSON_TABLE(COALESCE(JSON_EXTRACT(latest.result_json,'$.dimensions'),JSON_ARRAY()),
            '$[*]' COLUMNS(code VARCHAR(64) PATH '$.code', enabled BOOLEAN PATH '$.enabled',
              status VARCHAR(32) PATH '$.status')) dimension
          WHERE dimension.enabled=TRUE AND dimension.status=#{knownDimensionStatus}
            AND dimension.code IN ('CORE_SKILLS','ROLE_EXPERIENCE','RELATED_YEARS',
              'RESPONSIBILITY_MATCH','INDUSTRY_AND_PROJECTS')
        ) != 5 THEN 'missing'
          WHEN reference_score &gt;= 85 THEN 'strong'
          WHEN reference_score &gt;= 70 THEN 'consider' ELSE 'reject' END AS grade
        FROM latest WHERE candidate_position=1
      )
      SELECT COUNT(*) AS total,
        COALESCE(SUM(grade='strong'),0) AS strong,COALESCE(SUM(grade='consider'),0) AS consider,
        COALESCE(SUM(grade='reject'),0) AS reject,COALESCE(SUM(grade='missing'),0) AS missing
      FROM classified
      </script>
      """)
  MatchJobResultSummaryRow findJobResultsSummary(MatchJobQuery query);

  @SpecialSql("校验代表运行仍在当前演员的岗位台账中，归属人仅能借本人有效入库项读取")
  @Select("<script>" + SCOPED + " SELECT COUNT(*) FROM scoped WHERE id=#{basisRunId} </script>")
  int countVisibleJobBasis(MatchJobQuery query);

  @SpecialSql("只返回仍由本人归属且未退回、未进入招聘历史的运行结果候选人")
  @Select("""
      SELECT DISTINCT w.candidate_id FROM matching_weighted_result w
      JOIN matching_match_run r ON r.id=w.match_run_id
      JOIN matching_screening_admission a ON a.candidate_id=w.candidate_id
        AND a.resume_version_id=w.resume_version_id AND a.jd_version_id=r.jd_version_id
      JOIN talent_candidate c ON c.id=w.candidate_id
      WHERE w.match_run_id=#{runId} AND c.owner_user_id=#{actorId} AND a.returned_at IS NULL
        AND NOT EXISTS (SELECT 1 FROM recruitment_application application
          WHERE application.resume_version_id=a.resume_version_id)
      """)
  List<String> findOwnedPendingResultCandidateIds(@Param("runId") String runId, @Param("actorId") String actorId);

  @SpecialSql("游标绑定当前授权数据修订，运行状态或结果变化时要求刷新，避免重排遗漏或重复")
  @Select("<script>" + SCOPED + """
      SELECT SHA2(CAST(JSON_ARRAY(COUNT(*),MAX(r.update_time),COALESCE(SUM(r.version),0),
        (SELECT COUNT(*) FROM matching_weighted_result w JOIN scoped s ON s.id=w.match_run_id),
        (SELECT MAX(w.update_time) FROM matching_weighted_result w JOIN scoped s ON s.id=w.match_run_id),
        (SELECT COUNT(*) FROM pending a WHERE a.jd_version_id IN (SELECT jd_version_id FROM scoped)),
        (SELECT COALESCE(SUM(a.version),0) FROM pending a WHERE a.jd_version_id IN (SELECT jd_version_id FROM scoped)),
        (SELECT MAX(a.update_time) FROM pending a WHERE a.jd_version_id IN (SELECT jd_version_id FROM scoped))) AS CHAR),256)
      FROM scoped r
      </script>
      """)
  String findJobRevision(MatchJobQuery query);

  /** 在共享任务已锁定后，锁定精确绑定的匹配运行。 */
  @SpecialSql("共享任务终态回调需要锁定绑定记录并读取最新业务状态")
  @Select("""
      SELECT id,task_id,jd_version_id,jd_fact_snapshot_id,plan_version_id,criterion_plan_version_id,
             weight_profile_version_id,personal_preference_version_id,run_purpose,selection_snapshot_id,
             selected_criterion_ids_json,sort_mode,status,total_count,succeeded_count,failed_count,
             owner_user_id,owner_department_id,error_code,version,started_at,finished_at,scoring_version,
             ext,remark,create_id,create_name,create_time,update_id,update_name,update_time
      FROM matching_match_run WHERE id=#{id} AND task_id=#{taskId} FOR UPDATE
      """)
  MatchRun findBoundRunForUpdate(String id, String taskId);

  /** 更新指定业务记录。 */
  @SpecialSql("调用方锁定当前任务租约后接管旧运行，原子保留首次 started_at 并清理上次失败信息")
  @Update(
      "UPDATE matching_match_run SET"
          + " status=#{runningStatus},started_at=COALESCE(started_at,#{now}),finished_at=NULL,error_code=NULL,version=version+1,update_id='"
          + PlatformActorConstants.SYSTEM_ACTOR_ID
          + "',update_time=#{now}"
          + " WHERE id=#{id} AND status IN (#{queuedStatus},#{runningStatus},#{failedStatus},#{partialStatus})")
  /** 启动运行。 */
  int startRun(
      String id,
      Instant now,
      String runningStatus,
      String queuedStatus,
      String failedStatus,
      String partialStatus);

  @SpecialSql("单项运行结果需要按运行和候选人唯一键原子 upsert 以支持重试")
  @Insert(
      "INSERT INTO"
          + " matching_match_run_item_outcome(match_run_id,candidate_id,resume_version_id,status,error_code,last_attempt_no,create_id,create_time,update_id,update_time)"
          + " VALUES(#{runId},#{candidateId},#{resumeVersionId},#{status},#{errorCode},#{attempt},'"
          + PlatformActorConstants.SYSTEM_ACTOR_ID
          + "',#{now},'"
          + PlatformActorConstants.SYSTEM_ACTOR_ID
          + "',#{now}) ON DUPLICATE KEY UPDATE"
          + " resume_version_id=VALUES(resume_version_id),status=VALUES(status),error_code=VALUES(error_code),last_attempt_no=VALUES(last_attempt_no),update_id=VALUES(update_id),update_time=VALUES(update_time)")
  int upsertItemOutcome(
      String runId,
      String candidateId,
      String resumeVersionId,
      String status,
      String errorCode,
      int attempt,
      Instant now);

  @SpecialSql("按基础评估反查最近一次运行需要联查加权结果")
  @Select(
      "SELECT"
          + " r.id,r.task_id,r.scoring_version,r.jd_version_id,r.jd_fact_snapshot_id,r.plan_version_id,r.criterion_plan_version_id,r.weight_profile_version_id,r.personal_preference_version_id,r.run_purpose,r.selection_snapshot_id,r.selected_criterion_ids_json,r.sort_mode,r.status,r.total_count,r.succeeded_count,r.failed_count,r.owner_user_id,r.owner_department_id,r.error_code,r.version,r.started_at,r.finished_at,r.ext,r.remark,r.create_id,r.create_name,r.create_time,r.update_id,r.update_name,r.update_time"
          + " FROM matching_match_run r JOIN matching_weighted_result w ON w.match_run_id=r.id"
          + " WHERE w.base_evaluation_id=#{baseId} ORDER BY r.create_time DESC LIMIT 1")
  MatchRun findRunByBase(String baseId);

  @SpecialSql("基础评估包含维度和条件 JSON，写入时必须显式 CAST 并保留算法快照")
  @Insert(
      "INSERT INTO"
          + " matching_base_evaluation(id,cache_key,jd_fact_snapshot_id,resume_fact_snapshot_id,plan_version_id,weight_profile_version_id,selected_criterion_hash,evaluated_on,professional_score,bonus_score,reference_score,must_tier,selected_bonus_cap,dimensions_json,criteria_json,algorithm_version,create_time)"
          + " VALUES(#{id},#{cacheKey},#{jdFactId},#{resumeFactId},#{planVersionId},#{weightVersionId},#{criterionHash},#{evaluatedOn},#{professional},#{bonus},#{reference},#{mustTier},#{bonusCap},CAST(#{dimensions}"
          + " AS JSON),CAST(#{criteria} AS JSON),'" + AiScoringConstants.ALGORITHM_VERSION + "',#{now})")
  int insertBase(
      String id,
      String cacheKey,
      String jdFactId,
      String resumeFactId,
      String planVersionId,
      String weightVersionId,
      String criterionHash,
      LocalDate evaluatedOn,
      BigDecimal professional,
      int bonus,
      BigDecimal reference,
      String mustTier,
      int bonusCap,
      String dimensions,
      String criteria,
      Instant now);

  @SpecialSql("加权结果包含 JSON 快照且需按运行候选人唯一键原子 upsert")
  @Insert(
      "INSERT INTO"
          + " matching_weighted_result(id,match_run_id,base_evaluation_id,candidate_id,resume_version_id,professional_score,bonus_score,reference_score,must_tier,result_json,create_id,create_time,update_id,update_time)"
          + " VALUES(#{id},#{runId},#{baseId},#{candidateId},#{resumeVersionId},#{professional},#{bonus},#{reference},#{mustTier},CAST(#{resultJson}"
          + " AS JSON),'"
          + PlatformActorConstants.SYSTEM_ACTOR_ID
          + "',#{now},'"
          + PlatformActorConstants.SYSTEM_ACTOR_ID
          + "',#{now}) ON DUPLICATE KEY UPDATE"
          + " base_evaluation_id=VALUES(base_evaluation_id),professional_score=VALUES(professional_score),bonus_score=VALUES(bonus_score),reference_score=VALUES(reference_score),must_tier=VALUES(must_tier),result_json=VALUES(result_json),update_id=VALUES(update_id),update_time=VALUES(update_time)")
  int upsertResult(
      String id,
      String runId,
      String baseId,
      String candidateId,
      String resumeVersionId,
      BigDecimal professional,
      int bonus,
      BigDecimal reference,
      String mustTier,
      String resultJson,
      Instant now);
}
