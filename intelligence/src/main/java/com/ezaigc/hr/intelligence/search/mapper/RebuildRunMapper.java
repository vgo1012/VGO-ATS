package com.ezaigc.hr.intelligence.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.intelligence.search.domain.entity.RebuildRun;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RebuildRunMapper extends BaseMapper<RebuildRun> {
  @SpecialSql("原共享任务行锁后读取重建当前版本，人工历史纳管不能复用旧快照")
  @Select("SELECT id,task_id,target_index,status,projected_count,error_code,requested_by_user_id,started_at,finished_at,"
      + "version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time FROM search_index_rebuild_run WHERE id=#{id} FOR UPDATE")
  RebuildRun currentForUpdate(@Param("id") String id);
}
