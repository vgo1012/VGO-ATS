package com.ezaigc.hr.talent.application.provider;

import com.ezaigc.hr.talent.constant.TalentConstants;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

/** 简历解析模型模块能力端口。 */
public interface ResumeParseModelPort {
  ModelResult parse(ParseRequest request);

  /**
   * 返回调用前即可确定的模型路由标识，用于安全复用同一简历版本的已完成结果。
   * 实现切换模型或供应商时必须同步改变该标识；无法稳定声明时返回 {@code null} 禁用前置复用。
   */
  default ModelIdentity cacheIdentity() {
    return null;
  }

  @Value
  @Accessors(fluent = true)
  class ModelIdentity {
    String provider;
    String modelVersion;
  }

  @Value
  @Accessors(fluent = true)
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  class ParseRequest {
    String schemaVersion;
    String promptVersion;
    @ToString.Exclude String fileName;
    @ToString.Exclude List<PageText> pages;

    public ParseRequest(String schemaVersion, String promptVersion, List<PageText> pages) {
      this(schemaVersion, promptVersion, null, pages);
    }

    public ParseRequest(String schemaVersion, String promptVersion, String fileName, List<PageText> pages) {
      this.schemaVersion = schemaVersion;
      this.promptVersion = promptVersion;
      this.fileName = fileName;
      this.pages = List.copyOf(pages);
    }
  }

  @Value
  @Accessors(fluent = true)
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  class PageText {
    int pageNo;
    @ToString.Exclude String text;
  }

  @Value
  @Accessors(fluent = true)
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  class ModelResult {
    String provider;
    String modelVersion;
    @ToString.Exclude String rawJson;
    int inputTokens;
    int outputTokens;
    long durationMillis;
  }

  @Value
  @Accessors(fluent = true)
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  class ResumeFacts {
    @NotNull @Valid CandidateIdentity candidate;
    @NotNull
    @Size(max = TalentConstants.MAX_RESUME_WORK_EXPERIENCES)
    List<@NotNull @Valid WorkExperience> workExperiences;
    @NotNull
    @Size(max = TalentConstants.MAX_RESUME_PROJECT_EXPERIENCES)
    List<@NotNull @Valid ProjectExperience> projectExperiences;
    @NotNull
    @Size(max = TalentConstants.MAX_RESUME_EDUCATIONS)
    List<@NotNull @Valid Education> educations;
    @NotNull
    @Size(max = TalentConstants.MAX_RESUME_SKILLS)
    List<@NotNull @Valid Skill> skills;
    @NotNull
    @Size(max = TalentConstants.MAX_RESUME_CREDENTIALS)
    List<@NotNull @Valid Credential> credentials;
    @NotNull
    @Size(max = TalentConstants.MAX_RESUME_LANGUAGES)
    List<@NotNull @Valid Language> languages;
    @NotNull
    @Size(max = TalentConstants.MAX_RESUME_INDUSTRY_TAGS)
    List<@NotBlank @Size(max = TalentConstants.MAX_RESUME_SHORT_LABEL_LENGTH) String>
        industryTags;
    @NotNull
    @Size(max = TalentConstants.MAX_RESUME_RESPONSIBILITY_TAGS)
    List<
            @NotBlank
            @Size(max = TalentConstants.MAX_RESUME_RESPONSIBILITY_TAG_LENGTH) String>
        responsibilityTags;
    @NotNull
    @Size(max = TalentConstants.MAX_RESUME_UNKNOWN_ITEMS)
    List<@NotBlank @Size(max = TalentConstants.MAX_RESUME_UNKNOWN_ITEM_LENGTH) String>
        unknownItems;
    public ResumeFacts(
        CandidateIdentity candidate,
        List<WorkExperience> workExperiences,
        List<ProjectExperience> projectExperiences,
        List<Education> educations,
        List<Skill> skills,
        List<Credential> credentials,
        List<Language> languages,
        List<String> industryTags,
        List<String> responsibilityTags,
        List<String> unknownItems) {
      this.candidate = candidate;
      this.workExperiences = copy(workExperiences);
      this.projectExperiences = copy(projectExperiences);
      this.educations = copy(educations);
      this.skills = copy(skills);
      this.credentials = copy(credentials);
      this.languages = copy(languages);
      this.industryTags = copy(industryTags);
      this.responsibilityTags = copy(responsibilityTags);
      this.unknownItems = copy(unknownItems);
    }

    private static <T> List<T> copy(List<T> value) {
      return value == null ? List.of() : List.copyOf(value);
    }
  }

  @Value
  @Accessors(fluent = true)
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  class CandidateIdentity {
    @Size(max = TalentConstants.MAX_RESUME_FULL_NAME_LENGTH) String fullName;
    @Email @Size(max = TalentConstants.CANDIDATE_EMAIL_MAX_LENGTH) @ToString.Exclude String email;
    @Size(max = TalentConstants.CANDIDATE_PHONE_MAX_LENGTH)
    @Pattern(regexp = TalentConstants.CANDIDATE_PHONE_INPUT_PATTERN)
    @ToString.Exclude String phone;
    @Size(max = TalentConstants.MAX_RESUME_ROLE_LENGTH) String currentRole;
    @Size(max = TalentConstants.MAX_RESUME_CITY_LENGTH) String city;
    @Min(TalentConstants.MIN_RESUME_AGE) @Max(TalentConstants.MAX_RESUME_AGE) Integer age;
    @Size(max = TalentConstants.MAX_RESUME_CODE_LENGTH)
    @Pattern(regexp = TalentConstants.RESUME_GENDER_INPUT_PATTERN) String gender;
    @Size(max = TalentConstants.MAX_RESUME_JOB_INTENTION_LENGTH) String jobIntention;
    /** 出生信息保留原文年/月/日精度，不使用经历推测。 */
    @Size(max = 10)
    @Pattern(regexp = "[0-9]{4}(-[0-9]{2}(-[0-9]{2})?)?")
    @ToString.Exclude String birthDate;

    public CandidateIdentity(String fullName, String email, String phone, String currentRole,
        String city, Integer age, String gender, String jobIntention) {
      this(fullName, email, phone, currentRole, city, age, gender, jobIntention, null);
    }

    @JsonCreator
    public CandidateIdentity(String fullName, String email, String phone, String currentRole,
        String city, Integer age, String gender, String jobIntention, String birthDate) {
      this.fullName = fullName;
      this.email = email;
      this.phone = phone;
      this.currentRole = currentRole;
      this.city = city;
      this.age = age;
      this.gender = gender;
      this.jobIntention = jobIntention;
      this.birthDate = birthDate;
    }
  }

  @Value
  @Accessors(fluent = true)
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  class WorkExperience {
    @NotBlank
    @Size(max = TalentConstants.MAX_RESUME_ITEM_ID_LENGTH)
    @Pattern(regexp = TalentConstants.RESUME_ITEM_ID_INPUT_PATTERN) String id;
    @Size(max = TalentConstants.MAX_RESUME_ORGANIZATION_NAME_LENGTH) String employer;
    @Size(max = TalentConstants.MAX_RESUME_ROLE_LENGTH) String role;
    @Size(max = TalentConstants.MAX_RESUME_DATE_LENGTH)
    @Pattern(regexp = TalentConstants.RESUME_DATE_INPUT_PATTERN) String startDate;
    @Size(max = TalentConstants.MAX_RESUME_DATE_LENGTH)
    @Pattern(regexp = TalentConstants.RESUME_DATE_INPUT_PATTERN) String endDate;
    @Size(max = TalentConstants.MAX_RESUME_DESCRIPTION_LENGTH) String description;
    @NotNull
    @Size(max = TalentConstants.MAX_RESUME_ITEM_TEXTS)
    List<@NotBlank @Size(max = TalentConstants.MAX_RESUME_LIST_TEXT_LENGTH) String>
        responsibilities;
    @NotNull
    @Size(max = TalentConstants.MAX_RESUME_ITEM_TEXTS)
    List<@NotBlank @Size(max = TalentConstants.MAX_RESUME_LIST_TEXT_LENGTH) String> achievements;
  }

  @Value
  @Accessors(fluent = true)
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  class ProjectExperience {
    @NotBlank
    @Size(max = TalentConstants.MAX_RESUME_ITEM_ID_LENGTH)
    @Pattern(regexp = TalentConstants.RESUME_ITEM_ID_INPUT_PATTERN) String id;
    @Size(max = TalentConstants.MAX_RESUME_ORGANIZATION_NAME_LENGTH) String projectName;
    @Size(max = TalentConstants.MAX_RESUME_ROLE_LENGTH) String role;
    @Size(max = TalentConstants.MAX_RESUME_DATE_LENGTH)
    @Pattern(regexp = TalentConstants.RESUME_DATE_INPUT_PATTERN) String startDate;
    @Size(max = TalentConstants.MAX_RESUME_DATE_LENGTH)
    @Pattern(regexp = TalentConstants.RESUME_DATE_INPUT_PATTERN) String endDate;
    @Size(max = TalentConstants.MAX_RESUME_DESCRIPTION_LENGTH) String description;
    @NotNull
    @Size(max = TalentConstants.MAX_RESUME_ITEM_TEXTS)
    List<@NotBlank @Size(max = TalentConstants.MAX_RESUME_SHORT_LABEL_LENGTH) String>
        technologies;
    @NotNull
    @Size(max = TalentConstants.MAX_RESUME_ITEM_TEXTS)
    List<@NotBlank @Size(max = TalentConstants.MAX_RESUME_LIST_TEXT_LENGTH) String> achievements;
  }

  @Value
  @Accessors(fluent = true)
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  class Education {
    @NotBlank
    @Size(max = TalentConstants.MAX_RESUME_ITEM_ID_LENGTH)
    @Pattern(regexp = TalentConstants.RESUME_ITEM_ID_INPUT_PATTERN) String id;
    @Size(max = TalentConstants.MAX_RESUME_ORGANIZATION_NAME_LENGTH) String school;
    @Size(max = TalentConstants.MAX_RESUME_SHORT_LABEL_LENGTH) String degree;
    @Size(max = TalentConstants.MAX_RESUME_ROLE_LENGTH) String major;
    @Size(max = TalentConstants.MAX_RESUME_DATE_LENGTH)
    @Pattern(regexp = TalentConstants.RESUME_DATE_INPUT_PATTERN) String startDate;
    @Size(max = TalentConstants.MAX_RESUME_DATE_LENGTH)
    @Pattern(regexp = TalentConstants.RESUME_DATE_INPUT_PATTERN) String endDate;
    @Size(max = TalentConstants.MAX_RESUME_CODE_LENGTH) String learningMode;
  }

  @Value
  @Accessors(fluent = true)
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  class Skill {
    @NotBlank
    @Size(max = TalentConstants.MAX_RESUME_ITEM_ID_LENGTH)
    @Pattern(regexp = TalentConstants.RESUME_ITEM_ID_INPUT_PATTERN) String id;
    @NotBlank @Size(max = TalentConstants.MAX_RESUME_SHORT_LABEL_LENGTH) String name;
    @Size(max = TalentConstants.MAX_RESUME_CODE_LENGTH) String level;
    @DecimalMin(TalentConstants.MIN_RESUME_YEARS)
    @DecimalMax(TalentConstants.MAX_RESUME_YEARS) Double years;
  }

  @Value
  @Accessors(fluent = true)
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  class Credential {
    @NotBlank
    @Size(max = TalentConstants.MAX_RESUME_ITEM_ID_LENGTH)
    @Pattern(regexp = TalentConstants.RESUME_ITEM_ID_INPUT_PATTERN) String id;
    @NotBlank @Size(max = TalentConstants.MAX_RESUME_ORGANIZATION_NAME_LENGTH) String name;
    @Size(max = TalentConstants.MAX_RESUME_ORGANIZATION_NAME_LENGTH) String issuer;
    @Size(max = TalentConstants.MAX_RESUME_DATE_LENGTH)
    @Pattern(regexp = TalentConstants.RESUME_DATE_INPUT_PATTERN) String issuedDate;
    @Size(max = TalentConstants.MAX_RESUME_DATE_LENGTH)
    @Pattern(regexp = TalentConstants.RESUME_DATE_INPUT_PATTERN) String expiryDate;
  }

  @Value
  @Accessors(fluent = true)
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  class Language {
    @NotBlank
    @Size(max = TalentConstants.MAX_RESUME_ITEM_ID_LENGTH)
    @Pattern(regexp = TalentConstants.RESUME_ITEM_ID_INPUT_PATTERN) String id;
    @NotBlank @Size(max = TalentConstants.MAX_RESUME_LEVEL_LENGTH) String name;
    @Size(max = TalentConstants.MAX_RESUME_LEVEL_LENGTH) String level;
  }

  final class ModelUnavailableException extends RuntimeException {
    public ModelUnavailableException(String message) {
      super(message);
    }
  }
}
