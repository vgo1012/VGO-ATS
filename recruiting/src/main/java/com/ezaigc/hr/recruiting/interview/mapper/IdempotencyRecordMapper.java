package com.ezaigc.hr.recruiting.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.recruiting.interview.domain.entity.IdempotencyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Options.FlushCachePolicy;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecord> {

  @SpecialSql("幂等预留冲突后锁定当前赢家，并绕过会话及二级缓存中的旧结果")
  @Select("""
      SELECT actor_user_id, idempotency_key, action_code, request_fingerprint, target_id,
             ext, remark, create_id, create_name, create_time, update_id, update_name, update_time, version
        FROM interview_idempotency_record
       WHERE actor_user_id = #{actorId} AND idempotency_key = #{key}
       FOR UPDATE
      """)
  @Options(useCache = false, flushCache = FlushCachePolicy.TRUE)
  IdempotencyRecord findIdempotencyForUpdate(@Param("actorId") String actorId, @Param("key") String key);
}
