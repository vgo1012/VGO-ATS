package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarYearEntity;
import com.ezaigc.hr.talent.mapper.WorkCalendarYearMapper;
import com.ezaigc.hr.talent.service.WorkCalendarYearPersistenceService;
import org.springframework.stereotype.Service;

/** 年度有效日历指针的薄持久化实现。 */
@Service
public class WorkCalendarYearPersistenceServiceImpl
    extends ServiceImpl<WorkCalendarYearMapper, WorkCalendarYearEntity>
    implements WorkCalendarYearPersistenceService {}
