package com.ezaigc.hr.recruiting.interview.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewExternalCalendarProjection;
import java.time.Instant;
import java.util.List;

public interface InterviewExternalCalendarProjectionPersistenceService
        extends IService<InterviewExternalCalendarProjection> {
    InterviewExternalCalendarProjection findByInterviewId(String interviewId);
    InterviewExternalCalendarProjection findById(String id);
    InterviewExternalCalendarProjection findByIdForUpdate(String id);
    InterviewExternalCalendarProjection findByInterviewIdForUpdate(String interviewId);
    List<InterviewExternalCalendarProjection> findDue(Instant now, int limit);
    int claim(String id, long version, String leaseToken, Instant leaseUntil, Instant now);
    int claimCreate(String id, long version, String leaseToken, Instant leaseUntil, Instant now,
            String expectedIntentId);
}
