package com.ezaigc.hr.recruiting.demand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdCodeOptionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** JD 编号维度字典持久化映射。 */
@Mapper
public interface JdCodeOptionMapper extends BaseMapper<JdCodeOptionEntity> {
  @SpecialSql("同类型名称唯一，重复新增按数据库排序规则返回已有选项")
  @Select("SELECT id, kind, code, name, version, ext, remark, create_id, create_name, create_time, update_id, update_name, update_time FROM demand_jd_code_option WHERE kind = #{kind} AND name = #{name} FOR UPDATE")
  JdCodeOptionEntity findCodeOptionByName(@Param("kind") String kind, @Param("name") String name);

  @SpecialSql("持有单例行锁时分配维度选项三位码，字典不支持删除")
  @Select("SELECT code FROM demand_jd_code_option WHERE kind = #{kind} ORDER BY code DESC LIMIT 1 FOR UPDATE")
  String lastOptionCode(String kind);

}
