package com.ezaigc.hr.recruiting.interview.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewExternalCalendarAttempt;
import com.ezaigc.hr.recruiting.interview.mapper.InterviewExternalCalendarAttemptMapper;
import com.ezaigc.hr.recruiting.interview.service.InterviewExternalCalendarAttemptPersistenceService;
import org.springframework.stereotype.Service;

@Service
public class InterviewExternalCalendarAttemptPersistenceServiceImpl
        extends ServiceImpl<InterviewExternalCalendarAttemptMapper, InterviewExternalCalendarAttempt>
        implements InterviewExternalCalendarAttemptPersistenceService {
    @Override
    public InterviewExternalCalendarAttempt findForUpdate(String id) {
        return baseMapper.selectOne(Wrappers.lambdaQuery(InterviewExternalCalendarAttempt.class)
                .eq(InterviewExternalCalendarAttempt::getId, id).last("FOR UPDATE"));
    }
}
