package com.ezaigc.hr.intelligence.search.application.assembler;

import com.ezaigc.hr.intelligence.search.domain.entity.RebuildRun;
import com.ezaigc.hr.intelligence.search.domain.response.RebuildRunResponse;
import org.springframework.stereotype.Component;

/** 搜索投影边界对象转换器，负责 DTO、领域对象与响应对象之间的转换。 */
@Component
public class SearchProjectionAssembler {

  public RebuildRunResponse toResponse(RebuildRun entity) {
    if (entity == null) {
      return null;
    }
    RebuildRunResponse response = new RebuildRunResponse();
    response.setId(entity.getId());
    response.setTaskId(entity.getTaskId());
    response.setTargetIndex(entity.getTargetIndex());
    response.setStatus(entity.getStatus());
    response.setProjectedCount(entity.getProjectedCount());
    response.setErrorCode(entity.getErrorCode());
    response.setRequestedByUserId(entity.getRequestedByUserId());
    response.setCreateTime(entity.getCreateTime());
    response.setStartedAt(entity.getStartedAt());
    response.setFinishedAt(entity.getFinishedAt());
    return response;
  }
}
