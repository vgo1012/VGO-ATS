package com.ezaigc.hr.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.identity.domain.entity.ObjectGrant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ObjectGrantMapper extends BaseMapper<ObjectGrant> {
  @SpecialSql("显式授权按对象 ID 去重分页，通配权限与精确权限不重复返回同一对象")
  @Select("""
      <script>
      SELECT DISTINCT object_id FROM identity_object_grant
      WHERE user_id=#{userId} AND object_type=#{objectType}
        AND permission_code IN (#{permissionCode}, '*')
        <if test='afterObjectId != null'>AND object_id &gt; #{afterObjectId}</if>
      ORDER BY object_id LIMIT 200
      </script>
      """)
  List<String> findGrantedObjectIdPage(String userId, String objectType, String permissionCode, String afterObjectId);
}
