package com.ezaigc.hr.recruiting.recruitment.service.impl;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.FollowUpPlan;
import com.ezaigc.hr.recruiting.recruitment.mapper.FollowUpPlanMapper;
import com.ezaigc.hr.recruiting.recruitment.service.FollowUpPlanPersistenceService;
import org.springframework.stereotype.Service;
import com.ezaigc.hr.recruiting.recruitment.enums.FollowUpPlanStatus;
import java.time.Instant;
import java.util.List;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
@Service
public class FollowUpPlanPersistenceServiceImpl extends ServiceImpl<FollowUpPlanMapper, FollowUpPlan>
    implements FollowUpPlanPersistenceService {
  @Override public List<FollowUpPlan> findRange(Instant from, Instant to, String applicationId, String jdId, String jdVersionId, String recruitmentType, String clientId, Instant cursorTime, String cursorId, int limit) {
    return findRange(from, to, applicationId, jdId, jdVersionId, recruitmentType, clientId, null, cursorTime, cursorId, limit);
  }
  @Override public List<FollowUpPlan> findRange(Instant from, Instant to, String applicationId, String jdId, String jdVersionId, String recruitmentType, String clientId, String status, Instant cursorTime, String cursorId, int limit) {
    return baseMapper.findRange(from, to, applicationId, jdId, jdVersionId, recruitmentType, clientId, status, cursorTime, cursorId, limit);
  }
  @Override public List<FollowUpPlan> findScheduled(String applicationId) {
    return list(Wrappers.lambdaQuery(FollowUpPlan.class).eq(FollowUpPlan::getApplicationId, applicationId)
        .eq(FollowUpPlan::getStatus, FollowUpPlanStatus.SCHEDULED.getCode()).orderByAsc(FollowUpPlan::getId));
  }
}
