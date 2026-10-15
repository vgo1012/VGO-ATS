package com.ezaigc.hr.intelligence.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.intelligence.search.domain.entity.SearchIndexResource;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SearchIndexResourceMapper extends BaseMapper<SearchIndexResource> {
  String COLUMNS = "id,index_name,scope_name,resource_kind,run_id,task_id,attempt_count,status,managed,"
      + "ready_at,pinned_until,retired_at,reconciliation_reason,reconciled_by,reconciled_at,original_request_terminated,"
      + "version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time";

  @SpecialSql("短事务锁住精确资源，与批准行同顺序串行化 pin、创建和持久退役")
  @Select("SELECT " + COLUMNS + " FROM search_index_resource WHERE index_name=#{name} FOR UPDATE")
  SearchIndexResource forUpdate(@Param("name") String name);

  @SpecialSql("批准行锁后当前读活体ID；最多101条足以拒绝政策上限100，不能复用旧RR快照")
  @Select("SELECT id FROM search_index_resource WHERE status <> #{deleted} ORDER BY status,id LIMIT 101 FOR UPDATE")
  List<String> liveCount(@Param("deleted") String deleted);

  @SpecialSql("当前读未决ID；最多11条足以拒绝政策上限10，不因旧RR快照突破创建容量")
  @Select("SELECT id FROM search_index_resource WHERE status IN (#{creating},#{unknown}) ORDER BY status,id LIMIT 11 FOR UPDATE")
  List<String> unresolvedCount(@Param("creating") String creating, @Param("unknown") String unknown);

  @SpecialSql("数据库时钟保守只增 pin，保留资源技术版本；必须已持有批准行和资源行锁")
  @Update("UPDATE search_index_resource SET pinned_until=GREATEST(COALESCE(pinned_until,CURRENT_TIMESTAMP(6)),"
      + "TIMESTAMPADD(MICROSECOND,#{micros},CURRENT_TIMESTAMP(6))) WHERE id=#{id} AND status=#{ready}")
  int pin(@Param("id") String id, @Param("micros") long micros, @Param("ready") String ready);

  @SpecialSql("持久删除意图同时验证 pin 已按数据库时钟过期，关闭 pin→delete 竞态")
  @Update("UPDATE search_index_resource SET status=#{retiring},retired_at=CURRENT_TIMESTAMP(6),"
      + "update_id=#{actor},update_time=CURRENT_TIMESTAMP(6),version=version+1 WHERE id=#{id} "
      + "AND version=#{version} AND managed=1 AND status=#{ready} "
      + "AND (pinned_until IS NULL OR pinned_until<=CURRENT_TIMESTAMP(6))")
  int retiring(@Param("id") String id, @Param("version") long version, @Param("actor") String actor, @Param("ready") String ready, @Param("retiring") String retiring);

  @SpecialSql("只扫描有界精确资源候选；READY年龄由数据库时钟计算，RETIRING可幂等恢复")
  @Select("SELECT " + COLUMNS + " FROM search_index_resource WHERE managed=1 AND (status=#{retiring} "
      + "OR (status=#{ready} AND ready_at<=TIMESTAMPADD(SECOND,-#{ageSeconds},CURRENT_TIMESTAMP(6)))) "
      + "ORDER BY create_time ASC,id ASC LIMIT #{limit}")
  List<SearchIndexResource> candidates(@Param("ageSeconds") long ageSeconds, @Param("limit") int limit, @Param("ready") String ready, @Param("retiring") String retiring);

  @SpecialSql("数量保留策略读取有限最新READY列表，保护最近已完成资源")
  @Select("SELECT index_name FROM search_index_resource WHERE status=#{ready} ORDER BY ready_at DESC,id DESC LIMIT #{limit}")
  List<String> newest(@Param("limit") int limit, @Param("ready") String ready);
}
