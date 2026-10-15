package com.ezaigc.hr.recruiting.demand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface JdVersionMapper extends BaseMapper<JdVersionEntity> {

  @SpecialSql("兼容旧 publicVisible 指纹及显式独立新增版本，按当前 JD 归属和完整业务内容去重")
  @Select("""
      SELECT v.id, v.jd_id, v.version_no, v.title, v.raw_text, v.status, v.source_type,
             v.client_id, v.recruitment_type, v.work_region_code,
             v.content_owner_user_id, v.content_fingerprint,
             v.ext, v.remark, v.create_id, v.create_name, v.create_time,
             v.update_id, v.update_name, v.update_time, v.version
      FROM demand_jd_version v
      JOIN demand_jd j ON j.id = v.jd_id
      WHERE j.owner_user_id = #{ownerUserId}
        AND j.owner_department_id = #{ownerDepartmentId}
        AND v.recruitment_type = #{recruitmentType}
        AND j.deleted = 0
        AND BINARY v.title = BINARY #{title}
        AND BINARY REPLACE(REPLACE(v.raw_text, CHAR(13, 10), CHAR(10)), CHAR(13), CHAR(10))
            = BINARY #{rawText}
        AND ((v.client_id = #{clientId}) OR (v.client_id IS NULL AND #{clientId} IS NULL))
        AND ((BINARY j.distinction = BINARY #{distinction})
             OR (j.distinction IS NULL AND #{distinction} IS NULL))
      ORDER BY v.create_time DESC, v.id DESC
      LIMIT 1
      """)
  JdVersionEntity findActiveExactVersion(
      @Param("ownerUserId") String ownerUserId,
      @Param("ownerDepartmentId") String ownerDepartmentId,
      @Param("recruitmentType") String recruitmentType,
      @Param("title") String title,
      @Param("rawText") String rawText,
      @Param("clientId") String clientId,
      @Param("distinction") String distinction,
      @Param("publicVisible") boolean ignoredPublicVisible);

  @SpecialSql("JD 墓碑锁后的当前读必须看到等待期间提交的同内容有效版本，避免并发重复重建")
  @Select("""
      SELECT v.id, v.jd_id, v.version_no, v.title, v.raw_text, v.status, v.source_type,
             v.client_id, v.recruitment_type, v.work_region_code,
             v.content_owner_user_id, v.content_fingerprint,
             v.ext, v.remark, v.create_id, v.create_name, v.create_time,
             v.update_id, v.update_name, v.update_time, v.version
      FROM demand_jd_version v
      JOIN demand_jd j ON j.id = v.jd_id
      WHERE j.owner_user_id = #{ownerUserId}
        AND j.owner_department_id = #{ownerDepartmentId}
        AND v.recruitment_type = #{recruitmentType}
        AND j.deleted = 0
        AND BINARY v.title = BINARY #{title}
        AND BINARY REPLACE(REPLACE(v.raw_text, CHAR(13, 10), CHAR(10)), CHAR(13), CHAR(10))
            = BINARY #{rawText}
        AND ((v.client_id = #{clientId}) OR (v.client_id IS NULL AND #{clientId} IS NULL))
        AND ((BINARY j.distinction = BINARY #{distinction})
             OR (j.distinction IS NULL AND #{distinction} IS NULL))
      ORDER BY v.create_time DESC, v.id DESC
      LIMIT 1
      FOR UPDATE
      """)
  JdVersionEntity findActiveExactVersionForUpdate(
      @Param("ownerUserId") String ownerUserId,
      @Param("ownerDepartmentId") String ownerDepartmentId,
      @Param("recruitmentType") String recruitmentType,
      @Param("title") String title,
      @Param("rawText") String rawText,
      @Param("clientId") String clientId,
      @Param("distinction") String distinction,
      @Param("publicVisible") boolean ignoredPublicVisible);
}
