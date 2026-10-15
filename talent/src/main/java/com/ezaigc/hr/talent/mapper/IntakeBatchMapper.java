package com.ezaigc.hr.talent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.talent.domain.entity.IntakeBatch;
import com.ezaigc.hr.talent.enums.ResumeIntakeProcessingStatus;
import com.ezaigc.hr.talent.enums.ResumeIntakeScoreStatus;
import com.ezaigc.hr.talent.enums.ResumeIntakeStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IntakeBatchMapper extends BaseMapper<IntakeBatch> {

  String BATCH_PROGRESS_SELECT = """
      SELECT b.id,b.source_channel_id,b.source_recruiter_user_id,b.default_owner_user_id,
             b.owner_department_id,b.jd_version_id,b.status,b.total_count,b.succeeded_count,b.failed_count,
             b.version,b.ext,b.remark,b.create_id,b.create_name,b.create_time,b.update_id,b.update_name,b.update_time,
             (SELECT COUNT(*) FROM talent_resume_intake i WHERE i.batch_id=b.id
                AND i.status IN ('""" + ResumeIntakeStatus.Codes.PARSING + "','" + ResumeIntakeStatus.Codes.CONFIRMED + "')) AS parsing_count,\n" + """
             (SELECT COUNT(*) FROM talent_resume_intake i WHERE i.batch_id=b.id
                AND ((i.status IN ('""" + ResumeIntakeStatus.Codes.PARSING + "','" + ResumeIntakeStatus.Codes.CONFIRMED
      + "') AND (i.score_status IS NULL OR i.score_status NOT IN ('"
      + ResumeIntakeScoreStatus.Codes.FAILED_RETRYABLE + "','" + ResumeIntakeScoreStatus.Codes.FAILED_FINAL
      + "'))) OR (i.status='" + ResumeIntakeStatus.Codes.COMPLETED
      + "' AND b.jd_version_id IS NOT NULL AND (i.score_status IS NULL OR i.score_status NOT IN ('"
      + ResumeIntakeScoreStatus.Codes.SUCCEEDED + "','" + ResumeIntakeScoreStatus.Codes.FAILED_RETRYABLE
      + "','" + ResumeIntakeScoreStatus.Codes.FAILED_FINAL + "'))))) AS processing_count,\n" + """
             (SELECT COUNT(*) FROM talent_resume_intake i
                JOIN talent_resume_intake_processing p ON p.id=i.processing_id
                WHERE i.batch_id=b.id AND p.status='""" + ResumeIntakeProcessingStatus.Codes.SUCCEEDED + "') AS parsed_count,\n" + """
             (SELECT COUNT(*) FROM talent_resume_intake i WHERE i.batch_id=b.id
                AND i.status='""" + ResumeIntakeStatus.Codes.COMPLETED
      + "' AND b.jd_version_id IS NOT NULL AND i.score_status='"
      + ResumeIntakeScoreStatus.Codes.SUCCEEDED + "') AS completed_count,\n" + """
             (SELECT COUNT(*) FROM talent_resume_intake i WHERE i.batch_id=b.id
                AND i.status='""" + ResumeIntakeStatus.Codes.IGNORED + "') AS ignored_count,\n" + """
             (SELECT COUNT(*) FROM talent_resume_intake i WHERE i.batch_id=b.id
                AND i.status!='""" + ResumeIntakeStatus.Codes.IGNORED + "'"
      + " AND (i.status IN ('" + ResumeIntakeStatus.Codes.PENDING_IMPORT + "','" + ResumeIntakeStatus.Codes.IDENTITY_REVIEW_REQUIRED
      + "','" + ResumeIntakeStatus.Codes.READY_FOR_CONFIRMATION + "','" + ResumeIntakeStatus.Codes.FAILED_RETRYABLE
      + "','" + ResumeIntakeStatus.Codes.REJECTED + "','" + ResumeIntakeStatus.Codes.QUARANTINED
      + "') OR i.score_status IN ('" + ResumeIntakeScoreStatus.Codes.FAILED_RETRYABLE
      + "','" + ResumeIntakeScoreStatus.Codes.FAILED_FINAL
      + "') OR (i.status='" + ResumeIntakeStatus.Codes.COMPLETED
      + "' AND b.jd_version_id IS NULL))) AS attention_count\n" + """
      FROM talent_intake_batch b
      """;

  @SpecialSql("解析校验任务按批次默认归属范围查询，进度由已接收文件实时聚合")
  @Select("<script>" + BATCH_PROGRESS_SELECT + """
      WHERE (#{scopeAll}=TRUE OR b.default_owner_user_id=#{actorId}
        <if test="managedUserIds != null and !managedUserIds.isEmpty()">OR b.default_owner_user_id IN <foreach collection="managedUserIds" item="v" open="(" separator="," close=")">#{v}</foreach></if>)
        AND (#{cursorTime} IS NULL OR b.create_time &lt; #{cursorTime}
             OR (b.create_time=#{cursorTime} AND b.id &lt; #{cursorId}))
      ORDER BY b.create_time DESC,b.id DESC LIMIT #{limit}</script>
      """)
  List<IntakeBatch> findBatches(String actorId, boolean scopeAll, Set<String> managedUserIds,
      Instant cursorTime, String cursorId, int limit);

  @SpecialSql("单批解析进度由收件事实实时聚合，不使用仅表示上传结束的批次状态")
  @Select(BATCH_PROGRESS_SELECT + " WHERE b.id=#{id}")
  IntakeBatch findBatchWithProgress(String id);

  @SpecialSql("历史批次补关联必须以空值条件原子写入，防止并发核对造成岗位改绑")
  @Update(
      """
      UPDATE talent_intake_batch
      SET jd_version_id=#{jdVersionId}, version=version+1, update_id=#{actorId}, update_time=#{now}
      WHERE id=#{id} AND jd_version_id IS NULL
      """)
  int linkJdVersion(String id, String jdVersionId, String actorId, Instant now);
}
