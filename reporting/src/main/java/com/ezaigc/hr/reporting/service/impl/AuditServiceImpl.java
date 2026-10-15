package com.ezaigc.hr.reporting.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.reporting.domain.entity.AuditEventEntity;
import com.ezaigc.hr.reporting.mapper.AuditMapper;
import com.ezaigc.hr.reporting.service.AuditService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 审计业务服务实现。
 */
@Service
public class AuditServiceImpl extends ServiceImpl<AuditMapper, AuditEventEntity>
        implements AuditService {

    @Override
    public void capture() {
        baseMapper.captureIdentity();
        baseMapper.captureDemand();
        baseMapper.captureMatching();
        baseMapper.captureTalent();
        baseMapper.captureRecruitmentStages();
        baseMapper.captureRecruitmentDecisions();
        baseMapper.captureInterviews();
        baseMapper.captureSubmissionSends();
    }

    @Override
    public List<AuditEventEntity> search(
            Instant from,
            Instant to,
            String eventType,
            String actorUserId,
            String targetType,
            String targetId,
            Instant beforeAt,
            String beforeId,
            boolean allScope,
            String currentUserId,
            List<String> managedUserIds,
            List<String> departmentIds,
            int limit) {
        return baseMapper.search(
                from,
                to,
                eventType,
                actorUserId,
                targetType,
                targetId,
                beforeAt,
                beforeId,
                allScope,
                currentUserId,
                managedUserIds,
                departmentIds,
                limit);
    }
}
