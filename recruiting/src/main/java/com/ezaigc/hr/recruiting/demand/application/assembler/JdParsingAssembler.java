package com.ezaigc.hr.recruiting.demand.application.assembler;

import com.ezaigc.hr.recruiting.demand.domain.entity.JdFactSnapshotEntity;
import com.ezaigc.hr.recruiting.demand.domain.response.JdFactsResponse;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

@Component
public class JdParsingAssembler {

  @Resource private ObjectMapper objectMapper;

  public JdFactsResponse toResponse(JdFactSnapshotEntity snapshot) {
    return new JdFactsResponse(
        snapshot.getId(),
        snapshot.getJdVersionId(),
        snapshot.getSnapshotVersion(),
        snapshot.getSourceType(),
        snapshot.getSchemaVersion(),
        objectMapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .readTree(snapshot.getFactsJson()),
        snapshot.getCreateTime());
  }
}
