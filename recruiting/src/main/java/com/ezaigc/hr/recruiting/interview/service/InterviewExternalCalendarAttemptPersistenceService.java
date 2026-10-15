package com.ezaigc.hr.recruiting.interview.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewExternalCalendarAttempt;

public interface InterviewExternalCalendarAttemptPersistenceService
        extends IService<InterviewExternalCalendarAttempt> {
    InterviewExternalCalendarAttempt findForUpdate(String id);
}
