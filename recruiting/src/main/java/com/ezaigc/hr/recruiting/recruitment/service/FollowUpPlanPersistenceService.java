package com.ezaigc.hr.recruiting.recruitment.service;
import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.FollowUpPlan;
import java.time.Instant;
import java.util.List;
public interface FollowUpPlanPersistenceService extends IService<FollowUpPlan> {
  List<FollowUpPlan> findRange(Instant from, Instant to, String applicationId, String jdId, String jdVersionId, String recruitmentType, String clientId, Instant cursorTime, String cursorId, int limit);
  List<FollowUpPlan> findRange(Instant from, Instant to, String applicationId, String jdId, String jdVersionId, String recruitmentType, String clientId, String status, Instant cursorTime, String cursorId, int limit);
  List<FollowUpPlan> findScheduled(String applicationId);
}
