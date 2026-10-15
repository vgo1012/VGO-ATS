package com.ezaigc.hr.intelligence.matching.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.intelligence.matching.domain.entity.IdempotencyRecord;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MatchingIdempotencyMapper extends BaseMapper<IdempotencyRecord> {
  @SpecialSql("唯一键配合 INSERT IGNORE 原子保留幂等命令，避免并发重复执行")
  @Insert("INSERT IGNORE INTO matching_idempotency_record("
      + "actor_user_id,idempotency_key,action_code,request_fingerprint,target_id,create_time)"
      + " VALUES(#{actorId},#{key},#{actionCode},#{fingerprint},#{targetId},#{now})")
  int insertIgnore(
      String actorId, String key, String actionCode, String fingerprint, String targetId, Instant now);
}
