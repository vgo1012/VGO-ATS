package com.ezaigc.hr.talent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.talent.domain.dto.IntakeScoringPreparationDTO;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.CandidateResumeRow;
import com.ezaigc.hr.talent.domain.entity.IdentityConflict;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.enums.CandidateStatus;
import com.ezaigc.hr.talent.enums.IntakeBatchStatus;
import com.ezaigc.hr.talent.enums.ResumeIntakeProcessingStatus;
import com.ezaigc.hr.talent.enums.ResumeIntakeProblemStatus;
import com.ezaigc.hr.talent.enums.ResumeIntakeScoreStatus;
import com.ezaigc.hr.talent.enums.ResumeIntakeStatus;
import com.ezaigc.hr.talent.enums.ResumeIntakeImportStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TalentIntakeMapper extends BaseMapper<ResumeIntake>, TalentIntakeSqlOperations {
  @SpecialSql("按冻结岗位查找已有简历材料关联，再由应用层实时回查共享准入")
  @Select("<script>SELECT candidate_id FROM ("
      + "SELECT i.candidate_id FROM talent_resume_intake i JOIN talent_intake_batch b ON b.id=i.batch_id "
      + "WHERE b.jd_version_id=#{jdVersionId} AND i.resume_version_id IS NOT NULL AND i.candidate_id IS NOT NULL "
      + "UNION SELECT candidate_id FROM talent_resume_jd_score_binding WHERE jd_version_id=#{jdVersionId}"
      + ") associated <if test='afterId != null'>WHERE candidate_id &gt; #{afterId}</if> "
      + "ORDER BY candidate_id LIMIT #{limit}</script>")
  List<String> findCandidateIdsForJdVersion(@Param("jdVersionId") String jdVersionId,
      @Param("afterId") String afterId, @Param("limit") int limit);
  @SpecialSql("领取事实已就绪且关联岗位仍待创建或同步评分的完成收件")
  @Select("SELECT i.id,i.batch_id,i.source_channel_id,i.source_recruiter_user_id,b.default_owner_user_id,b.jd_version_id,i.owner_user_id,i.owner_department_id,i.handled_by_user_id,i.original_file_name,i.media_type,i.size_bytes,i.content_sha256,i.object_key,i.processing_id,p.status AS processing_status,p.attempt_count AS processing_attempt,i.scan_status,i.status,i.failure_code,i.candidate_id,i.resume_version_id,i.score_run_id,i.score_status,i.version,i.ext,i.remark,i.create_id,i.create_name,i.create_time,i.update_id,i.update_name,i.update_time FROM talent_resume_intake i JOIN talent_intake_batch b ON b.id=i.batch_id JOIN talent_resume_intake_processing p ON p.id=i.processing_id WHERE i.status='"
      + ResumeIntakeStatus.Codes.COMPLETED
      + "' AND b.jd_version_id IS NOT NULL AND i.resume_version_id IS NOT NULL AND i.score_status IN ('"
      + ResumeIntakeScoreStatus.Codes.NOT_APPLICABLE + "','"
      + ResumeIntakeScoreStatus.Codes.WAITING_JD + "','"
      + ResumeIntakeScoreStatus.Codes.WAITING_CONFIG + "','"
      + ResumeIntakeScoreStatus.Codes.QUEUED + "','"
      + ResumeIntakeScoreStatus.Codes.RUNNING
      + "') AND EXISTS (SELECT 1 FROM talent_resume_fact_snapshot f WHERE f.resume_version_id=i.resume_version_id AND f.current_marker='Y') ORDER BY i.update_time,i.id LIMIT #{limit}")
  List<ResumeIntake> findScorableIntakes(int limit);

  @SpecialSql("共享解析事实导入任务异步完成前持续查询已确认且事实尚未绪的收件")
  @Select("SELECT i.id,i.batch_id,i.source_channel_id,i.source_recruiter_user_id,b.default_owner_user_id,b.jd_version_id,i.owner_user_id,i.owner_department_id,i.handled_by_user_id,i.original_file_name,i.media_type,i.size_bytes,i.content_sha256,i.object_key,i.processing_id,p.status AS processing_status,p.attempt_count AS processing_attempt,i.scan_status,i.status,i.failure_code,i.candidate_id,i.resume_version_id,i.score_run_id,i.score_status,i.version,i.ext,i.remark,i.create_id,i.create_name,i.create_time,i.update_id,i.update_name,i.update_time FROM talent_resume_intake i JOIN talent_intake_batch b ON b.id=i.batch_id JOIN talent_resume_intake_processing p ON p.id=i.processing_id WHERE i.status='"
      + ResumeIntakeStatus.Codes.CONFIRMED + "' AND p.status='"
      + ResumeIntakeProcessingStatus.Codes.SUCCEEDED
      + "' ORDER BY i.update_time,i.id LIMIT #{limit}")
  List<ResumeIntake> findAdoptingIntakes(int limit);

  @SpecialSql("我的简历只读取当前简历事实与公司默认评分都已完成的业务版本")
  @Select(
      "<script>SELECT candidate_id,resume_version_id,jd_version_id,default_match_run_id FROM ("
          + " SELECT r.candidate_id,v.id AS resume_version_id,b.jd_version_id,"
          + " i.score_run_id AS default_match_run_id,"
          + " ROW_NUMBER() OVER(PARTITION BY r.candidate_id,v.id,b.jd_version_id"
          + " ORDER BY i.update_time DESC,i.id DESC) AS position_no"
          + " FROM talent_resume r"
          + " JOIN talent_resume_version v ON v.resume_id=r.id AND v.version_no=r.current_version_no"
          + " JOIN talent_resume_intake i ON i.resume_version_id=v.id"
          + " JOIN talent_intake_batch b ON b.id=i.batch_id"
          + " WHERE i.status='"
          + ResumeIntakeStatus.Codes.COMPLETED
          + "' AND i.score_status='"
          + ResumeIntakeScoreStatus.Codes.SUCCEEDED
          + "' AND i.score_run_id IS NOT NULL AND b.jd_version_id IS NOT NULL"
          + " AND r.candidate_id IN <foreach collection=\"candidateIds\" item=\"id\""
          + " open=\"(\" separator=\",\" close=\")\">#{id}</foreach>"
          + ") ready WHERE position_no=1</script>")
  List<CandidateResumeRow> findCurrentCandidateResumes(
      @Param("candidateIds") List<String> candidateIds);

  @SpecialSql("共享历史保留全部简历版本的收件岗位，不以当前版本或评分完成状态隐藏历史；调用方已核验共享候选人集合")
  @Select("""
      <script>
      SELECT candidate_id,resume_version_id,jd_version_id,default_match_run_id FROM (
        SELECT r.candidate_id,v.id AS resume_version_id,b.jd_version_id,
          i.score_run_id AS default_match_run_id,
          ROW_NUMBER() OVER(PARTITION BY r.candidate_id,v.id,b.jd_version_id
            ORDER BY i.update_time DESC,i.id DESC) AS position_no
        FROM talent_resume r
        JOIN talent_resume_version v ON v.resume_id=r.id
        JOIN talent_resume_intake i ON i.resume_version_id=v.id
        JOIN talent_intake_batch b ON b.id=i.batch_id
        WHERE b.jd_version_id IS NOT NULL
          AND r.candidate_id IN
          <foreach collection="candidateIds" item="id" open="(" separator="," close=")">#{id}</foreach>
      ) historical WHERE position_no=1
      ORDER BY candidate_id,resume_version_id,jd_version_id
      </script>
      """)
  List<CandidateResumeRow> findCandidateResumeHistory(@Param("candidateIds") List<String> candidateIds);

  @SpecialSql("摘要按简历主记录读取唯一当前版本，不连接可重复的收件岗位上下文")
  @Select("<script>SELECT r.candidate_id,v.id AS resume_version_id"
      + " FROM talent_resume r JOIN talent_resume_version v"
      + " ON v.resume_id=r.id AND v.version_no=r.current_version_no"
      + " WHERE r.candidate_id IN <foreach collection=\"candidateIds\" item=\"id\""
      + " open=\"(\" separator=\",\" close=\")\">#{id}</foreach></script>")
  List<CandidateResumeRow> findCurrentCandidateResumeVersions(
      @Param("candidateIds") List<String> candidateIds);

  /** 更新指定业务记录。 */
  @SpecialSql("批次统计需要在单条语句中原子累加，并在全部文件处理完成前保持非终态")
  @Update(
      "UPDATE talent_intake_batch SET"
          + " total_count=total_count+1,succeeded_count=succeeded_count+#{succeeded},failed_count=failed_count+#{failed},"
          + "status='"
          + IntakeBatchStatus.Codes.PROCESSING
          + "',update_id=#{actorId},update_time=#{now} WHERE id=#{id}"
          + " AND status IN ('"
          + IntakeBatchStatus.Codes.OPEN
          + "','"
          + IntakeBatchStatus.Codes.PROCESSING
          + "')")
  /** 递增批次。 */
  int incrementBatch(String id, int succeeded, int failed, String actorId, Instant now);

  /** 更新指定业务记录。 */
  @SpecialSql("批次完成状态取决于数据库内累计失败数并需要状态守卫")
  @Update(
      "UPDATE talent_intake_batch SET status=CASE WHEN failed_count>0 THEN '"
          + IntakeBatchStatus.Codes.COMPLETED_WITH_ERRORS
          + "' ELSE '"
          + IntakeBatchStatus.Codes.COMPLETED
          + "' END,update_id=#{actorId},update_time=#{now} WHERE id=#{id}"
          + " AND status IN ('"
          + IntakeBatchStatus.Codes.OPEN
          + "','"
          + IntakeBatchStatus.Codes.PROCESSING
          + "')")
  /** 完成批次。 */
  int completeBatch(String id, String actorId, Instant now);

  @SpecialSql("后台收件完成器只读取共享处理已到指定状态且仍等待解析的有限批次")
  @Select("SELECT i.id,i.batch_id,i.source_channel_id,i.source_recruiter_user_id,b.default_owner_user_id,b.jd_version_id,i.owner_user_id,i.owner_department_id,i.handled_by_user_id,i.original_file_name,i.media_type,i.size_bytes,i.content_sha256,i.object_key,i.processing_id,p.status AS processing_status,p.attempt_count AS processing_attempt,i.scan_status,i.status,i.failure_code,i.candidate_id,i.resume_version_id,i.score_run_id,i.score_status,i.version,i.create_id,i.create_time,i.update_id,i.update_time,i.ext,i.remark,i.create_name,i.update_name FROM talent_resume_intake i JOIN talent_intake_batch b ON b.id=i.batch_id JOIN talent_resume_intake_processing p ON p.id=i.processing_id WHERE i.status='"
      + ResumeIntakeStatus.Codes.PARSING
      + "' AND p.status=#{processingStatus} ORDER BY i.create_time,i.id LIMIT #{limit}")
  List<ResumeIntake> findPendingIntakes(String processingStatus, int limit);

  @SpecialSql("联合解析评分只选择同一共享处理下最早的已绑定岗位收件")
  @Select("SELECT i.id,b.jd_version_id,i.handled_by_user_id,i.source_recruiter_user_id,i.create_id"
      + " FROM talent_resume_intake i JOIN talent_intake_batch b ON b.id=i.batch_id"
      + " WHERE i.processing_id=#{processingId} AND i.status='"
      + ResumeIntakeStatus.Codes.PARSING
      + "' AND b.jd_version_id IS NOT NULL ORDER BY i.create_time,i.id LIMIT 1")
  IntakeScoringPreparationDTO findScoringPreparationIntake(String processingId);

  @SpecialSql("后台收件状态必须带前置状态条件单调推进")
  @Update("UPDATE talent_resume_intake SET status=#{nextStatus},failure_code=#{failureCode},handled_by_user_id=#{actorId},version=version+1,update_id=#{actorId},update_time=#{now} WHERE id=#{id} AND status=#{expectedStatus}")
  int advanceIntake(String id, String expectedStatus, String nextStatus, String failureCode,
      String actorId, Instant now);

  @SpecialSql("身份待核对收件仅按版本和原解析绑定切换到独立重新解析结果")
  @Update("UPDATE talent_resume_intake SET processing_id=#{newProcessingId},status='"
      + ResumeIntakeStatus.Codes.PARSING
      + "',failure_code=NULL,handled_by_user_id=#{actorId},version=version+1,update_id=#{actorId},update_time=#{now}"
      + " WHERE id=#{id} AND version=#{version} AND processing_id=#{oldProcessingId}"
      + " AND status='" + ResumeIntakeStatus.Codes.IDENTITY_REVIEW_REQUIRED + "'"
      + " AND failure_code='" + ResumeIntakeStatus.Codes.IDENTITY_REVIEW_REQUIRED
      + "' AND scan_status=#{cleanScanStatus}"
      + " AND candidate_id IS NULL AND resume_version_id IS NULL")
  int reparseIntake(String id, long version, String oldProcessingId, String newProcessingId,
      String actorId, Instant now, String cleanScanStatus);

  @SpecialSql("评分异步轮询按收件技术版本 CAS，避免旧结果覆盖人工重试")
  @Update("UPDATE talent_resume_intake SET score_run_id=COALESCE(#{runId},score_run_id),score_status=#{scoreStatus},failure_code=#{failureCode},version=version+1,update_id=#{actorId},update_time=#{now} WHERE id=#{id} AND version=#{expectedVersion}")
  int updateScore(String id, long expectedVersion, String runId, String scoreStatus, String failureCode,
      String actorId, Instant now);

  @SpecialSql("收件详情需要联查批次默认负责人形成业务投影")
  @Select(
      """
      SELECT i.id,i.batch_id,i.source_channel_id,i.source_recruiter_user_id,
             b.default_owner_user_id,b.jd_version_id,i.owner_user_id,i.owner_department_id,i.handled_by_user_id,
             i.original_file_name,i.media_type,i.size_bytes,i.content_sha256,i.object_key,i.processing_id,
             p.status AS processing_status,p.attempt_count AS processing_attempt,
             NULLIF(JSON_UNQUOTE(JSON_EXTRACT(p.parsed_facts_json,'$.candidate.fullName')),'null') AS parsed_full_name,
             NULLIF(JSON_UNQUOTE(JSON_EXTRACT(p.parsed_facts_json,'$.candidate.email')),'null') AS parsed_email,
             NULLIF(JSON_UNQUOTE(JSON_EXTRACT(p.parsed_facts_json,'$.candidate.phone')),'null') AS parsed_phone,
             i.scan_status,i.status,i.failure_code,i.candidate_id,i.resume_version_id,i.score_run_id,i.score_status,
             i.version,
             i.ext,i.remark,i.create_id,i.create_name,i.create_time,i.update_id,i.update_name,i.update_time,
             EXISTS (SELECT 1 FROM talent_resume_intake duplicate_intake WHERE duplicate_intake.content_sha256=i.content_sha256 AND duplicate_intake.id!=i.id) AS duplicate_content
        FROM talent_resume_intake i JOIN talent_intake_batch b ON b.id=i.batch_id
        LEFT JOIN talent_resume_intake_processing p ON p.id=i.processing_id
       WHERE i.id=#{id}
      """)
  /** 查询接收。 */
  ResumeIntake findIntake(String id);

  String INTAKE_FILTER_SQL =
      " FROM talent_resume_intake i JOIN talent_intake_batch b ON b.id=i.batch_id\n"
          + " LEFT JOIN talent_candidate candidate ON candidate.id=i.candidate_id\n"
          + " LEFT JOIN talent_resume_intake_processing p ON p.id=i.processing_id\n"
          + " WHERE i.status != 'IGNORED'\n"
          + " AND (#{batchId} IS NULL OR i.batch_id=#{batchId})\n"
          + " AND (#{status} IS NULL"
          + " OR (#{status}='" + ResumeIntakeProblemStatus.Codes.PROBLEM + "' AND (i.status IN ('"
          + ResumeIntakeStatus.Codes.IDENTITY_REVIEW_REQUIRED + "','"
          + ResumeIntakeStatus.Codes.READY_FOR_CONFIRMATION + "','"
          + ResumeIntakeStatus.Codes.FAILED_RETRYABLE + "','"
          + ResumeIntakeStatus.Codes.REJECTED + "','"
          + ResumeIntakeStatus.Codes.QUARANTINED + "')"
          + " OR i.score_status IN ('" + ResumeIntakeScoreStatus.Codes.FAILED_RETRYABLE + "','"
          + ResumeIntakeScoreStatus.Codes.FAILED_FINAL + "')"
          + " OR b.jd_version_id IS NULL"
          + " OR i.failure_code='RESUME_VERSION_DUPLICATE_CONTENT'"
          + " OR EXISTS (SELECT 1 FROM talent_resume_intake problem_duplicate"
          + " WHERE problem_duplicate.content_sha256=i.content_sha256"
          + " AND problem_duplicate.id!=i.id)))"
          + " OR (#{status}='" + ResumeIntakeProblemStatus.Codes.IDENTITY_REVIEW + "' AND i.status IN ('"
          + ResumeIntakeStatus.Codes.IDENTITY_REVIEW_REQUIRED + "','"
          + ResumeIntakeStatus.Codes.READY_FOR_CONFIRMATION + "'))"
          + " OR (#{status}='" + ResumeIntakeProblemStatus.Codes.FAILED + "' AND (i.status IN ('"
          + ResumeIntakeStatus.Codes.FAILED_RETRYABLE + "','"
          + ResumeIntakeStatus.Codes.REJECTED + "','"
          + ResumeIntakeStatus.Codes.QUARANTINED + "') OR i.score_status IN ('"
          + ResumeIntakeScoreStatus.Codes.FAILED_RETRYABLE + "','"
          + ResumeIntakeScoreStatus.Codes.FAILED_FINAL + "')))"
          + " OR (#{status}='" + ResumeIntakeProblemStatus.Codes.MISSING_JD + "' AND b.jd_version_id IS NULL)"
          + " OR (#{status} NOT IN ('" + ResumeIntakeProblemStatus.Codes.PROBLEM + "','"
          + ResumeIntakeProblemStatus.Codes.IDENTITY_REVIEW + "','"
          + ResumeIntakeProblemStatus.Codes.FAILED + "','"
          + ResumeIntakeProblemStatus.Codes.MISSING_JD + "') AND i.status=#{status}))"
          + " AND (#{importStatus} IS NULL OR "
          + "CASE WHEN i.status='" + ResumeIntakeStatus.Codes.COMPLETED
          + "' AND i.score_status='" + ResumeIntakeScoreStatus.Codes.SUCCEEDED
          + "' THEN '" + ResumeIntakeImportStatus.Codes.IMPORTED
          + "' WHEN i.status IN ('" + ResumeIntakeStatus.Codes.PARSING + "','"
          + ResumeIntakeStatus.Codes.CONFIRMED + "','" + ResumeIntakeStatus.Codes.COMPLETED
          + "') THEN '" + ResumeIntakeImportStatus.Codes.PROCESSING
          + "' ELSE '" + ResumeIntakeImportStatus.Codes.PENDING + "' END = #{importStatus}) "
          + """
         AND (#{duplicateContent} IS NULL OR
              (EXISTS (SELECT 1 FROM talent_resume_intake duplicate_intake
                       WHERE duplicate_intake.content_sha256=i.content_sha256
                         AND duplicate_intake.id!=i.id)
               OR i.failure_code='RESUME_VERSION_DUPLICATE_CONTENT')=#{duplicateContent})
         AND (#{query} IS NULL OR i.original_file_name LIKE CONCAT('%',#{query},'%')
              OR candidate.full_name LIKE CONCAT('%',#{query},'%')
              OR candidate.primary_email LIKE CONCAT('%',#{query},'%')
              OR candidate.primary_phone LIKE CONCAT('%',#{query},'%'))
         AND (#{name} IS NULL OR candidate.full_name LIKE CONCAT('%',#{name},'%'))
         AND (#{from} IS NULL OR i.create_time &gt;= #{from})
         AND (#{to} IS NULL OR i.create_time &lt; #{to})
         AND (#{scopeAll}=TRUE OR i.owner_user_id=#{actorId}
          <if test="managedUserIds != null and !managedUserIds.isEmpty()">OR i.owner_user_id IN <foreach collection="managedUserIds" item="v" open="(" separator="," close=")">#{v}</foreach></if>
          <if test="visibleDepartmentIds != null and !visibleDepartmentIds.isEmpty()">OR i.owner_department_id IN <foreach collection="visibleDepartmentIds" item="v" open="(" separator="," close=")">#{v}</foreach></if>)
      """;

  @SpecialSql("收件列表需要联查批次默认负责人并按用户和部门动态拼接可见范围")
  @Select(
      """
      <script>SELECT i.id,i.batch_id,i.source_channel_id,i.source_recruiter_user_id,
              b.default_owner_user_id,b.jd_version_id,i.owner_user_id,i.owner_department_id,i.handled_by_user_id,
              i.original_file_name,i.media_type,i.size_bytes,i.content_sha256,i.object_key,i.processing_id,
              p.status AS processing_status,p.attempt_count AS processing_attempt,
              NULLIF(JSON_UNQUOTE(JSON_EXTRACT(p.parsed_facts_json,'$.candidate.fullName')),'null') AS parsed_full_name,
              NULLIF(JSON_UNQUOTE(JSON_EXTRACT(p.parsed_facts_json,'$.candidate.email')),'null') AS parsed_email,
              NULLIF(JSON_UNQUOTE(JSON_EXTRACT(p.parsed_facts_json,'$.candidate.phone')),'null') AS parsed_phone,
              i.scan_status,i.status,i.failure_code,i.candidate_id,i.resume_version_id,i.score_run_id,i.score_status,
              i.version,
              i.ext,i.remark,i.create_id,i.create_name,i.create_time,i.update_id,i.update_name,i.update_time,
              EXISTS (SELECT 1 FROM talent_resume_intake duplicate_intake WHERE duplicate_intake.content_sha256=i.content_sha256 AND duplicate_intake.id!=i.id) AS duplicate_content
      """ + INTAKE_FILTER_SQL + """
         AND (#{cursorTime} IS NULL OR i.create_time &lt; #{cursorTime} OR (i.create_time=#{cursorTime} AND i.id &lt; #{cursorId}))
       ORDER BY i.create_time DESC,i.id DESC LIMIT #{limit}</script>
      """)
  /** 查询接收记录列表。 */
  List<ResumeIntake> findIntakes(
      String batchId,
      String status,
      String importStatus,
      Boolean duplicateContent,
      String query,
      String name,
      Instant from,
      Instant to,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> visibleDepartmentIds,
      Instant cursorTime,
      String cursorId,
      int limit);

  @SpecialSql("收件总数复用列表过滤与授权范围，不受分页游标影响")
  @Select("<script>SELECT COUNT(*) " + INTAKE_FILTER_SQL + "</script>")
  /** 统计同过滤条件及可见范围内的全部收件。 */
  long countIntakes(
      String batchId, String status, String importStatus, Boolean duplicateContent, String query, String name,
      Instant from, Instant to,
      String actorId, boolean scopeAll, Set<String> managedUserIds, Set<String> visibleDepartmentIds);

  @SpecialSql("候选列表按归属和稳定游标检索；我的简历模式额外要求当前事实与默认评分完成")
  @Select(
      "<script>SELECT"
          + " id,full_name,primary_email,primary_phone,owner_user_id,owner_department_id,status,version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time"
          + " FROM talent_candidate c WHERE status != '"
          + CandidateStatus.Codes.DELETED
          + "' <if test=\"readyOnly\">AND EXISTS (SELECT 1 FROM talent_resume r JOIN talent_resume_version v"
          + " ON v.resume_id=r.id AND v.version_no=r.current_version_no"
          + " JOIN talent_resume_intake i ON i.resume_version_id=v.id JOIN talent_intake_batch b ON b.id=i.batch_id"
          + " WHERE r.candidate_id=c.id AND i.status='" + ResumeIntakeStatus.Codes.COMPLETED
          + "' AND i.score_status='" + ResumeIntakeScoreStatus.Codes.SUCCEEDED
          + "' AND i.score_run_id IS NOT NULL AND b.jd_version_id IS NOT NULL)</if>"
          + " AND (#{query} IS NULL OR full_name LIKE CONCAT('%',#{query},'%') OR primary_email"
          + " LIKE CONCAT('%',#{query},'%') OR primary_phone LIKE CONCAT('%',#{query},'%')) AND"
          + " (#{scopeAll}=TRUE OR owner_user_id=#{actorId} <if test=\"managedUserIds != null and"
          + " !managedUserIds.isEmpty()\">OR owner_user_id IN <foreach"
          + " collection=\"managedUserIds\" item=\"v\" open=\"(\" separator=\",\""
          + " close=\")\">#{v}</foreach></if> <if test=\"visibleDepartmentIds != null and"
          + " !visibleDepartmentIds.isEmpty()\">OR owner_department_id IN <foreach"
          + " collection=\"visibleDepartmentIds\" item=\"v\" open=\"(\" separator=\",\""
          + " close=\")\">#{v}</foreach></if>) AND (#{cursorTime} IS NULL OR update_time &lt;"
          + " #{cursorTime} OR (update_time=#{cursorTime} AND id &lt; #{cursorId})) ORDER BY"
          + " update_time DESC,id DESC LIMIT #{limit}</script>")
  /** 查询候选人列表。 */
  List<Candidate> findCandidates(
      String query,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> visibleDepartmentIds,
      boolean readyOnly,
      Instant cursorTime,
      String cursorId,
      int limit);

  @SpecialSql("同名候选人判定需要标准化姓名并复用动态可见范围约束")
  @Select(
      "<script>SELECT"
          + " id,full_name,primary_email,primary_phone,owner_user_id,owner_department_id,status,version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time"
          + " FROM talent_candidate WHERE status='"
          + CandidateStatus.Codes.ACTIVE
          + "' AND LOWER(TRIM(full_name))=LOWER(TRIM(#{name})) AND (#{scopeAll}=TRUE OR"
          + " owner_user_id=#{actorId} <if test=\"managedUserIds != null and"
          + " !managedUserIds.isEmpty()\">OR owner_user_id IN <foreach"
          + " collection=\"managedUserIds\" item=\"v\" open=\"(\" separator=\",\""
          + " close=\")\">#{v}</foreach></if> <if test=\"visibleDepartmentIds != null and"
          + " !visibleDepartmentIds.isEmpty()\">OR owner_department_id IN <foreach"
          + " collection=\"visibleDepartmentIds\" item=\"v\" open=\"(\" separator=\",\""
          + " close=\")\">#{v}</foreach></if>) ORDER BY update_time DESC,id DESC LIMIT 2</script>")
  /** 按精确名称查询候选人列表。 */
  List<Candidate> findCandidatesByExactName(
      String name,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> visibleDepartmentIds);

  /** 删除指定业务记录。 */
  @SpecialSql("候选人合并前需通过自连接删除与保留候选人重复的唯一身份")
  @Delete(
      "DELETE moving FROM talent_candidate_identity moving JOIN talent_candidate_identity kept ON"
          + " kept.identity_type=moving.identity_type AND"
          + " kept.normalized_value_hash=moving.normalized_value_hash WHERE"
          + " moving.candidate_id=#{mergedId} AND kept.candidate_id=#{survivorId}")
  /** 删除重复被合并身份列表。 */
  int deleteDuplicateMergedIdentities(String survivorId, String mergedId);

  @SpecialSql("简历下一版本号需要在数据库中聚合现有最大版本")
  @Select(
      "SELECT COALESCE(MAX(version_no),0) FROM talent_resume_version WHERE resume_id=#{resumeId}")
  /** 查询最大简历版本。 */
  int findMaxResumeVersion(String resumeId);

  @SpecialSql("候选人的指定简历版本需要联查简历归属关系")
  @Select(
      "SELECT"
          + " v.id,v.resume_id,v.intake_id,v.version_no,v.original_file_name,v.media_type,v.size_bytes,v.content_sha256,v.object_key,v.status,v.version,v.ext,v.remark,v.create_id,v.create_name,v.create_time,v.update_id,v.update_name,v.update_time"
          + " FROM talent_resume_version v JOIN talent_resume r ON r.id=v.resume_id WHERE"
          + " v.id=#{resumeVersionId} AND r.candidate_id=#{candidateId}")
  /** 查询候选人简历版本。 */
  ResumeVersion findCandidateResumeVersion(String candidateId, String resumeVersionId);

  @SpecialSql("候选人简历版本列表需要联查简历归属并执行稳定游标分页")
  @Select(
      "SELECT"
          + " v.id,v.resume_id,v.intake_id,v.version_no,v.original_file_name,v.media_type,v.size_bytes,v.content_sha256,v.object_key,v.status,v.version,v.ext,v.remark,v.create_id,v.create_name,v.create_time,v.update_id,v.update_name,v.update_time"
          + " FROM talent_resume_version v JOIN talent_resume r ON r.id=v.resume_id WHERE"
          + " r.candidate_id=#{candidateId} AND (#{cursorVersionNo} IS NULL OR v.version_no <"
          + " #{cursorVersionNo} OR (v.version_no=#{cursorVersionNo} AND v.id < #{cursorId})) ORDER BY"
          + " v.version_no DESC,v.id DESC LIMIT #{limit}")
  /** 查询简历版本列表。 */
  List<ResumeVersion> findResumeVersions(
      String candidateId, Integer cursorVersionNo, String cursorId, int limit);

  /** 更新指定业务记录。 */
  @SpecialSql("合并简历时需批量迁移版本并按已有最大版本整体偏移序号")
  @Update(
      "UPDATE talent_resume_version SET"
          + " resume_id=#{survivorResumeId},version_no=version_no+#{offset} WHERE"
          + " resume_id=#{mergedResumeId}")
  int moveResumeVersions(String survivorResumeId, String mergedResumeId, int offset);

  @SpecialSql("冲突列表需要联查收件归属并按用户和部门动态约束可见范围")
  @Select(
      """
      <script>SELECT c.id,c.intake_id,c.candidate_id_a,c.candidate_id_b,c.conflict_type,c.status,c.resolution,c.chosen_candidate_id,c.reason,c.resolved_by_user_id,c.resolved_at,c.version,c.ext,c.remark,c.create_id,c.create_name,c.create_time,c.update_id,c.update_name,c.update_time
       FROM talent_identity_conflict c JOIN talent_resume_intake i ON i.id=c.intake_id
       WHERE (#{status} IS NULL OR c.status=#{status})
       AND (#{candidateId} IS NULL OR c.candidate_id_a=#{candidateId} OR c.candidate_id_b=#{candidateId})
       <if test="intakeIds != null and !intakeIds.isEmpty()">AND c.intake_id IN
         <foreach collection="intakeIds" item="v" open="(" separator="," close=")">#{v}</foreach></if>
       AND (#{scopeAll}=TRUE OR i.owner_user_id=#{actorId}
        <if test="managedUserIds != null and !managedUserIds.isEmpty()">OR i.owner_user_id IN <foreach collection="managedUserIds" item="v" open="(" separator="," close=")">#{v}</foreach></if>
        <if test="visibleDepartmentIds != null and !visibleDepartmentIds.isEmpty()">OR i.owner_department_id IN <foreach collection="visibleDepartmentIds" item="v" open="(" separator="," close=")">#{v}</foreach></if>)
       AND (#{cursorTime} IS NULL OR c.create_time &lt; #{cursorTime} OR (c.create_time=#{cursorTime} AND c.id &lt; #{cursorId}))
       ORDER BY c.create_time DESC,c.id DESC LIMIT #{limit}</script>
      """)
  /** 查询冲突列表。 */
  List<IdentityConflict> findConflicts(
      String status,
      List<String> intakeIds,
      String candidateId,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> visibleDepartmentIds,
      Instant cursorTime,
      String cursorId,
      int limit);

  @SpecialSql("幂等记录依赖复合主键和 INSERT IGNORE 保证并发首次写入")
  @Insert(
      "INSERT IGNORE INTO"
          + " talent_idempotency_record(actor_user_id,idempotency_key,action_code,request_fingerprint,target_id,create_time)"
          + " VALUES(#{actorId},#{key},#{action},#{fingerprint},#{targetId},#{now})")
  /** 新增幂等。 */
  int insertIdempotency(
      String actorId, String key, String action, String fingerprint, String targetId, Instant now);
}
