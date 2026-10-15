package com.ezaigc.hr.recruiting.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.recruiting.activity.domain.entity.ActivityOriginEntity;
import com.ezaigc.hr.recruiting.activity.domain.dto.ActivityRecordQuery;
import com.ezaigc.hr.recruiting.activity.domain.dto.ActivityRecordRowDTO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ActivityOriginMapper extends BaseMapper<ActivityOriginEntity> {
  @SpecialSql("跨招聘事实表归并操作记录，数据库分组与稳定游标分页；应用层按面试及风险权限裁剪")
  @Select(ActivityRecordSql.SELECT)
  List<ActivityRecordRowDTO> findRecords(@Param("q") ActivityRecordQuery query);
}
