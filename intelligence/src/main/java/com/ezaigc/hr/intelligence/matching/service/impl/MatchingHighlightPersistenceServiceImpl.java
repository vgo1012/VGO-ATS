package com.ezaigc.hr.intelligence.matching.service.impl;

import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MATCH_DETAIL_WRITE_BATCH_SIZE;
import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_MATCH_HIGHLIGHTS;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchingHighlight;
import com.ezaigc.hr.intelligence.matching.mapper.MatchingHighlightMapper;
import com.ezaigc.hr.intelligence.matching.service.MatchingHighlightPersistenceService;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** MatchingHighlight 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class MatchingHighlightPersistenceServiceImpl
    extends ServiceImpl<MatchingHighlightMapper, MatchingHighlight>
    implements MatchingHighlightPersistenceService {

  @Override
  public boolean insertHighlightBatch(List<MatchingHighlight> highlights) {
    validateBatch(highlights, MAX_MATCH_HIGHLIGHTS, "matching highlights");
    return highlights.isEmpty() || saveBatch(highlights, MATCH_DETAIL_WRITE_BATCH_SIZE);
  }

  private static void validateBatch(List<?> values, int maximumSize, String label) {
    if (values == null
        || values.size() > maximumSize
        || values.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException(label + " batch is invalid");
    }
  }
}
