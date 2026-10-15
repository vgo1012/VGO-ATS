package com.ezaigc.hr.recruiting.interview.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.interview.domain.entity.ScheduleHistory;
import java.util.List;
import java.time.Instant;

/** ScheduleHistory 实体的持久化服务。 */
public interface ScheduleHistoryPersistenceService extends IService<ScheduleHistory> {

  /** 查询历史。 */
  List<ScheduleHistory> findHistory(String interviewId, Instant cursorTime, String cursorId, int limit);
}
