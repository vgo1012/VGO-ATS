package com.ezaigc.hr.talent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.talent.domain.dto.ResumeProjectionCursorDTO;
import com.ezaigc.hr.talent.domain.dto.ResumeSearchStateRow;
import com.ezaigc.hr.talent.domain.dto.SearchFactContactDTO;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.entity.ResumeResourceEntity;
import com.ezaigc.hr.talent.enums.CandidateStatus;
import com.ezaigc.hr.talent.enums.ResumeVersionStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ResumeParsingMapper extends BaseMapper<FactSnapshot>, ResumeParsingSqlOperations {

  String CURRENT_SEARCH_STATE_SELECT = "SELECT c.id candidate_id,c.status candidate_status,c.version candidate_version,c.full_name,"
      + "c.primary_email email,c.primary_phone phone,v.id resume_version_id,"
      + "COALESCE(v.version_no,0) resume_version_no,s.id fact_snapshot_id,"
      + "GREATEST(c.update_time,COALESCE(s.create_time,v.create_time,c.create_time)) update_time,"
      + "ch.code source_channel"
      + " FROM talent_candidate c LEFT JOIN talent_resume r ON r.candidate_id=c.id AND c.status='"
      + CandidateStatus.Codes.ACTIVE + "' LEFT JOIN talent_resume_version v ON v.resume_id=r.id AND v.status='"
      + ResumeVersionStatus.Codes.AVAILABLE + "' AND NOT EXISTS (SELECT 1 FROM talent_resume_version newer"
      + " WHERE newer.resume_id=v.resume_id AND newer.status='" + ResumeVersionStatus.Codes.AVAILABLE
      + "' AND newer.version_no>v.version_no)"
      + " LEFT JOIN talent_resume_fact_snapshot s ON s.resume_version_id=v.id AND s.current_marker='Y'"
      + " LEFT JOIN talent_resume_intake i ON i.id=v.intake_id"
      + " LEFT JOIN talent_source_channel ch ON ch.id=i.source_channel_id";

  @SpecialSql("候选人当前搜索状态包含删除墓碑与无事实的最新可用简历，不能内连接而遗漏终态")
  @Select(CURRENT_SEARCH_STATE_SELECT + " WHERE c.id=#{candidateId}")
  ResumeSearchStateRow findCurrentSearchState(String candidateId);

  @SpecialSql("按候选人主键有界回放最新状态，包含无事实及删除记录，不使用事实时间作为完整性游标")
  @Select(CURRENT_SEARCH_STATE_SELECT + " WHERE c.id>#{afterCandidateId} ORDER BY c.id LIMIT #{limit}")
  List<ResumeSearchStateRow> findCurrentSearchStatesAfter(
      @Param("afterCandidateId") String afterCandidateId, @Param("limit") int limit);

  @SpecialSql("解析资源需要联查简历版本、简历与候选人并校验两级可用状态")
  @Select(
      "SELECT v.id"
          + " resume_version_id,v.original_file_name,v.media_type,v.content_sha256,v.object_key,c.id"
          + " candidate_id,c.owner_user_id,c.owner_department_id FROM talent_resume_version v JOIN"
          + " talent_resume r ON r.id=v.resume_id JOIN talent_candidate c ON c.id=r.candidate_id"
          + " WHERE v.id=#{versionId} AND v.status='"
          + ResumeVersionStatus.Codes.AVAILABLE
          + "' AND c.status='"
          + CandidateStatus.Codes.ACTIVE
          + "'")
  /** 查询简历资源。 */
  ResumeResourceEntity findResumeResource(String versionId);

  @SpecialSql("候选人与版本已按路由顺序加锁后，当前读归属并拒绝跨候选人迁移")
  @Select(
      "SELECT v.id resume_version_id,v.original_file_name,v.media_type,v.content_sha256,"
          + "v.object_key,c.id candidate_id,c.owner_user_id,c.owner_department_id"
          + " FROM talent_resume_version v JOIN talent_resume r ON r.id=v.resume_id"
          + " JOIN talent_candidate c ON c.id=r.candidate_id"
          + " WHERE v.id=#{versionId} AND c.id=#{candidateId} AND v.status='"
          + ResumeVersionStatus.Codes.AVAILABLE + "' AND c.status='"
          + CandidateStatus.Codes.ACTIVE + "' FOR UPDATE")
  ResumeResourceEntity findResumeResourceForUpdate(
      @Param("versionId") String versionId, @Param("candidateId") String candidateId);

  @SpecialSql("批量解析资源需要联查简历版本、简历与候选人并校验两级可用状态")
  @Select({
    "<script>",
    "SELECT v.id resume_version_id,v.original_file_name,v.media_type,v.content_sha256,"
        + "v.object_key,c.id candidate_id,c.owner_user_id,c.owner_department_id",
    "FROM talent_resume_version v",
    "JOIN talent_resume r ON r.id=v.resume_id",
    "JOIN talent_candidate c ON c.id=r.candidate_id",
    "WHERE v.status='" + ResumeVersionStatus.Codes.AVAILABLE + "'",
    "AND c.status='" + CandidateStatus.Codes.ACTIVE + "'",
    "AND v.id IN",
    "<foreach collection='versionIds' item='versionId' open='(' separator=',' close=')'>",
    "#{versionId}",
    "</foreach>",
    "</script>"
  })
  /** 批量查询可用简历资源；ID 通过 MyBatis 参数绑定，禁止拼接用户输入。 */
  List<ResumeResourceEntity> findResumeResources(
      @Param("versionIds") Collection<String> versionIds);

  @SpecialSql("事实纠正必须对当前快照行加锁以串行化版本号和当前标记切换")
  @Select(
      "SELECT"
          + " id,resume_version_id,parse_snapshot_id,previous_snapshot_id,snapshot_version,source_type,current_marker,full_name,email,phone,current_role,city,age,gender,job_intention,corrected_by_user_id,correction_reason,version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time"
          + " FROM talent_resume_fact_snapshot WHERE resume_version_id=#{versionId} AND"
          + " current_marker='Y' FOR UPDATE")
  /** 查询当前事实快照用于更新。 */
  FactSnapshot findCurrentFactSnapshotForUpdate(String versionId);

  @SpecialSql("搜索投影增量游标需要联查版本、收件渠道、简历和有效候选人")
  @Select(
      "SELECT s.id,s.create_time,ch.code AS source_channel,v.version_no AS resume_version_no,"
          + "c.version candidate_version,c.full_name,c.primary_email email,c.primary_phone phone"
          + " FROM talent_resume_fact_snapshot s"
          + " JOIN talent_resume_version v ON v.id=s.resume_version_id AND v.status='"
          + ResumeVersionStatus.Codes.AVAILABLE
          + "' JOIN talent_resume_intake i ON i.id=v.intake_id"
          + " JOIN talent_source_channel ch ON ch.id=i.source_channel_id"
          + " JOIN talent_resume r ON r.id=v.resume_id"
          + " JOIN talent_candidate c ON c.id=r.candidate_id AND c.status='"
          + CandidateStatus.Codes.ACTIVE
          + "' WHERE s.current_marker='Y'"
          + " AND NOT EXISTS (SELECT 1 FROM talent_resume_version newer"
          + " WHERE newer.resume_id=v.resume_id AND newer.status='" + ResumeVersionStatus.Codes.AVAILABLE + "'"
          + " AND newer.version_no>v.version_no)"
          + " AND (s.create_time>#{afterCreatedAt} OR (s.create_time=#{afterCreatedAt} AND s.id>#{afterSnapshotId}))"
          + " ORDER BY s.create_time,s.id LIMIT #{limit}")
  /** 查询当前事实快照列表变更后。 */
  List<ResumeProjectionCursorDTO> findCurrentFactSnapshotsAfter(
      Instant afterCreatedAt, String afterSnapshotId, int limit);

  @SpecialSql("搜索命中按候选人有效状态、最新可用简历和当前快照回查，不能返回旧版本联系方式")
  @Select({
    "<script>",
    "SELECT s.id,s.resume_version_id,c.primary_email email,c.primary_phone phone FROM talent_resume_fact_snapshot s",
    "JOIN talent_resume_version v ON v.id=s.resume_version_id AND v.status='" + ResumeVersionStatus.Codes.AVAILABLE + "'",
    "JOIN talent_resume r ON r.id=v.resume_id",
    "JOIN talent_candidate c ON c.id=r.candidate_id AND c.status='" + CandidateStatus.Codes.ACTIVE + "'",
    "WHERE s.current_marker='Y' AND NOT EXISTS (SELECT 1 FROM talent_resume_version newer",
    "WHERE newer.resume_id=v.resume_id AND newer.status='" + ResumeVersionStatus.Codes.AVAILABLE + "' AND newer.version_no>v.version_no)",
    "AND v.id IN",
    "<foreach collection='versionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
    "</script>"
  })
  List<SearchFactContactDTO> findLatestAvailableFactContacts(@Param("versionIds") Collection<String> versionIds);

  @SpecialSql("事实纠正的受影响能力集合写入 JSON 列需要显式 CAST")
  @Insert(
      "INSERT INTO"
          + " talent_fact_correction(id,resume_version_id,previous_fact_snapshot_id,new_fact_snapshot_id,corrected_by_user_id,reason,affected_capabilities_json,create_time)"
          + " VALUES(#{id},#{versionId},#{previousId},#{newId},#{actorId},#{reason},CAST(#{capabilities}"
          + " AS JSON),#{now})")
  /** 新增更正。 */
  int insertCorrection(
      String id,
      String versionId,
      String previousId,
      String newId,
      String actorId,
      String reason,
      String capabilities,
      Instant now);

}
