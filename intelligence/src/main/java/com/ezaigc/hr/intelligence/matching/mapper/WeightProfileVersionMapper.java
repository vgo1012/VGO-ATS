package com.ezaigc.hr.intelligence.matching.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileVersion;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WeightProfileVersionMapper extends BaseMapper<WeightProfileVersion> {

  @SpecialSql("管理列表按方案当前版本号批量加载，避免逐行查询")
  @Select("""
      <script>
      SELECT v.id, v.profile_id, v.version_no, v.skills_weight, v.role_weight, v.years_weight,
             v.responsibility_weight, v.industry_project_weight, v.screening_settings_json,
             v.ext, v.remark, v.create_id, v.create_name, v.create_time,
             v.update_id, v.update_name, v.update_time, v.version
        FROM matching_weight_profile_version v
        JOIN matching_weight_profile p
          ON p.id = v.profile_id AND p.current_version_no = v.version_no
       WHERE v.profile_id IN
       <foreach collection="profileIds" item="profileId" open="(" separator="," close=")">
         #{profileId}
       </foreach>
      </script>
      """)
  List<WeightProfileVersion> findCurrentVersions(
      @Param("profileIds") Set<String> profileIds);
}
