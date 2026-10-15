package com.ezaigc.hr.intelligence.matching.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import org.apache.ibatis.annotations.Mapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MatchPlanVersionMapper extends BaseMapper<MatchPlanVersion> {

    @SpecialSql("关联岗位选择的独立评分版本，历史运行仍使用冻结版本")
    @Select("""
            SELECT v.id,v.plan_id,v.jd_version_id,v.version_no,v.status,v.active_slot,
              v.controlled_criterion_plan_version_id,v.skills_enabled,v.role_enabled,v.years_enabled,
              v.responsibility_enabled,v.industry_project_enabled,v.version,v.configured_by_user_id,
              v.activated_by_user_id,v.activated_at,v.ext,v.remark,v.create_id,v.create_name,v.create_time,
              v.update_id,v.update_name,v.update_time
            FROM matching_jd_plan_binding b JOIN matching_jd_match_plan_version v ON v.id=b.plan_version_id
            WHERE b.id=#{jdVersionId} AND v.status=#{activeStatus}
            """)
    MatchPlanVersion activeForJd(String jdVersionId, String activeStatus);

}
