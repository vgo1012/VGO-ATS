package com.ezaigc.hr.recruiting.interview.mapper;

import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewExternalCalendarProjection;
import java.time.Instant;
import java.util.List;

public interface InterviewExternalCalendarProjectionSqlOperations {
    List<InterviewExternalCalendarProjection> findDue(Instant now, int limit,
            String pending, String failed, String processing, String unknown,
            String succeededAttempt, String failedAttempt, String createOperation);
}
