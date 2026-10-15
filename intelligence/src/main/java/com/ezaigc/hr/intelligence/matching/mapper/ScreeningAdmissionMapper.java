package com.ezaigc.hr.intelligence.matching.mapper;

import com.ezaigc.hr.intelligence.matching.domain.dto.ScreeningSelectionQuery;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.intelligence.matching.constant.DefaultScoringConstants;
import com.ezaigc.hr.intelligence.matching.domain.entity.ScreeningAdmission;
import com.ezaigc.hr.intelligence.matching.domain.entity.ScreeningCandidateRow;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunPurpose;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunStatus;
import com.ezaigc.hr.talent.enums.ResumeIntakeScoreStatus;
import com.ezaigc.hr.talent.enums.ResumeIntakeStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ScreeningAdmissionMapper extends BaseMapper<ScreeningAdmission> {

  @SpecialSql("候选人合并在双方行锁之后当前读所有业务历史，禁止遗留未迁移引用")
  @Select("SELECT candidate_id FROM matching_candidate_selection_item WHERE candidate_id=#{candidateId} LIMIT 1 FOR UPDATE")
  String findMergeSelection(String candidateId);

  @SpecialSql("候选人合并在双方行锁之后当前读所有业务历史，禁止遗留未迁移引用")
  @Select("SELECT candidate_id FROM matching_match_run_item_outcome WHERE candidate_id=#{candidateId} LIMIT 1 FOR UPDATE")
  String findMergeOutcome(String candidateId);

  @SpecialSql("候选人合并在双方行锁之后当前读所有业务历史，禁止遗留未迁移引用")
  @Select("SELECT candidate_id FROM matching_weighted_result WHERE candidate_id=#{candidateId} LIMIT 1 FOR UPDATE")
  String findMergeWeightedResult(String candidateId);

  @SpecialSql("候选人合并在双方行锁之后当前读所有业务历史，禁止遗留未迁移引用")
  @Select("SELECT candidate_id FROM matching_screening_admission WHERE candidate_id=#{candidateId} LIMIT 1 FOR UPDATE")
  String findMergeAdmission(String candidateId);
  @SpecialSql("准入选择读模型按冻结公司运行授权，仅关联候选人姓名；JD、姓名、未退回及未招聘条件先过滤再分页，返回前由应用层复核简历事实权限")
  @Select("""
      <script>
      SELECT a.candidate_id,c.full_name AS candidate_name,a.resume_version_id,a.jd_version_id,
        a.default_match_run_id,a.id AS admission_id,a.admitted_at,v.version_no AS resume_version_no,
        v.original_file_name,
        w.professional_score,w.reference_score
      FROM matching_screening_admission a
      JOIN matching_match_run r ON r.id=a.default_match_run_id AND r.jd_version_id=a.jd_version_id
      JOIN matching_weighted_result w ON w.match_run_id=r.id
        AND w.candidate_id=a.candidate_id AND w.resume_version_id=a.resume_version_id
      JOIN talent_candidate c ON c.id=a.candidate_id
      JOIN talent_resume_version v ON v.id=a.resume_version_id
      WHERE a.jd_version_id=#{jdVersionId} AND a.returned_at IS NULL
        AND r.run_purpose='""" + MatchRunPurpose.Codes.COMPANY_DEFAULT + "'\n" + """
        AND r.weight_profile_version_id='""" + DefaultScoringConstants.WEIGHT_VERSION_ID + "'\n" + """
        AND r.personal_preference_version_id IS NULL
        AND r.status IN ('""" + MatchRunStatus.Codes.SUCCEEDED + "','" + MatchRunStatus.Codes.PARTIAL + "')" + """
        AND w.stable_rank IS NOT NULL
        AND (EXISTS (SELECT 1 FROM talent_resume_intake i JOIN talent_intake_batch b ON b.id=i.batch_id
          WHERE i.resume_version_id=a.resume_version_id AND i.score_run_id=r.id
            AND i.status='""" + ResumeIntakeStatus.Codes.COMPLETED + "' AND i.score_status='"
            + ResumeIntakeScoreStatus.Codes.SUCCEEDED + "'" + """
            AND b.jd_version_id=a.jd_version_id)
          OR EXISTS (SELECT 1 FROM talent_resume_jd_score_binding binding
            WHERE binding.candidate_id=a.candidate_id AND binding.resume_version_id=a.resume_version_id
              AND binding.jd_version_id=a.jd_version_id AND binding.default_match_run_id=r.id))
        AND NOT EXISTS (SELECT 1 FROM recruitment_application application
          WHERE application.resume_version_id=a.resume_version_id
            AND application.current_stage NOT IN ('TERMINATED','ONBOARDED'))
        AND (#{scopeAll}=TRUE OR r.owner_user_id=#{actorId}
          OR c.owner_user_id=#{actorId}
          <if test="managedUserIds != null and !managedUserIds.isEmpty()">
            OR r.owner_user_id IN <foreach collection="managedUserIds" item="id" open="(" separator="," close=")">#{id}</foreach>
          </if>
          <if test="visibleDepartmentIds != null and !visibleDepartmentIds.isEmpty()">
            OR r.owner_department_id IN <foreach collection="visibleDepartmentIds" item="id" open="(" separator="," close=")">#{id}</foreach>
          </if>)
        <if test="query != null">AND c.full_name LIKE CONCAT('%',#{query},'%')</if>
        <if test="beforeTime != null">
          AND (a.admitted_at &lt; #{beforeTime} OR (a.admitted_at=#{beforeTime} AND a.id &lt; #{beforeId}))
        </if>
      ORDER BY a.admitted_at DESC,a.id DESC LIMIT #{limit}
      </script>
      """)
  List<ScreeningCandidateRow> findSelectionCandidates(ScreeningSelectionQuery query);

  @SpecialSql("批量查询准入事实提供人才模块的实时分流裁决")
  @Select("<script>SELECT DISTINCT resume_version_id FROM matching_screening_admission"
      + " WHERE returned_at IS NULL AND candidate_id IN"
      + " <foreach collection=\"candidateIds\" item=\"id\" open=\"(\" separator=\",\" close=\")\">#{id}</foreach></script>")
  Set<String> activeResumeVersionIds(@Param("candidateIds") List<String> candidateIds);

  @SpecialSql("锁定准入状态并读取最新技术版本，避免返回和再次准入之间的旧快照")
  @Select("SELECT id,candidate_id,resume_version_id,jd_version_id,default_match_run_id,"
      + "admitted_by_user_id,owner_department_id,admitted_at,returned_at,version,ext,remark,"
      + "create_id,create_name,create_time,update_id,update_name,update_time"
      + " FROM matching_screening_admission WHERE id=#{id} FOR UPDATE")
  ScreeningAdmission lockAdmission(String id);

  @SpecialSql("仅当前未退回准入可以按技术版本退回，保留默认评分与所有历史")
  @Update("UPDATE matching_screening_admission SET returned_at=#{now},version=version+1,"
      + "update_id=#{actorId},update_time=#{now} WHERE id=#{id} AND version=#{version} AND returned_at IS NULL")
  int returnToResumes(String id, long version, String actorId, Instant now);

  @SpecialSql("候选人完成流转后按精确简历版本结束全部待处理准入，保留评分和准入历史")
  @Update("UPDATE matching_screening_admission SET returned_at=#{now},version=version+1,"
      + "update_id=#{actorId},update_time=#{now} WHERE candidate_id=#{candidateId}"
      + " AND resume_version_id=#{resumeVersionId} AND returned_at IS NULL")
  int completeActive(String candidateId, String resumeVersionId, String actorId, Instant now);

  @SpecialSql("再次准入复用唯一身份并推进技术版本，旧退回命令不能影响新一轮准入")
  @Update("UPDATE matching_screening_admission SET returned_at=NULL,admitted_at=#{now},"
      + "admitted_by_user_id=#{actorId},owner_department_id=#{departmentId},default_match_run_id=#{runId},"
      + "version=version+1,update_id=#{actorId},update_time=#{now}"
      + " WHERE id=#{id} AND version=#{version} AND returned_at IS NOT NULL")
  int readmit(String id, long version, String runId, String actorId, String departmentId, Instant now);
  @SpecialSql("三元组唯一键配合 INSERT IGNORE 提供并发准入幂等")
  @Insert("INSERT IGNORE INTO matching_screening_admission("
      + "id,candidate_id,resume_version_id,jd_version_id,default_match_run_id,"
      + "admitted_by_user_id,owner_department_id,admitted_at,create_id,create_time,update_id,update_time)"
      + " VALUES(#{id},#{candidateId},#{resumeVersionId},#{jdVersionId},#{runId},"
      + "#{actorId},#{departmentId},#{now},#{actorId},#{now},#{actorId},#{now})")
  int insertIgnore(
      String id,
      String candidateId,
      String resumeVersionId,
      String jdVersionId,
      String runId,
      String actorId,
      String departmentId,
      Instant now);

  @SpecialSql("单条参数化 INSERT IGNORE 批量写入准入，三元组唯一键裁决重复与并发")
  @Insert("""
      <script>
      INSERT IGNORE INTO matching_screening_admission(
        id,candidate_id,resume_version_id,jd_version_id,default_match_run_id,
        admitted_by_user_id,owner_department_id,admitted_at,create_id,create_time,update_id,update_time)
      VALUES
      <foreach collection="items" item="item" separator=",">
        (#{item.id},#{item.candidateId},#{item.resumeVersionId},#{item.jdVersionId},
         #{item.defaultMatchRunId},#{item.admittedByUserId},#{item.ownerDepartmentId},
         #{item.admittedAt},#{item.createId},#{item.createTime},#{item.updateId},#{item.updateTime})
      </foreach>
      </script>
      """)
  int insertBatchIgnore(@Param("items") List<ScreeningAdmission> items);

  @SpecialSql("按精确候选人、简历版本和岗位版本读取唯一准入事实")
  @Select("SELECT id,candidate_id,resume_version_id,jd_version_id,default_match_run_id,"
      + "admitted_by_user_id,owner_department_id,admitted_at,create_id,create_time,"
      + "create_name,update_id,update_name,update_time,version,ext,remark,returned_at FROM matching_screening_admission"
      + " WHERE candidate_id=#{candidateId} AND resume_version_id=#{resumeVersionId}"
      + " AND jd_version_id=#{jdVersionId} FOR UPDATE")
  ScreeningAdmission findByIdentity(
      String candidateId, String resumeVersionId, String jdVersionId);

  @SpecialSql("显式重评优先读取最新绑定，否则读取首次成功默认评分；重新解析不使评分失效，已准入项保持原运行")
  @Select(
      "<script> WITH canonical AS ("
          + " SELECT w.candidate_id,w.resume_version_id,r.jd_version_id,r.id AS default_match_run_id,"
          + " w.professional_score,w.bonus_score,w.reference_score,w.must_tier,"
          + " r.owner_user_id,r.owner_department_id,"
          + " ROW_NUMBER() OVER(PARTITION BY w.candidate_id,w.resume_version_id,r.jd_version_id"
          + " ORDER BY CASE WHEN score_binding.id IS NULL THEN 1 ELSE 0 END ASC,"
          + " score_binding.selected_at DESC,r.create_time ASC,r.id ASC) AS position_no"
          + " FROM matching_weighted_result w JOIN matching_match_run r ON r.id=w.match_run_id"
          + " JOIN matching_base_evaluation evaluation ON evaluation.id=w.base_evaluation_id"
          + " LEFT JOIN talent_resume_jd_score_binding score_binding"
          + " ON score_binding.candidate_id=w.candidate_id"
          + " AND score_binding.resume_version_id=w.resume_version_id"
          + " AND score_binding.jd_version_id=r.jd_version_id"
          + " AND score_binding.default_match_run_id=r.id"
          + " WHERE r.run_purpose='" + MatchRunPurpose.Codes.COMPANY_DEFAULT + "'"
          + " AND r.weight_profile_version_id='" + DefaultScoringConstants.WEIGHT_VERSION_ID + "'"
          + " AND r.personal_preference_version_id IS NULL"
          + " AND r.status IN ('" + MatchRunStatus.Codes.SUCCEEDED + "','"
          + MatchRunStatus.Codes.PARTIAL + "') AND w.stable_rank IS NOT NULL"
          + " AND (EXISTS (SELECT 1 FROM talent_resume_intake i JOIN talent_intake_batch b ON b.id=i.batch_id"
          + " WHERE i.resume_version_id=w.resume_version_id AND i.score_run_id=r.id"
          + " AND i.status='" + ResumeIntakeStatus.Codes.COMPLETED + "' AND i.score_status='"
          + ResumeIntakeScoreStatus.Codes.SUCCEEDED + "' AND b.jd_version_id=r.jd_version_id)"
          + " OR score_binding.id IS NOT NULL))"
          + " SELECT c.candidate_id,c.resume_version_id,c.jd_version_id,c.default_match_run_id,"
          + " c.professional_score,c.bonus_score,c.reference_score,c.must_tier,"
          + " a.id AS admission_id,a.admitted_at,a.version AS admission_version"
          + " FROM canonical c LEFT JOIN matching_screening_admission a"
          + " ON a.candidate_id=c.candidate_id AND a.resume_version_id=c.resume_version_id"
          + " AND a.jd_version_id=c.jd_version_id AND a.returned_at IS NULL"
          + " JOIN talent_candidate candidate ON candidate.id=c.candidate_id"
          + " WHERE ((a.id IS NULL AND c.position_no=1)"
          + " OR c.default_match_run_id=a.default_match_run_id)"
          + " AND NOT EXISTS (SELECT 1 FROM recruitment_application application"
          + " WHERE application.resume_version_id=c.resume_version_id"
          + " AND application.current_stage NOT IN ('TERMINATED','ONBOARDED'))"
          + " AND (#{scopeAll}=TRUE OR c.owner_user_id=#{actorId}"
          + " OR (a.id IS NOT NULL AND candidate.owner_user_id=#{actorId})"
          + " <if test=\"managedUserIds != null and !managedUserIds.isEmpty()\">"
          + " OR c.owner_user_id IN <foreach collection=\"managedUserIds\" item=\"id\""
          + " open=\"(\" separator=\",\" close=\")\">#{id}</foreach></if>"
          + " <if test=\"visibleDepartmentIds != null and !visibleDepartmentIds.isEmpty()\">"
          + " OR c.owner_department_id IN <foreach collection=\"visibleDepartmentIds\" item=\"id\""
          + " open=\"(\" separator=\",\" close=\")\">#{id}</foreach></if>)"
          + " <choose><when test=\"admittedOnly\">AND a.id IS NOT NULL</when><otherwise>"
          + " AND c.candidate_id IN <foreach collection=\"candidateIds\" item=\"candidateId\""
          + " open=\"(\" separator=\",\" close=\")\">#{candidateId}</foreach>"
          + " </otherwise></choose>"
          + " ORDER BY COALESCE(a.admitted_at,TIMESTAMP('1000-01-01')) DESC,"
          + " c.candidate_id,c.jd_version_id,c.resume_version_id LIMIT 1001 </script>")
  List<ScreeningCandidateRow> findScreeningCandidates(
      @Param("candidateIds") List<String> candidateIds,
      @Param("actorId") String actorId,
      @Param("scopeAll") boolean scopeAll,
      @Param("managedUserIds") Set<String> managedUserIds,
      @Param("visibleDepartmentIds") Set<String> visibleDepartmentIds,
      @Param("admittedOnly") boolean admittedOnly);
}
