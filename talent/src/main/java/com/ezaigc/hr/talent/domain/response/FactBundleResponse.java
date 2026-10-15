package com.ezaigc.hr.talent.domain.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FactBundleResponse {
  /** 快照。 */
  private FactSnapshotResponse snapshot;
  /** 工作经历列表。 */
  private List<WorkFactResponse> workExperiences;
  /** 项目经历列表。 */
  private List<ProjectFactResponse> projectExperiences;
  /** 教育经历列表。 */
  private List<EducationFactResponse> educations;
  /** 技能。 */
  private List<SkillFactResponse> skills;
  private List<SimpleFactResponse> simpleFacts;
}
