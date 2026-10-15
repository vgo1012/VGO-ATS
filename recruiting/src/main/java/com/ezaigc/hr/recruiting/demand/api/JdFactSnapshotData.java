package com.ezaigc.hr.recruiting.demand.api;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
public class JdFactSnapshotData {
  private String snapshotId;
  /** 职位描述（JD）版本 ID。 */
  private String jdVersionId;
  /** 快照版本。 */
  private int snapshotVersion;
  /** 专业。 */
  private ProfessionalFacts professional;
  /** 受控。 */
  private ControlledFacts controlled;
  /** 事实满足情况。 */
  private FulfillmentFacts fulfillment;

  public JdFactSnapshotData(
      String snapshotId,
      String jdVersionId,
      int snapshotVersion,
      ProfessionalFacts professional,
      ControlledFacts controlled,
      FulfillmentFacts fulfillment) {
    this.snapshotId = snapshotId;
    this.jdVersionId = jdVersionId;
    this.snapshotVersion = snapshotVersion;
    this.professional = professional;
    this.controlled = controlled;
    this.fulfillment = fulfillment;
  }

  @Value
  @Accessors(fluent = true)
  public static class ProfessionalFacts {
    List<Skill> coreSkills;
    List<Role> roleExperiences;
    BigDecimal minimumYears;
    BigDecimal preferredYears;
    List<String> responsibilities;
    List<String> industryTags;
    List<String> projectTags;

    public ProfessionalFacts(
        List<Skill> coreSkills,
        List<Role> roleExperiences,
        BigDecimal minimumYears,
        BigDecimal preferredYears,
        List<String> responsibilities,
        List<String> industryTags,
        List<String> projectTags) {
      this.coreSkills = List.copyOf(coreSkills);
      this.roleExperiences = List.copyOf(roleExperiences);
      this.minimumYears = minimumYears;
      this.preferredYears = preferredYears;
      this.responsibilities = List.copyOf(responsibilities);
      this.industryTags = List.copyOf(industryTags);
      this.projectTags = List.copyOf(projectTags);
    }
  }

  @Value
  @Accessors(fluent = true)
  public static class ControlledFacts {
    Integer ageMin;
    Integer ageMax;
    String sex;
    List<String> educations;
    List<String> credentials;
    List<Language> languages;

    public ControlledFacts(
        Integer ageMin,
        Integer ageMax,
        String sex,
        List<String> educations,
        List<String> credentials,
        List<Language> languages) {
      this.ageMin = ageMin;
      this.ageMax = ageMax;
      this.sex = sex;
      this.educations = List.copyOf(educations);
      this.credentials = List.copyOf(credentials);
      this.languages = List.copyOf(languages);
    }
  }

  @Value
  @Accessors(fluent = true)
  public static class FulfillmentFacts {
    List<String> locations;
    String availability;
    BigDecimal salaryMin;
    BigDecimal salaryMax;
    String salaryPeriod;

    public FulfillmentFacts(
        List<String> locations,
        String availability,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryPeriod) {
      this.locations = List.copyOf(locations);
      this.availability = availability;
      this.salaryMin = salaryMin;
      this.salaryMax = salaryMax;
      this.salaryPeriod = salaryPeriod;
    }
  }

  @Value
  @Accessors(fluent = true)
  public static class Skill {
    String id;
    String name;
    boolean required;
    List<String> aliases;

    public Skill(String id, String name, boolean required, List<String> aliases) {
      this.id = id;
      this.name = name;
      this.required = required;
      this.aliases = List.copyOf(aliases);
    }
  }

  @Value
  @Accessors(fluent = true)
  public static class Role {
    String id;
    String role;
    String level;
  }

  @Value
  @Accessors(fluent = true)
  public static class Language {
    String id;
    String name;
    String level;
  }

}
