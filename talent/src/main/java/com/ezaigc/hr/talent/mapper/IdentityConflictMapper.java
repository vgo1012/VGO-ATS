package com.ezaigc.hr.talent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.talent.domain.entity.IdentityConflict;
import com.ezaigc.hr.talent.domain.entity.CandidateConflictCountRow;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Options;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;

@Mapper
public interface IdentityConflictMapper extends BaseMapper<IdentityConflict> {
  @SpecialSql("冲突落证先共享锁定外键候选人父记录，与确认及合并保持候选人→收件锁序")
  @Select("SELECT id FROM talent_candidate WHERE id=#{candidateId} FOR SHARE")
  @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
  String lockCandidateForConflict(String candidateId);

  @SpecialSql("确认与身份冲突落证共用收件行锁，串行检查版本、状态及已有冲突")
  @Select("""
      SELECT id,batch_id,source_channel_id,source_recruiter_user_id,owner_user_id,owner_department_id,
             handled_by_user_id,original_file_name,media_type,size_bytes,content_sha256,object_key,
             processing_id,scan_status,status,failure_code,candidate_id,resume_version_id,score_run_id,
             score_status,version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time
        FROM talent_resume_intake WHERE id=#{intakeId} FOR UPDATE
      """)
  @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
  ResumeIntake lockIntake(String intakeId);

  @SpecialSql("人才池冲突标注批量统计候选人两侧，并复用收件可见范围防止泄露")
  @Select("""
      <script>
      SELECT linked.candidate_id, COUNT(DISTINCT linked.conflict_id) AS open_identity_conflict_count
      FROM (
        SELECT c.id AS conflict_id, c.candidate_id_a AS candidate_id, c.intake_id
          FROM talent_identity_conflict c WHERE c.status=#{openStatus} AND c.candidate_id_a IN
          <foreach collection="candidateIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        UNION ALL
        SELECT c.id AS conflict_id, c.candidate_id_b AS candidate_id, c.intake_id
          FROM talent_identity_conflict c WHERE c.status=#{openStatus} AND c.candidate_id_b IN
          <foreach collection="candidateIds" item="id" open="(" separator="," close=")">#{id}</foreach>
      ) linked
      JOIN talent_resume_intake i ON i.id=linked.intake_id
      WHERE (#{scopeAll}=TRUE OR i.owner_user_id=#{actorId}
        <if test="managedUserIds != null and !managedUserIds.isEmpty()">OR i.owner_user_id IN
          <foreach collection="managedUserIds" item="v" open="(" separator="," close=")">#{v}</foreach></if>
        <if test="visibleDepartmentIds != null and !visibleDepartmentIds.isEmpty()">OR i.owner_department_id IN
          <foreach collection="visibleDepartmentIds" item="v" open="(" separator="," close=")">#{v}</foreach></if>)
      GROUP BY linked.candidate_id
      </script>
      """)
  List<CandidateConflictCountRow> countOpenByCandidateIds(
      Collection<String> candidateIds, String openStatus, String actorId, boolean scopeAll,
      Set<String> managedUserIds, Set<String> visibleDepartmentIds);
}
