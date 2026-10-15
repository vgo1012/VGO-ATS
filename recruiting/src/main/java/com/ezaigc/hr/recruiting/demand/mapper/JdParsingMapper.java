package com.ezaigc.hr.recruiting.demand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.common.infrastructure.ai.enums.AiOutputValidationStatus;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdFactSnapshotEntity;
import com.ezaigc.hr.recruiting.demand.enums.JdAiModelRunStatus;
import com.ezaigc.hr.recruiting.demand.service.JdParsingSqlOperations;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface JdParsingMapper extends BaseMapper<JdFactSnapshotEntity>, JdParsingSqlOperations {

  /** 更新指定业务记录。 */
  @SpecialSql("模型运行完成必须以 RUNNING 为前置状态原子写入归档、用量和耗时结果")
  @Update(
      "UPDATE demand_jd_ai_model_run SET"
          + " model_provider=#{provider},model_version=#{modelVersion},status='"
          + JdAiModelRunStatus.Codes.SUCCEEDED
          + "',validation_status='"
          + AiOutputValidationStatus.Codes.VALID
          + "',raw_output_object_key=#{objectKey},raw_output_hash=#{hash},raw_output_size=#{size},encryption_version=#{encryptionVersion},input_tokens=#{inputTokens},output_tokens=#{outputTokens},duration_millis=#{durationMillis},finished_at=#{now},update_id='"
          + PlatformActorConstants.SYSTEM_ACTOR_ID
          + "',update_time=#{now}"
          + " WHERE id=#{id} AND status='"
          + JdAiModelRunStatus.Codes.RUNNING
          + "'")
  /** 完成模型运行。 */
  int completeModelRun(
      String id,
      String provider,
      String modelVersion,
      String objectKey,
      String hash,
      long size,
      String encryptionVersion,
      int inputTokens,
      int outputTokens,
      long durationMillis,
      Instant now);

  /** 更新指定业务记录。 */
  @SpecialSql("模型运行失败必须以 RUNNING 为前置状态原子写入可空模型与归档诊断字段")
  @Update(
      "UPDATE demand_jd_ai_model_run SET"
          + " model_provider=COALESCE(#{provider},model_provider),model_version=COALESCE(#{modelVersion},model_version),status='"
          + JdAiModelRunStatus.Codes.FAILED
          + "',validation_status=#{validationStatus},raw_output_object_key=#{objectKey},raw_output_hash=#{hash},raw_output_size=#{size},encryption_version=#{encryptionVersion},input_tokens=#{inputTokens},output_tokens=#{outputTokens},duration_millis=#{durationMillis},error_code=#{errorCode},finished_at=#{now},update_id='"
          + PlatformActorConstants.SYSTEM_ACTOR_ID
          + "',update_time=#{now}"
          + " WHERE id=#{id} AND status='"
          + JdAiModelRunStatus.Codes.RUNNING
          + "'")
  /** 标记失败模型运行。 */
  int failModelRun(
      String id,
      String validationStatus,
      String errorCode,
      String provider,
      String modelVersion,
      String objectKey,
      String hash,
      Long size,
      String encryptionVersion,
      int inputTokens,
      int outputTokens,
      long durationMillis,
      Instant now);

  @SpecialSql("替换当前事实快照前必须锁定当前标记行以保护唯一当前快照约束")
  @Select(
      "SELECT"
          + " id,jd_version_id,model_run_id,snapshot_version,source_type,current_marker,schema_version,facts_json,version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time"
          + " FROM demand_jd_fact_snapshot WHERE jd_version_id=#{versionId} AND current_marker='Y'"
          + " FOR UPDATE")
  /** 查询当前用于更新。 */
  JdFactSnapshotEntity findCurrentForUpdate(String versionId);
}
