package com.ezaigc.hr.intelligence.matching.domain.dto;
import java.time.Instant;
import java.util.Set;
import lombok.Data;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunStatus;
import com.ezaigc.hr.intelligence.matching.enums.MatchDimensionStatus;
import com.ezaigc.hr.intelligence.matching.enums.CriterionMatchStatus;
import com.ezaigc.hr.intelligence.matching.enums.MatchResultSortMode;
/** 服务端确定的评分台账范围，不接受客户端传入授权信息。 */
@Data
public class MatchJobQuery {
  private final String knownDimensionStatus = MatchDimensionStatus.KNOWN.getCode();
  private final String succeededStatus = MatchRunStatus.SUCCEEDED.getCode();
  private final String partialStatus = MatchRunStatus.PARTIAL.getCode();
  private final String queuedStatus = MatchRunStatus.QUEUED.getCode();
  private final String runningStatus = MatchRunStatus.RUNNING.getCode();
  private final String passTier = CriterionMatchStatus.PASS.getCode();
  private final String unknownTier = CriterionMatchStatus.UNKNOWN.getCode();
  private final String professionalSort = MatchResultSortMode.PROFESSIONAL_SCORE.getCode();
  private final String bonusSort = MatchResultSortMode.BONUS_SCORE.getCode();
  private String actorId;
  private boolean companyScope;
  private Set<String> managedUserIds;
  private Set<String> departmentIds;
  private String jdVersionId;
  private String basisRunId;
  private Instant beforeTime;
  private String beforeId;
  private long afterRank;
  private int limit;
}
