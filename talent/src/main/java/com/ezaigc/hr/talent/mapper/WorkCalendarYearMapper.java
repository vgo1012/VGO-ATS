package com.ezaigc.hr.talent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarYearEntity;
import org.apache.ibatis.annotations.Mapper;

/** 年度有效日历指针的实体映射。 */
@Mapper
public interface WorkCalendarYearMapper extends BaseMapper<WorkCalendarYearEntity> {}
