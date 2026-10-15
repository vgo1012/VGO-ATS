package com.ezaigc.hr.intelligence.matching.mapper;

import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRun;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 匹配运行显式 SQL 操作契约，承载联表、锁定和条件更新。
 * Typed contract for SQL that remains Mapper-owned because it is state-sensitive or
 * projection-oriented.
 */
public interface MatchRunSqlOperations {
  /** 启动运行。 */
  int startRun(String id, Instant now);

  int upsertItemOutcome(
      String runId,
      String candidateId,
      String resumeVersionId,
      String status,
      String errorCode,
      int attempt,
      Instant now);

  MatchRun findRunByBase(String baseId);

  int insertBase(
      String id,
      String cacheKey,
      String jdFactId,
      String resumeFactId,
      String planVersionId,
      String weightVersionId,
      String criterionHash,
      LocalDate evaluatedOn,
      BigDecimal professional,
      int bonus,
      BigDecimal reference,
      String mustTier,
      int bonusCap,
      String dimensions,
      String criteria,
      Instant now);

  int upsertResult(
      String id,
      String runId,
      String baseId,
      String candidateId,
      String resumeVersionId,
      BigDecimal professional,
      int bonus,
      BigDecimal reference,
      String mustTier,
      String resultJson,
      Instant now);
}
