package com.ezaigc.hr.intelligence.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.intelligence.search.domain.entity.ProjectionCheckpoint;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectionCheckpointMapper extends BaseMapper<ProjectionCheckpoint> {

  @SpecialSql("通过 INSERT IGNORE 原子化并发初始化投影检查点")
  @Insert(
      "INSERT IGNORE INTO search_projection_checkpoint("
          + "projection_name,index_name,last_created_at,last_snapshot_id,projected_count,version,create_id,create_time,update_id,update_time)"
          + " VALUES(#{projectionName},#{indexName},#{lastCreatedAt},#{lastSnapshotId},#{projectedCount},#{version},"
          + "#{createId},#{createTime},#{updateId},#{updateTime})")
  /** 新增忽略。 */
  int insertIgnore(ProjectionCheckpoint checkpoint);

  /** 短事务当前读，避免复用调用者的历史快照；返回后释放共享锁再执行搜索 I/O。 */
  @SpecialSql("批准指针必须使用共享锁当前读，禁止复用调用者的旧事务快照")
  @Select("SELECT index_name,version FROM search_projection_checkpoint WHERE projection_name=#{name} FOR SHARE")
  @ConstructorArgs({@Arg(column = "index_name", javaType = String.class),
      @Arg(column = "version", javaType = long.class)})
  SearchIndexTarget approvedCurrent(@Param("name") String name);

  /** 批准事务中的排他当前读，跨实例串行化批准 CAS。 */
  @SpecialSql("批准索引短事务排他锁定独立权威行，串行化跨实例代际 CAS")
  @Select("SELECT index_name,version FROM search_projection_checkpoint WHERE projection_name=#{name} FOR UPDATE")
  @ConstructorArgs({@Arg(column = "index_name", javaType = String.class),
      @Arg(column = "version", javaType = long.class)})
  SearchIndexTarget approvedForUpdate(@Param("name") String name);
}
