package com.ezaigc.hr.recruiting.interview.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewExternalCalendarProjection;
import com.ezaigc.hr.recruiting.interview.enums.InterviewExternalCalendarStatus;
import com.ezaigc.hr.recruiting.interview.enums.InterviewExternalCalendarAttemptStatus;
import com.ezaigc.hr.recruiting.interview.enums.InterviewExternalCalendarOperation;
import com.ezaigc.hr.recruiting.interview.mapper.InterviewExternalCalendarProjectionMapper;
import com.ezaigc.hr.recruiting.interview.service.InterviewExternalCalendarProjectionPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class InterviewExternalCalendarProjectionPersistenceServiceImpl
        extends ServiceImpl<InterviewExternalCalendarProjectionMapper, InterviewExternalCalendarProjection>
        implements InterviewExternalCalendarProjectionPersistenceService {
    @Override
    public InterviewExternalCalendarProjection findByInterviewId(String interviewId) {
        return baseMapper.selectOne(Wrappers.lambdaQuery(InterviewExternalCalendarProjection.class)
                .eq(InterviewExternalCalendarProjection::getInterviewId, interviewId));
    }

    @Override
    public InterviewExternalCalendarProjection findById(String id) {
        return baseMapper.selectById(id);
    }

    @Override
    public InterviewExternalCalendarProjection findByIdForUpdate(String id) {
        return baseMapper.selectOne(Wrappers.lambdaQuery(InterviewExternalCalendarProjection.class)
                .eq(InterviewExternalCalendarProjection::getId, id).last("FOR UPDATE"));
    }

    @Override
    public InterviewExternalCalendarProjection findByInterviewIdForUpdate(String interviewId) {
        return baseMapper.selectOne(Wrappers.lambdaQuery(InterviewExternalCalendarProjection.class)
                .eq(InterviewExternalCalendarProjection::getInterviewId, interviewId).last("FOR UPDATE"));
    }

    @Override
    public List<InterviewExternalCalendarProjection> findDue(Instant now, int limit) {
        return baseMapper.findDue(now, Math.clamp(limit, 1, 20),
                InterviewExternalCalendarStatus.PENDING.name(), InterviewExternalCalendarStatus.FAILED.name(),
                InterviewExternalCalendarStatus.PROCESSING.name(), InterviewExternalCalendarStatus.UNKNOWN.name(),
                InterviewExternalCalendarAttemptStatus.SUCCEEDED.name(), InterviewExternalCalendarAttemptStatus.FAILED.name(),
                InterviewExternalCalendarOperation.CREATE.name());
    }

    @Override
    public int claim(String id, long version, String leaseToken, Instant leaseUntil, Instant now) {
        return baseMapper.update(claimUpdate(id, version, leaseToken, leaseUntil, now));
    }

    @Override
    public int claimCreate(String id, long version, String leaseToken, Instant leaseUntil, Instant now,
            String expectedIntentId) {
        LambdaUpdateWrapper<InterviewExternalCalendarProjection> update =
                claimUpdate(id, version, leaseToken, leaseUntil, now)
                        .set(InterviewExternalCalendarProjection::getCreateIntentId, leaseToken)
                        .isNull(InterviewExternalCalendarProjection::getActiveType)
                        .in(InterviewExternalCalendarProjection::getStatus,
                                InterviewExternalCalendarStatus.PENDING.name(), InterviewExternalCalendarStatus.FAILED.name());
        if (expectedIntentId == null) update.isNull(InterviewExternalCalendarProjection::getCreateIntentId);
        else update.eq(InterviewExternalCalendarProjection::getCreateIntentId, expectedIntentId);
        return baseMapper.update(update);
    }

    private static LambdaUpdateWrapper<InterviewExternalCalendarProjection> claimUpdate(
            String id, long version, String leaseToken, Instant leaseUntil, Instant now) {
        return Wrappers.lambdaUpdate(InterviewExternalCalendarProjection.class)
                .set(InterviewExternalCalendarProjection::getStatus,
                        InterviewExternalCalendarStatus.PROCESSING.name())
                .set(InterviewExternalCalendarProjection::getLeaseToken, leaseToken)
                .set(InterviewExternalCalendarProjection::getLeaseUntil, leaseUntil)
                .set(InterviewExternalCalendarProjection::getUpdateTime, now)
                .setIncrBy(InterviewExternalCalendarProjection::getVersion, 1)
                .eq(InterviewExternalCalendarProjection::getId, id)
                .eq(InterviewExternalCalendarProjection::getVersion, version)
                .le(InterviewExternalCalendarProjection::getNextAttemptAt, now)
                .and(lease -> lease.isNull(InterviewExternalCalendarProjection::getLeaseUntil)
                        .or().le(InterviewExternalCalendarProjection::getLeaseUntil, now))
                .and(state -> state.in(InterviewExternalCalendarProjection::getStatus,
                                InterviewExternalCalendarStatus.PENDING.name(),
                                InterviewExternalCalendarStatus.FAILED.name())
                        .or(expired -> expired.eq(InterviewExternalCalendarProjection::getStatus,
                                        InterviewExternalCalendarStatus.PROCESSING.name())
                                .le(InterviewExternalCalendarProjection::getLeaseUntil, now)));
    }

}
