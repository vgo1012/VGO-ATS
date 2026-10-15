package com.ezaigc.hr.intelligence.matching.service.impl;

import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MATCH_DETAIL_WRITE_BATCH_SIZE;
import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_SELECTED_CRITERIA;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.matching.domain.entity.CriterionEvaluation;
import com.ezaigc.hr.intelligence.matching.mapper.CriterionEvaluationMapper;
import com.ezaigc.hr.intelligence.matching.service.CriterionEvaluationPersistenceService;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 受控条件评价快照的 MyBatis-Plus 批量持久化实现。 */
@Service
public class CriterionEvaluationPersistenceServiceImpl
    extends ServiceImpl<CriterionEvaluationMapper, CriterionEvaluation>
    implements CriterionEvaluationPersistenceService {

  @Override
  public boolean insertCriterionEvaluationBatch(List<CriterionEvaluation> evaluations) {
    validateBatch(evaluations);
    return evaluations.isEmpty() || saveBatch(evaluations, MATCH_DETAIL_WRITE_BATCH_SIZE);
  }

  private static void validateBatch(List<CriterionEvaluation> evaluations) {
    if (evaluations == null
        || evaluations.size() > MAX_SELECTED_CRITERIA
        || evaluations.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("criterion evaluations batch is invalid");
    }
  }
}
