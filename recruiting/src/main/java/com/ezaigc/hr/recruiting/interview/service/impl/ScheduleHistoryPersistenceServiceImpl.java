package com.ezaigc.hr.recruiting.interview.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.interview.domain.entity.ScheduleHistory;
import com.ezaigc.hr.recruiting.interview.mapper.ScheduleHistoryMapper;
import com.ezaigc.hr.recruiting.interview.service.ScheduleHistoryPersistenceService;
import java.util.List;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** ScheduleHistory 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class ScheduleHistoryPersistenceServiceImpl
    extends ServiceImpl<ScheduleHistoryMapper, ScheduleHistory>
    implements ScheduleHistoryPersistenceService {

  /** 查询历史。 */
  @Override
  public List<ScheduleHistory> findHistory(String interviewId, Instant cursorTime, String cursorId, int limit) {
    PageLimits.resolve(limit, 1, PageLimits.MAXIMUM + 1);
    var query = Wrappers.lambdaQuery(ScheduleHistory.class)
            .eq(ScheduleHistory::getInterviewId, interviewId)
            .and(cursorTime != null, cursor -> cursor.lt(ScheduleHistory::getOccurredAt, cursorTime)
                .or(tie -> tie.eq(ScheduleHistory::getOccurredAt, cursorTime).lt(ScheduleHistory::getId, cursorId)))
            .orderByDesc(ScheduleHistory::getOccurredAt, ScheduleHistory::getId);
    return baseMapper.selectPage(new Page<>(1, limit, false), query).getRecords();
  }
}
