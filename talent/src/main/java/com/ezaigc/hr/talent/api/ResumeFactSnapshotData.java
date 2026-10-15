package com.ezaigc.hr.talent.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
public class ResumeFactSnapshotData {
  private String snapshotId;
  /** 简历版本 ID。 */
  private String resumeVersionId;
  /** 候选人 ID。 */
  private String candidateId;
  /** 快照版本。 */
  private int snapshotVersion;
  /** 候选人。 */
  private CandidateFacts candidate;
  /** 工作经历列表。 */
  private List<Work> workExperiences;
  /** 项目经历列表。 */
  private List<Project> projectExperiences;
  /** 教育经历列表。 */
  private List<Education> educations;
  /** 技能。 */
  private List<Skill> skills;
  private List<SimpleFact> simpleFacts;

  public ResumeFactSnapshotData(
      String snapshotId,
      String resumeVersionId,
      String candidateId,
      int snapshotVersion,
      CandidateFacts candidate,
      List<Work> workExperiences,
      List<Project> projectExperiences,
      List<Education> educations,
      List<Skill> skills,
      List<SimpleFact> simpleFacts) {
    workExperiences = List.copyOf(workExperiences);
    projectExperiences = List.copyOf(projectExperiences);
    educations = List.copyOf(educations);
    skills = List.copyOf(skills);
    simpleFacts = List.copyOf(simpleFacts);
    this.snapshotId = snapshotId;
    this.resumeVersionId = resumeVersionId;
    this.candidateId = candidateId;
    this.snapshotVersion = snapshotVersion;
    this.candidate = candidate;
    this.workExperiences = workExperiences;
    this.projectExperiences = projectExperiences;
    this.educations = educations;
    this.skills = skills;
    this.simpleFacts = simpleFacts;
  }

  @Value
  @Accessors(fluent = true)
  public static class CandidateFacts {
    String fullName;
    @ToString.Exclude String email;
    @ToString.Exclude String phone;
    String currentRole;
    String city;
    Integer age;
    String gender;
    String jobIntention;
  }

  @Value
  @Accessors(fluent = true)
  public static class Work {
    String itemKey;
    String employer;
    String role;
    LocalDate startDate;
    LocalDate endDate;
    String description;
    List<String> responsibilities;
    List<String> achievements;
    String startDateText;
    String endDateText;

    public Work(
        String itemKey,
        String employer,
        String role,
        LocalDate startDate,
        LocalDate endDate,
        String description,
        List<String> responsibilities,
        List<String> achievements) {
      this(itemKey, employer, role, startDate, endDate, description, responsibilities, achievements,
          startDate == null ? null : startDate.toString(), endDate == null ? null : endDate.toString());
    }

    public Work(String itemKey, String employer, String role, LocalDate startDate, LocalDate endDate,
        String description, List<String> responsibilities, List<String> achievements,
        String startDateText, String endDateText) {
      this.itemKey = itemKey;
      this.employer = employer;
      this.role = role;
      this.startDate = startDate;
      this.endDate = endDate;
      this.description = description;
      this.responsibilities = List.copyOf(responsibilities);
      this.achievements = List.copyOf(achievements);
      this.startDateText = startDateText;
      this.endDateText = endDateText;
    }
  }

  @Value
  @Accessors(fluent = true)
  public static class Project {
    String itemKey;
    String projectName;
    String role;
    LocalDate startDate;
    LocalDate endDate;
    String description;
    List<String> technologies;
    List<String> achievements;

    public Project(
        String itemKey,
        String projectName,
        String role,
        LocalDate startDate,
        LocalDate endDate,
        String description,
        List<String> technologies,
        List<String> achievements) {
      this.itemKey = itemKey;
      this.projectName = projectName;
      this.role = role;
      this.startDate = startDate;
      this.endDate = endDate;
      this.description = description;
      this.technologies = List.copyOf(technologies);
      this.achievements = List.copyOf(achievements);
    }
  }

  @Value
  @Accessors(fluent = true)
  public static class Education {
    String itemKey;
    String school;
    String degree;
    String major;
    LocalDate startDate;
    LocalDate endDate;
    String learningMode;
  }

  @Value
  @Accessors(fluent = true)
  public static class Skill {
    String itemKey;
    String name;
    String level;
    BigDecimal years;
  }

  @Value
  @Accessors(fluent = true)
  public static class SimpleFact {
    String type;
    String itemKey;
    String name;
    String level;
    String issuer;
    LocalDate startDate;
    LocalDate endDate;
  }

}
