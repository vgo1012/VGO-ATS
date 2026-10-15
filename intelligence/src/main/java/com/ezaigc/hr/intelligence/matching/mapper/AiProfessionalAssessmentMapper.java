package com.ezaigc.hr.intelligence.matching.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.intelligence.matching.domain.entity.AiProfessionalAssessment;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Options.FlushCachePolicy;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiProfessionalAssessmentMapper
    extends BaseMapper<AiProfessionalAssessment>, AiProfessionalAssessmentSqlOperations {

  @Override
  @SpecialSql("按与权重、人员和条件无关的唯一缓存键读取冻结 AI 专业评分")
  // 外层 NOT_SUPPORTED 作用域可能缓存首次未命中；内层事务提交领取/完成后必须强制读取最新状态。
  @Options(useCache = false, flushCache = FlushCachePolicy.TRUE)
  @Select("""
      SELECT id,cache_key,jd_fact_snapshot_id,resume_fact_snapshot_id,prompt_version,schema_version,
             provider,model,evaluated_on,status,claim_token,lease_until,result_json,input_tokens,
             output_tokens,duration_millis,error_code,deleted,ext,remark,create_id,create_name,
             create_time,update_id,update_name,update_time,version
      FROM matching_ai_professional_assessment
      WHERE cache_key=#{cacheKey} AND deleted=FALSE
      """)
  AiProfessionalAssessment findByCacheKey(String cacheKey);

  @Override
  @SpecialSql("唯一缓存键原子插入评分领取，或仅接管失败及已过期处理记录并保留原始主键")
  @Insert("""
      INSERT INTO matching_ai_professional_assessment(
        id,cache_key,jd_fact_snapshot_id,resume_fact_snapshot_id,prompt_version,schema_version,
        provider,model,evaluated_on,status,claim_token,lease_until,input_tokens,output_tokens,
        duration_millis,error_code,deleted,create_id,create_time,update_id,update_time,version)
      VALUES(#{assessment.id},#{assessment.cacheKey},#{assessment.jdFactSnapshotId},
        #{assessment.resumeFactSnapshotId},#{assessment.promptVersion},#{assessment.schemaVersion},
        #{assessment.provider},#{assessment.model},#{assessment.evaluatedOn},#{processingStatus},
        #{assessment.claimToken},#{assessment.leaseUntil},0,0,0,NULL,FALSE,#{actorId},#{now},
        #{actorId},#{now},0)
      ON DUPLICATE KEY UPDATE
        claim_token=IF(deleted=FALSE AND (status=#{failedStatus} OR
          (status=#{processingStatus} AND lease_until<=#{now})),
          VALUES(claim_token),claim_token),
        lease_until=IF(claim_token=VALUES(claim_token),VALUES(lease_until),lease_until),
        status=IF(claim_token=VALUES(claim_token),#{processingStatus},status),
        evaluated_on=IF(claim_token=VALUES(claim_token),VALUES(evaluated_on),evaluated_on),
        provider=IF(claim_token=VALUES(claim_token),VALUES(provider),provider),
        model=IF(claim_token=VALUES(claim_token),VALUES(model),model),
        error_code=IF(claim_token=VALUES(claim_token),NULL,error_code),
        update_id=IF(claim_token=VALUES(claim_token),VALUES(update_id),update_id),
        update_time=IF(claim_token=VALUES(claim_token),VALUES(update_time),update_time),
        version=IF(claim_token=VALUES(claim_token),version+1,version)
      """)
  int claim(
      AiProfessionalAssessment assessment,
      Instant now,
      String actorId,
      String processingStatus,
      String failedStatus);

  @Override
  @SpecialSql("仅当前未过期租约持有者可写入成功结果，成功后不可再领取或覆盖")
  @Update("""
      UPDATE matching_ai_professional_assessment
      SET status=#{succeededStatus},result_json=CAST(#{resultJson} AS JSON),provider=#{provider},model=#{model},
          input_tokens=#{inputTokens},output_tokens=#{outputTokens},duration_millis=#{durationMillis},
          error_code=NULL,claim_token=NULL,lease_until=NULL,update_id=#{actorId},update_time=#{now},
          version=version+1
      WHERE id=#{id} AND deleted=FALSE AND status=#{processingStatus} AND claim_token=#{claimToken}
        AND lease_until>#{now}
      """)
  int complete(
      String id,
      String claimToken,
      String resultJson,
      String provider,
      String model,
      int inputTokens,
      int outputTokens,
      long durationMillis,
      Instant now,
      String actorId,
      String succeededStatus,
      String processingStatus);

  @Override
  @SpecialSql("仅当前未过期租约持有者可记录失败，避免迟到执行覆盖后续领取")
  @Update("""
      UPDATE matching_ai_professional_assessment
      SET status=#{failedStatus},error_code=#{errorCode},claim_token=NULL,lease_until=NULL,
          update_id=#{actorId},update_time=#{now},version=version+1
      WHERE id=#{id} AND deleted=FALSE AND status=#{processingStatus} AND claim_token=#{claimToken}
        AND lease_until>#{now}
      """)
  int fail(
      String id,
      String claimToken,
      String errorCode,
      Instant now,
      String actorId,
      String failedStatus,
      String processingStatus);
}
