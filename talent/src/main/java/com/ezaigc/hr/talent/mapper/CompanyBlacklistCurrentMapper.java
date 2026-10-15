package com.ezaigc.hr.talent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.talent.domain.entity.CompanyBlacklistCurrent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CompanyBlacklistCurrentMapper extends BaseMapper<CompanyBlacklistCurrent> {
  @SpecialSql("候选人行锁串行化风险写入和招聘推进，投影使用当前读避免 RR 旧快照")
  @Select("SELECT candidate_id,record_id,active,version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time FROM risk_company_blacklist_current WHERE candidate_id=#{candidateId} FOR UPDATE")
  CompanyBlacklistCurrent lockCurrent(String candidateId);
}
