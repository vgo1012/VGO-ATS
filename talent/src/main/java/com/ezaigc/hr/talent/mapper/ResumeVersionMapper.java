package com.ezaigc.hr.talent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ResumeVersionMapper extends BaseMapper<ResumeVersion> {
  @SpecialSql("简历版本行锁协调分流与招聘创建，锁由调用方短事务持有")
  @Select("SELECT id FROM talent_resume_version WHERE id=#{id} FOR UPDATE")
  String lockForRouting(String id);
}
