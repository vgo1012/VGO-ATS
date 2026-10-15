package com.ezaigc.hr.intelligence.matching.domain.response;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobBasisRow;
import java.time.Instant;
import lombok.Data;
import lombok.AllArgsConstructor;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
/** basisRunId 是可查看原始执行记录的代表运行，不代替原有运行 ID。 */
@Data
@AllArgsConstructor
public class MatchJobBasisResponse {
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
  private List<String> selectedCriterionIds;
  private JsonNode personalScreeningSettings;

  public static MatchJobBasisResponse from(MatchJobBasisRow v, ObjectMapper json) {
    return new MatchJobBasisResponse(v.getBasisRunId(), v.getJdVersionId(), v.getBasisKey(),
        v.getPlanVersionId(), v.getWeightProfileVersionId(), v.getSortMode(), v.getLastRunAt(),
        v.getRunCount(), v.getCandidateCount(), v.getScoredCandidateCount(),
        v.getSkillsWeight(), v.getRoleWeight(), v.getYearsWeight(), v.getResponsibilityWeight(),
        v.getIndustryProjectWeight(), v.getSelectedCriterionIdsJson() == null ? List.of()
            : json.readValue(v.getSelectedCriterionIdsJson(), new TypeReference<List<String>>() {}),
        v.getPersonalScreeningSettingsJson() == null ? null : json.readTree(v.getPersonalScreeningSettingsJson()));
  }
}
