package com.ezaigc.hr.recruiting.demand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface JdEntityMapper extends BaseMapper<JdEntity> {
  @SpecialSql("所有 JD 编号与维度字典分配共用单例行锁，由应用事务持有")
  @Select("SELECT `last_value` FROM demand_jd_code_sequence WHERE id = 1 FOR UPDATE")
  Long lockCodeAllocation();

  @SpecialSql("检查已使用编号时保留逻辑删除记录，防止新旧编号格式相互冲突")
  @Select("SELECT id FROM demand_jd WHERE code = #{code} FOR UPDATE")
  String findUsedCodeForUpdate(String code);

  @SpecialSql("持有全局编号锁后读取部门最新已分配码，避免事务旧快照重复分配")
  @Select("SELECT code FROM demand_jd_department_code WHERE department_id = #{departmentId} FOR UPDATE")
  String findDepartmentCode(String departmentId);

  @SpecialSql("持有全局编号锁后读取最新部门码上限")
  @Select("SELECT code FROM demand_jd_department_code ORDER BY code DESC LIMIT 1 FOR UPDATE")
  String lastDepartmentCode();

  @SpecialSql("持有全局编号锁后为部门保存永久码，唯一约束阻止复用")
  @Insert("INSERT INTO demand_jd_department_code (department_id, code) VALUES (#{departmentId}, #{code})")
  int insertDepartmentCode(@Param("departmentId") String departmentId, @Param("code") String code);

  @SpecialSql("在全局编号锁下初始化部门、岗位类型和区域组合流水，已有六位组合保持不变")
  @Insert("""
      INSERT INTO demand_jd_code_pair_sequence (id, `last_value`)
      VALUES (#{pair}, 0) ON DUPLICATE KEY UPDATE `last_value` = `last_value`
      """)
  int initializePairSequence(String pair);

  @SpecialSql("组合流水在应用事务内递增，禁止回退或随 JD 删除回收")
  @Update("UPDATE demand_jd_code_pair_sequence SET `last_value` = `last_value` + 1 WHERE id = #{pair} AND `last_value` < 9223372036854775807")
  int advancePairSequence(String pair);

  @SpecialSql("在持有编号锁的事务中读取本次组合流水")
  @Select("SELECT `last_value` FROM demand_jd_code_pair_sequence WHERE id = #{pair}")
  long currentPairSequence(String pair);

}
