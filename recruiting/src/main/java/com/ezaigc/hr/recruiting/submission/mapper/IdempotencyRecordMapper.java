package com.ezaigc.hr.recruiting.submission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.recruiting.submission.domain.entity.IdempotencyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;

@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecord> {
  @SpecialSql("唯一键并发冲突后使用当前读取得已提交幂等事实，避免快照读误判未完成")
  @Select("SELECT actor_user_id,idempotency_key,action_code,request_fingerprint,target_id,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time,version"
      + " FROM submission_idempotency_record WHERE actor_user_id=#{actorId} AND idempotency_key=#{key} FOR UPDATE")
  IdempotencyRecord findForUpdate(String actorId, String key);
}
