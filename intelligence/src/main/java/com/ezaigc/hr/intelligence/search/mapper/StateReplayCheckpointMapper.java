package com.ezaigc.hr.intelligence.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.intelligence.search.domain.entity.StateReplayCheckpoint;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StateReplayCheckpointMapper extends BaseMapper<StateReplayCheckpoint> {
  @SpecialSql("并发初始化当前状态回放进度，不覆盖其他进程已提交的游标")
  @Insert("INSERT IGNORE INTO search_state_replay_checkpoint"
      + "(projection_name,last_candidate_id,create_id,create_time,update_id,update_time)"
      + " VALUES(#{projectionName},#{lastCandidateId},#{createId},#{createTime},#{updateId},#{updateTime})")
  int insertIgnore(StateReplayCheckpoint checkpoint);
}
