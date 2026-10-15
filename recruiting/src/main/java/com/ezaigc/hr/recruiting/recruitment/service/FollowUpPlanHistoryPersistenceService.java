package com.ezaigc.hr.recruiting.recruitment.service;
import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.FollowUpPlanHistory;
import java.time.Instant;
import java.util.List;
public interface FollowUpPlanHistoryPersistenceService extends IService<FollowUpPlanHistory> {
  List<FollowUpPlanHistory> findHistory(String applicationId, Instant cursorTime, String cursorId, int limit);
}
