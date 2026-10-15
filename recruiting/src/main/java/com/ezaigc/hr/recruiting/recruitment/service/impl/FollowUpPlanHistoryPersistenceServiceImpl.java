package com.ezaigc.hr.recruiting.recruitment.service.impl;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.FollowUpPlanHistory;
import com.ezaigc.hr.recruiting.recruitment.mapper.FollowUpPlanHistoryMapper;
import com.ezaigc.hr.recruiting.recruitment.service.FollowUpPlanHistoryPersistenceService;
import org.springframework.stereotype.Service;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import java.time.Instant;
import java.util.List;
@Service
public class FollowUpPlanHistoryPersistenceServiceImpl extends ServiceImpl<FollowUpPlanHistoryMapper, FollowUpPlanHistory>
    implements FollowUpPlanHistoryPersistenceService {
  @Override
  public List<FollowUpPlanHistory> findHistory(String applicationId, Instant cursorTime, String cursorId, int limit) {
    PageLimits.resolve(limit, 1, PageLimits.MAXIMUM + 1);
    return baseMapper.findHistory(applicationId, cursorTime, cursorId, limit);
  }
}
