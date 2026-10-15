package com.ezaigc.hr.recruiting.recruitment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationStageCountDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationStageCountQuery;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkItemRow;
import com.ezaigc.hr.recruiting.recruitment.enums.ApplicationStepStatus;
import com.ezaigc.hr.recruiting.recruitment.enums.OnboardingProcessStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ApplicationMapper extends BaseMapper<Application>, ApplicationSqlOperations {

  @SpecialSql("候选人合并在双方行锁之后当前读所有业务历史，禁止遗留未迁移引用")
  @Select("SELECT candidate_id FROM recruitment_application WHERE candidate_id=#{candidateId} LIMIT 1 FOR UPDATE")
  String findMergeApplication(String candidateId);

  @SpecialSql("候选人合并在双方行锁之后当前读所有业务历史，禁止遗留未迁移引用")
  @Select("SELECT candidate_id FROM interview_interview WHERE candidate_id=#{candidateId} LIMIT 1 FOR UPDATE")
  String findMergeInterview(String candidateId);

  @SpecialSql("候选人合并在双方行锁之后当前读所有业务历史，禁止遗留未迁移引用")
  @Select("SELECT candidate_id FROM recruitment_intention WHERE candidate_id=#{candidateId} LIMIT 1 FOR UPDATE")
  String findMergeIntention(String candidateId);

  @SpecialSql("候选人合并在双方行锁之后当前读所有业务历史，禁止遗留未迁移引用")
  @Select("SELECT candidate_id FROM recruitment_candidate_engagement_lock WHERE candidate_id=#{candidateId} LIMIT 1 FOR UPDATE")
  String findMergeEngagement(String candidateId);

  @SpecialSql("候选人合并在双方行锁之后当前读所有业务历史，禁止遗留未迁移引用")
  @Select("SELECT candidate_id FROM recruitment_candidate_engagement_lock_history WHERE candidate_id=#{candidateId} LIMIT 1 FOR UPDATE")
  String findMergeEngagementHistory(String candidateId);

  @SpecialSql("候选人合并在双方行锁之后当前读所有业务历史，禁止遗留未迁移引用")
  @Select("SELECT candidate_id FROM recruitment_engagement_collaboration_request WHERE candidate_id=#{candidateId} LIMIT 1 FOR UPDATE")
  String findMergeCollaboration(String candidateId);

  @SpecialSql("候选人合并在双方行锁之后当前读所有业务历史，禁止遗留未迁移引用")
  @Select("SELECT candidate_id FROM submission_client_submission WHERE candidate_id=#{candidateId} LIMIT 1 FOR UPDATE")
  String findMergeSubmission(String candidateId);

  @SpecialSql("候选人合并在双方行锁之后当前读所有业务历史，禁止遗留未迁移引用")
  @Select("SELECT candidate_id FROM submission_client_restriction WHERE candidate_id=#{candidateId} LIMIT 1 FOR UPDATE")
  String findMergeClientRestriction(String candidateId);

  @SpecialSql("候选人合并在双方行锁之后当前读所有业务历史，禁止遗留未迁移引用")
  @Select("SELECT candidate_id FROM submission_client_restriction_current WHERE candidate_id=#{candidateId} LIMIT 1 FOR UPDATE")
  String findMergeClientRestrictionProjection(String candidateId);

  @SpecialSql("候选人合并在双方行锁之后当前读所有业务历史，禁止遗留未迁移引用")
  @Select("SELECT candidate_id FROM demand_jd_resume_source WHERE candidate_id=#{candidateId} LIMIT 1 FOR UPDATE")
  String findMergeJdSource(String candidateId);

  @SpecialSql("在招聘域内按归属、部门和协作范围聚合；显式授权 ID 通过 identity API 分批提供并排除重计")
  @Select("""
      <script>
      SELECT a.current_stage stage, COUNT(*) count, MIN(a.id) first_application_id
      FROM recruitment_application a
      WHERE a.create_time &gt;= #{from} AND a.create_time &lt; #{to}
        <if test='jdId != null'>AND a.jd_id=#{jdId}</if>
        <if test='jdVersionId != null'>AND a.jd_version_id=#{jdVersionId}</if>
        <if test='grantedApplicationIds != null'>
          AND a.id IN <foreach collection='grantedApplicationIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
          AND NOT
        </if>
        <if test='grantedApplicationIds == null'>AND</if>
        COALESCE((#{allScope}=TRUE OR a.owner_user_id=#{actorId}
          <if test='managedUserIds != null and !managedUserIds.isEmpty()'>
            OR a.owner_user_id IN <foreach collection='managedUserIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
          </if>
          <if test='departmentIds != null and !departmentIds.isEmpty()'>
            OR a.owner_department_id IN <foreach collection='departmentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
          </if>
          OR EXISTS (SELECT 1 FROM recruitment_application_collaborator c
            WHERE c.application_id=a.id AND c.user_id=#{actorId})), FALSE)
      GROUP BY a.current_stage ORDER BY MIN(a.id)
      </script>
      """)
  List<ApplicationStageCountDTO> countStages(ApplicationStageCountQuery query);

  @SpecialSql("简历分流已持有版本锁；当前读避免事务旧快照漏掉刚提交的招聘流程")
  @Select("SELECT id FROM recruitment_application WHERE resume_version_id=#{resumeVersionId} LIMIT 1 FOR UPDATE")
  String findRecruitmentHistoryForRouting(String resumeVersionId);

  @SpecialSql("简历路由锁内只检查未终态招聘活动，终态历史允许开启新的 JD 评分周期")
  @Select("SELECT id FROM recruitment_application WHERE resume_version_id=#{resumeVersionId}"
      + " AND current_stage NOT IN ('TERMINATED','ONBOARDED') LIMIT 1 FOR UPDATE")
  String findActiveRecruitmentForRouting(String resumeVersionId);

  @SpecialSql("招聘申请状态迁移前需要对当前行加排他锁")
  @Select(
      "SELECT"
          + " id,candidate_id,demand_id,jd_id,jd_version_id,resume_version_id,workflow_template_version_id,match_plan_version_id,recruitment_type,progressive_follow_up,current_stage,outcome,owner_user_id,owner_department_id,active_slot,terminal_at,version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time"
          + " FROM recruitment_application WHERE id=#{id} FOR UPDATE")
  Application lockApplication(String id);

  @SpecialSql("HC 容量写路径先读取 Application 的不可变 JD 版本 ID，以统一 JD 后锁定 Application")
  @Select("SELECT jd_version_id FROM recruitment_application WHERE id=#{id}")
  String findJdVersionId(String id);

  @SpecialSql("招聘写命令先读取不可变候选人 ID，统一 Candidate → JD → Application 锁序")
  @Select("SELECT candidate_id FROM recruitment_application WHERE id=#{id}")
  String findCandidateId(String id);

  @SpecialSql("申请列表包含工作流步骤 EXISTS、多个可选过滤条件和稳定游标")
  @Select(
      "SELECT id,candidate_id,demand_id,jd_id,jd_version_id,resume_version_id,workflow_template_version_id,match_plan_version_id,recruitment_type,progressive_follow_up,current_stage,outcome,owner_user_id,owner_department_id,active_slot,terminal_at,version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time FROM recruitment_application"
          + " WHERE (#{jdId} IS NULL OR jd_id=#{jdId}) AND (#{jdVersionId} IS NULL OR jd_version_id=#{jdVersionId})"
          + " AND (#{ownerUserId} IS NULL OR owner_user_id=#{ownerUserId})"
          + " AND (#{stage} IS NULL OR current_stage=#{stage} OR (#{stage}='ONBOARDED' AND EXISTS"
          + " (SELECT 1 FROM recruitment_onboarding_process op WHERE op.application_id=recruitment_application.id"
          + " AND op.status IN ('" + OnboardingProcessStatus.Codes.IN_PROGRESS
          + "','" + OnboardingProcessStatus.Codes.ONBOARDED + "'))))"
          + " AND (#{lifecycle} = 'ALL' OR (#{lifecycle} = 'ACTIVE' AND terminal_at IS NULL"
          + " AND NOT EXISTS (SELECT 1 FROM recruitment_onboarding_process active_op"
          + " WHERE active_op.application_id=recruitment_application.id AND active_op.status IN ('"
          + OnboardingProcessStatus.Codes.IN_PROGRESS + "','" + OnboardingProcessStatus.Codes.ONBOARDED + "')))"
          + " OR (#{lifecycle} = 'ARCHIVED' AND (terminal_at IS NOT NULL OR EXISTS"
          + " (SELECT 1 FROM recruitment_onboarding_process archived_op"
          + " WHERE archived_op.application_id=recruitment_application.id AND archived_op.status IN ('"
          + OnboardingProcessStatus.Codes.IN_PROGRESS + "','" + OnboardingProcessStatus.Codes.ONBOARDED + "')))))"
          + " AND (#{workflowStepCode} IS NULL OR EXISTS (SELECT 1 FROM recruitment_application_workflow_step fs WHERE fs.application_id=recruitment_application.id AND fs.step_code=#{workflowStepCode} AND fs.status IN ('"
          + ApplicationStepStatus.Codes.PENDING
          + "','"
          + ApplicationStepStatus.Codes.IN_PROGRESS
          + "')))"
          + " AND (#{from} IS NULL OR update_time >= #{from}) AND (#{to} IS NULL OR update_time < #{to})"
          + " AND (#{interviewType} IS NULL OR EXISTS (SELECT 1 FROM recruitment_application_workflow_step its"
          + " WHERE its.application_id=recruitment_application.id AND current_stage='IN_PROCESS' AND terminal_at IS NULL"
          + " AND its.status IN ('" + ApplicationStepStatus.Codes.PENDING
          + "','" + ApplicationStepStatus.Codes.IN_PROGRESS + "')"
          + " AND NOT EXISTS (SELECT 1 FROM recruitment_application_workflow_step earlier"
          + " WHERE earlier.application_id=its.application_id AND earlier.status IN ('" + ApplicationStepStatus.Codes.PENDING
          + "','" + ApplicationStepStatus.Codes.IN_PROGRESS + "')"
          + " AND (earlier.sequence_no < its.sequence_no OR (earlier.sequence_no=its.sequence_no AND earlier.id < its.id))) AND ("
          + " (#{interviewType}='INTERNAL' AND (its.step_code='PRE_SUBMISSION_INTERNAL_INTERVIEW'"
          + " OR (its.step_code LIKE 'INTERVIEW_ROUND_%' AND recruitment_type='INTERNAL')))"
          + " OR (#{interviewType}='CLIENT' AND its.step_code LIKE 'INTERVIEW_ROUND_%' AND recruitment_type='EXTERNAL')"
          + " OR (#{interviewType}='CLIENT_AI' AND its.step_code='CLIENT_AI_INTERVIEW'))))"
          + " AND (#{cursorTime} IS NULL OR update_time < #{cursorTime} OR (update_time=#{cursorTime} AND id < #{cursorId}))"
          + " ORDER BY update_time DESC,id DESC LIMIT #{limit}")
  /** 查询应聘流程列表。 */
  List<Application> findApplications(
      String jdId,
      String jdVersionId,
      String ownerUserId,
      String stage,
      String workflowStepCode,
      String lifecycle,
      Instant from,
      Instant to,
      String interviewType,
      boolean onboarding,
      Instant cursorTime,
      String cursorId,
      int limit);

  /** 更新指定业务记录。 */
  @SpecialSql("Offer 重新进入处理中需要以 COALESCE 原子保留首次开始时间并批量守卫状态")
  @Update(
      "UPDATE recruitment_application_workflow_step SET status='"
          + ApplicationStepStatus.Codes.IN_PROGRESS
          + "',started_at=COALESCE(started_at,#{now}),completed_at=NULL,result_reason=NULL,owner_user_id=#{ownerUserId},due_at=#{dueAt},version=version+1,update_id=#{actorId},update_time=#{now}"
          + " WHERE application_id=#{applicationId} AND step_code='OFFER' AND status IN"
          + " ('"
          + ApplicationStepStatus.Codes.PENDING
          + "','"
          + ApplicationStepStatus.Codes.FAILED
          + "','"
          + ApplicationStepStatus.Codes.WITHDRAWN
          + "')")
  int markOfferInProgress(
      String applicationId, String ownerUserId, Instant dueAt, String actorId, Instant now);

  @SpecialSql("路径变更记录包含两个 JSON 文档，写入时需要数据库 JSON 类型校验")
  @Insert(
      "INSERT INTO"
          + " recruitment_workflow_path_change_record(id,application_id,change_type,before_step_ids_json,after_step_ids_json,reason,application_version,operated_by_user_id,create_time)"
          + " VALUES(#{id},#{applicationId},#{type},CAST(#{beforeJson} AS JSON),CAST(#{afterJson}"
          + " AS JSON),#{reason},#{version},#{actorId},#{now})")
  int insertPathChange(
      String id,
      String applicationId,
      String type,
      String beforeJson,
      String afterJson,
      String reason,
      long version,
      String actorId,
      Instant now);

  @SpecialSql("协作人变更记录包含前后两个 JSON 快照，写入时需要数据库 JSON 类型校验")
  @Insert(
      "INSERT INTO"
          + " recruitment_application_collaborator_change_record(id,application_id,before_user_ids_json,after_user_ids_json,reason,application_version,operated_by_user_id,create_time)"
          + " VALUES(#{id},#{applicationId},CAST(#{beforeJson} AS JSON),CAST(#{afterJson} AS"
          + " JSON),#{reason},#{version},#{actorId},#{now})")
  /** 新增协作人变更。 */
  int insertCollaboratorChange(
      String id,
      String applicationId,
      String beforeJson,
      String afterJson,
      String reason,
      long version,
      String actorId,
      Instant now);

  @SpecialSql("工作台任务是申请与工作流步骤的联表投影")
  @Select(
      """
          <script>
          SELECT a.id application_id,a.candidate_id,a.jd_id,a.jd_version_id,s.id step_id,
                 s.step_code,s.status step_status,s.owner_user_id,s.due_at,
                 s.update_time task_anchor_at,a.current_stage,a.outcome,
                 a.create_time application_created_at
            FROM recruitment_application a
            JOIN recruitment_application_workflow_step s ON s.application_id = a.id
           WHERE s.status IN ('"""
          + ApplicationStepStatus.Codes.PENDING
          + "','"
          + ApplicationStepStatus.Codes.IN_PROGRESS
          + "')\n"
          + """
             AND (#{scopeAll} = TRUE OR s.owner_user_id = #{actorId}
                  <if test="managedUserIds != null and !managedUserIds.isEmpty()">
                    OR s.owner_user_id IN
                    <foreach collection="managedUserIds" item="value" open="(" separator="," close=")">#{value}</foreach>
                  </if>)
           ORDER BY s.due_at,s.id
           LIMIT #{limit}
          </script>
          """)
  /** 查询工作条目列表。 */
  List<WorkItemRow> findWorkItems(
      @Param("actorId") String actorId,
      @Param("scopeAll") boolean scopeAll,
      @Param("managedUserIds") Set<String> managedUserIds,
      @Param("limit") int limit);

  @SpecialSql("按候选人批量核实当前用户未完成的招聘待办，不使用工作台条数上限")
  @Select(
      """
          <script>
          SELECT DISTINCT a.candidate_id
            FROM recruitment_application a
            JOIN recruitment_application_workflow_step s ON s.application_id = a.id
           WHERE s.owner_user_id = #{actorId}
             AND s.status IN ('"""
          + ApplicationStepStatus.Codes.PENDING
          + "','"
          + ApplicationStepStatus.Codes.IN_PROGRESS
          + "')\n"
          + """
             AND a.terminal_at IS NULL
             <if test="jdId != null">AND a.jd_id = #{jdId}</if>
             AND a.candidate_id IN
             <foreach collection="candidateIds" item="candidateId" open="(" separator="," close=")">
               #{candidateId}
             </foreach>
          </script>
          """)
  List<String> findMyTaskCandidateIds(
      @Param("actorId") String actorId, @Param("candidateIds") List<String> candidateIds,
      @Param("jdId") String jdId);

  @SpecialSql("幂等键以 INSERT IGNORE 原子竞争，重复请求不能覆盖原始指纹与结果")
  @Insert(
      "INSERT IGNORE INTO"
          + " recruitment_idempotency_record(actor_user_id,idempotency_key,action_code,request_fingerprint,target_id,create_time)"
          + " VALUES(#{actorId},#{key},#{action},#{fingerprint},#{targetId},#{now})")
  /** 新增幂等。 */
  int insertIdempotency(
      String actorId, String key, String action, String fingerprint, String targetId, Instant now);
}
