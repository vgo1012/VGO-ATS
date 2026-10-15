package com.ezaigc.hr.recruiting.recruitment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OnboardingProcess;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OnboardingProcessMapper extends BaseMapper<OnboardingProcess> {
  @SpecialSql("入职状态迁移前锁定流程行，防止实际入职确认与退回共享库并发覆盖")
  @Select("SELECT application_id,status,started_at,actual_onboarded_at,closed_at,started_by_user_id,"
      + "completed_by_user_id,version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time "
      + "FROM recruitment_onboarding_process WHERE application_id=#{applicationId} FOR UPDATE")
  OnboardingProcess lockProcess(String applicationId);
}
