package com.ezaigc.hr.talent.domain.entity;

import java.util.List;
import lombok.Data;

@Data
public class FactBundle {
  /** 快照。 */
  private FactSnapshot snapshot;
  /** 工作经历列表。 */
  private List<WorkFact> workExperiences;
  /** 项目经历列表。 */
  private List<ProjectFact> projectExperiences;
  /** 教育经历列表。 */
  private List<EducationFact> educations;
  /** 技能。 */
  private List<SkillFact> skills;
  private List<SimpleFact> simpleFacts;

  public FactBundle(
      FactSnapshot snapshot,
      List<WorkFact> workExperiences,
      List<ProjectFact> projectExperiences,
      List<EducationFact> educations,
      List<SkillFact> skills,
      List<SimpleFact> simpleFacts) {
    this.snapshot = snapshot;
    this.workExperiences = List.copyOf(workExperiences);
    this.projectExperiences = List.copyOf(projectExperiences);
    this.educations = List.copyOf(educations);
    this.skills = List.copyOf(skills);
    this.simpleFacts = List.copyOf(simpleFacts);
  }
}
