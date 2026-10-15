package com.ezaigc.hr.talent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;

@Mapper
public interface CandidateMapper extends BaseMapper<Candidate> {

  @SpecialSql("候选人合并在双方行锁之后当前读所有业务历史，禁止遗留未迁移引用")
  @Select("SELECT candidate_id FROM risk_company_blacklist_record WHERE candidate_id=#{candidateId} LIMIT 1 FOR UPDATE")
  String findMergeBlacklistHistory(String candidateId);

  @SpecialSql("候选人合并在双方行锁之后当前读所有业务历史，禁止遗留未迁移引用")
  @Select("SELECT candidate_id FROM risk_company_blacklist_current WHERE candidate_id=#{candidateId} LIMIT 1 FOR UPDATE")
  String findMergeBlacklistProjection(String candidateId);

  @SpecialSql("候选人合并在双方行锁之后当前读所有业务历史，禁止遗留未迁移引用")
  @Select("SELECT candidate_id FROM candidate_education_verification_record WHERE candidate_id=#{candidateId} LIMIT 1 FOR UPDATE")
  String findMergeEducation(String candidateId);

  @SpecialSql("候选人合并在双方行锁之后当前读所有业务历史，禁止遗留未迁移引用")
  @Select("SELECT candidate_id FROM talent_resume_lifecycle WHERE candidate_id=#{candidateId} LIMIT 1 FOR UPDATE")
  String findMergeLifecycle(String candidateId);

  @SpecialSql("候选人合并在双方行锁之后当前读所有业务历史，禁止遗留未迁移引用")
  @Select("SELECT candidate_id FROM talent_resume_jd_score_binding WHERE candidate_id=#{candidateId} LIMIT 1 FOR UPDATE")
  String findMergeScoreBinding(String candidateId);
  @SpecialSql("候选人级路由锁串行化不同逻辑简历分流与归属更新")
  @Select("SELECT id FROM talent_candidate WHERE id=#{id} FOR UPDATE")
  String lockCandidate(String id);

  @SpecialSql("候选人命令锁后读取完整当前状态与归属，不使用 RR 历史快照或合并别名")
  @Select("SELECT id,full_name,primary_email,primary_phone,owner_user_id,owner_department_id,status,version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time FROM talent_candidate WHERE id=#{id} FOR UPDATE")
  Candidate findCandidateForUpdate(String id);
}
