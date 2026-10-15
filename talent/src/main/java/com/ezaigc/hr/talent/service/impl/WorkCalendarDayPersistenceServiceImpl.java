package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarDayEntity;
import com.ezaigc.hr.talent.mapper.WorkCalendarDayMapper;
import com.ezaigc.hr.talent.service.WorkCalendarDayPersistenceService;
import org.springframework.stereotype.Service;

/** 年度日历日期的薄持久化实现。 */
@Service
public class WorkCalendarDayPersistenceServiceImpl
    extends ServiceImpl<WorkCalendarDayMapper, WorkCalendarDayEntity>
    implements WorkCalendarDayPersistenceService {}
