package com.ezaigc.hr.intelligence.matching.domain.entity;
import java.time.Instant;
import lombok.Data;
/** 相同冻结依据的可比较评分分组。 */
@Data
public class MatchJobBasisRow {
  private String basisRunId;
  private String jdVersionId;
  private String basisKey;
  private String planVersionId;
  private String weightProfileVersionId;
  private String sortMode;
  private Instant lastRunAt;
  private long runCount;
  private long candidateCount;
  private long scoredCandidateCount;
  private int skillsWeight;
  private int roleWeight;
  private int yearsWeight;
  private int responsibilityWeight;
  private int industryProjectWeight;
  private String selectedCriterionIdsJson;
  private String personalScreeningSettingsJson;
}
