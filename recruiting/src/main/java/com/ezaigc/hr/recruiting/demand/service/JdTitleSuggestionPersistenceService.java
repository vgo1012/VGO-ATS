package com.ezaigc.hr.recruiting.demand.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.demand.domain.dto.JdTitleSuggestionOptionDTO;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdTitleSuggestionEntity;
import java.time.Instant;
import java.util.List;

public interface JdTitleSuggestionPersistenceService extends IService<JdTitleSuggestionEntity> {
  int insertProposal(String id, String name, String actorId, Instant now);
  List<JdTitleSuggestionOptionDTO> listOptions(String actorId, String query);
  List<JdTitleSuggestionEntity> listReviews(String status);
  int review(String id, String status, String reason, long version, String actorId, Instant now);
}
