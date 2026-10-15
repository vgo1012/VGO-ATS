package com.ezaigc.hr.recruiting.demand.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.demand.domain.dto.JdTitleSuggestionOptionDTO;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdTitleSuggestionEntity;
import com.ezaigc.hr.recruiting.demand.mapper.JdTitleSuggestionMapper;
import com.ezaigc.hr.recruiting.demand.enums.JdStatus;
import com.ezaigc.hr.recruiting.demand.enums.JdTitleSuggestionStatus;
import com.ezaigc.hr.recruiting.demand.service.JdTitleSuggestionPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class JdTitleSuggestionPersistenceServiceImpl
    extends ServiceImpl<JdTitleSuggestionMapper, JdTitleSuggestionEntity>
    implements JdTitleSuggestionPersistenceService {
  @Override public int insertProposal(String id, String name, String actorId, Instant now) {
    return baseMapper.insertProposal(id, name, actorId, now, JdTitleSuggestionStatus.PENDING.getCode());
  }
  @Override public List<JdTitleSuggestionOptionDTO> listOptions(String actorId, String query) {
    return baseMapper.listOptions(actorId, query, JdTitleSuggestionStatus.APPROVED.getCode(), JdStatus.ACTIVE.getCode());
  }
  @Override public List<JdTitleSuggestionEntity> listReviews(String status) {
    return baseMapper.listReviews(status);
  }
  @Override public int review(String id, String status, String reason, long version, String actorId, Instant now) {
    return baseMapper.review(id, status, reason, version, actorId, now, JdTitleSuggestionStatus.PENDING.getCode());
  }
}
