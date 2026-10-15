package com.ezaigc.hr.intelligence.matching.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlan;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MatchingConfigurationMapper
    extends BaseMapper<MatchPlan>, MatchingConfigurationSqlOperations {

  @SpecialSql("权重资料删除前需要联查个人方案及历史匹配运行统计引用")
  @Select(
      """
      SELECT
        (SELECT COUNT(*) FROM matching_personal_prefer_selection_version p
          JOIN matching_weight_profile_version v ON v.id = p.weight_profile_version_id
         WHERE v.profile_id = #{profileId})
        + (SELECT COUNT(*) FROM matching_match_run r
          JOIN matching_weight_profile_version v ON v.id = r.weight_profile_version_id
         WHERE v.profile_id = #{profileId})
      """)
  /** 统计权重配置方案引用列表。 */
  int countWeightProfileReferences(String profileId);

  @SpecialSql("受控条件期望值写入 JSON 列需要数据库 CAST 校验")
  @Insert(
      """
      INSERT INTO matching_controlled_criterion_definition
        (id, plan_version_id, criterion_code, level, operator, expected_value_json,
         bonus_points, reason, evidence_policy, selectable_by_hr, sequence_no, create_time)
      VALUES (#{id}, #{planVersionId}, #{criterionCode}, #{level}, #{operator}, CAST(#{expectedValueJson} AS JSON),
              #{bonusPoints}, #{reason}, #{evidencePolicy}, #{selectableByHr}, #{sequenceNo}, #{now})
      """)
  /** 新增条件。 */
  int insertCriterion(
      String id,
      String planVersionId,
      String criterionCode,
      String level,
      String operator,
      String expectedValueJson,
      int bonusPoints,
      String reason,
      String evidencePolicy,
      boolean selectableByHr,
      int sequenceNo,
      Instant now);

  @SpecialSql("删除方案版本后需要聚合剩余最大版本号以修正父方案指针")
  @Select(
      "SELECT COALESCE(MAX(version_no), 0) FROM matching_jd_match_plan_version WHERE plan_id ="
          + " #{planId}")
  /** 查询最大剩余匹配方案版本序号。 */
  int findMaxRemainingMatchPlanVersionNo(String planId);

  @SpecialSql("个人偏好版本号需要按用户和方案版本聚合生成")
  @Select(
      """
      SELECT COALESCE(MAX(version_no), 0) FROM matching_personal_prefer_selection_version
       WHERE owner_user_id = #{ownerUserId} AND plan_version_id = #{planVersionId}
      """)
  /** 查询最大个人偏好版本。 */
  int findMaxPersonalPreferenceVersion(String ownerUserId, String planVersionId);

  @SpecialSql("个人偏好选择集合写入 JSON 列需要数据库 CAST 校验")
  @Insert(
      """
      INSERT INTO matching_personal_prefer_selection_version
        (id, owner_user_id, plan_version_id, weight_profile_version_id,
         version_no, selected_definition_ids_json, create_time)
      VALUES (#{id}, #{ownerUserId}, #{planVersionId}, #{weightProfileVersionId},
              #{versionNo}, CAST(#{selectedIdsJson} AS JSON), #{now})
      """)
  /** 新增个人偏好。 */
  int insertPersonalPreference(
      String id,
      String ownerUserId,
      String planVersionId,
      String weightProfileVersionId,
      int versionNo,
      String selectedIdsJson,
      Instant now);
}
