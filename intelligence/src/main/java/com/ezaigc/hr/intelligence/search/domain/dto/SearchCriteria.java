package com.ezaigc.hr.intelligence.search.domain.dto;

import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.CANDIDATE_GENDER_PATTERN;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_CANDIDATE_AGE;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_FILTER_VALUE_LENGTH;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_FILTER_VALUES;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_NATURAL_CONDITION_LENGTH;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_QUERY_LENGTH;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_TOTAL_FILTER_VALUES;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_WORK_YEARS_DECIMAL;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MIN_CANDIDATE_AGE;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MIN_WORK_YEARS_DECIMAL;

import com.ezaigc.hr.intelligence.search.enums.TalentSearchScope;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SearchCriteria {
  /** 查询。 */
  private @Size(max = MAX_QUERY_LENGTH) String query;
  /** 自定义全文条件，各项之间为 AND。 */
  private @NotNull @Size(max = 10) List<@NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      conditions = List.of();
  /** 技能。 */
  private @NotNull @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      skills = List.of();
  /** 任职公司列表。 */
  private @NotNull @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      employers = List.of();
  /** 角色列表。 */
  private @NotNull @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      roles = List.of();
  /** 项目列表。 */
  private @NotNull @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      projects = List.of();
  /** 证书列表。 */
  private @NotNull @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      certificates = List.of();
  /** 语言能力列表。 */
  private @NotNull @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      languages = List.of();
  /** 学历列表。 */
  private @NotNull @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      degrees = List.of();
  /** 城市列表。 */
  private @NotNull @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      cities = List.of();
  /** 行业列表。 */
  private @NotNull @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      industries = List.of();
  /** 标签列表。 */
  private @NotNull @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      tags = List.of();
  /** 最小工作年限。 */
  private @DecimalMin(MIN_WORK_YEARS_DECIMAL) @DecimalMax(MAX_WORK_YEARS_DECIMAL) BigDecimal
      minWorkYears;
  /** 最大工作年限（排他）。 */
  private @DecimalMin(MIN_WORK_YEARS_DECIMAL) @DecimalMax(MAX_WORK_YEARS_DECIMAL) BigDecimal
      maxWorkYears;
  /** 搜索范围；个人池按当前角色的数据范围实时裁剪。 */
  private @Size(max = 32) @Pattern(regexp = "SHARED|PERSONAL_POOL|MY_TASKS") String scope = "SHARED";
  /** 逻辑 JD，覆盖历史版本；待处置按本人未完成任务所属招聘活动过滤，其他范围按简历关联岗位过滤。 */
  private @Size(max = 64) @Pattern(regexp = "\\S+") String jdId;
  /** 个人池队列分类，在数据库回查和分页前应用。 */
  private @Pattern(regexp = "PENDING_SCREENING|PENDING_DISPOSAL") String poolStage;
  /** 当前范围允许搜索的候选人。 */
  private @NotNull @Size(max = 200) List<@NotBlank @Size(max = 64) String> candidateIds =
      List.of();
  /** 服务端写入的游标用户隔离标识。 */
  private @Size(max = 64) String cursorActor;
  /** 来源渠道。 */
  private @Size(max = MAX_FILTER_VALUE_LENGTH) String sourceChannel;
  /** 意向。 */
  private @Size(max = MAX_FILTER_VALUE_LENGTH) String intention;
  /** 性别（仅保存明确证据）。 */
  private @Size(max = MAX_FILTER_VALUE_LENGTH) @Pattern(regexp = CANDIDATE_GENDER_PATTERN)
      String gender;
  /** 最小年龄。 */
  private @Min(MIN_CANDIDATE_AGE) @Max(MAX_CANDIDATE_AGE) Integer minAge;
  /** 最大年龄。 */
  private @Min(MIN_CANDIDATE_AGE) @Max(MAX_CANDIDATE_AGE) Integer maxAge;
  /** 更新变更前。 */
  private Instant updatedFrom;
  /** 更新变更后。 */
  private Instant updatedTo;
  /** 自然语言搜索条件。 */
  private @Size(max = MAX_NATURAL_CONDITION_LENGTH) String naturalCondition;

  @AssertTrue(message = "关联岗位筛选仅适用于共享库、个人待筛选或待处置")
  @JsonIgnore
  public boolean isJdScopeValid() {
    return jdId == null || TalentSearchScope.PERSONAL_POOL.matches(scope)
        || TalentSearchScope.SHARED.matches(scope) || TalentSearchScope.MY_TASKS.matches(scope);
  }

  @AssertTrue(message = "筛选词条合计不能超过 " + MAX_TOTAL_FILTER_VALUES + " 项")
  @JsonIgnore
  public boolean isTotalFilterCountValid() {
    return size(conditions)
            + size(skills)
            + size(employers)
            + size(roles)
            + size(projects)
            + size(certificates)
            + size(languages)
            + size(degrees)
            + size(cities)
            + size(industries)
            + size(tags)
        <= MAX_TOTAL_FILTER_VALUES;
  }

  @AssertTrue(message = "最小年龄不能大于最大年龄")
  @JsonIgnore
  public boolean isAgeRangeValid() {
    return minAge == null || maxAge == null || minAge <= maxAge;
  }

  @AssertTrue(message = "最小工作年限必须小于最大工作年限")
  @JsonIgnore
  public boolean isWorkYearsRangeValid() {
    return minWorkYears == null
        || maxWorkYears == null
        || minWorkYears.compareTo(maxWorkYears) < 0;
  }

  @AssertTrue(message = "更新时间起点不能晚于终点")
  @JsonIgnore
  public boolean isUpdatedRangeValid() {
    return updatedFrom == null || updatedTo == null || !updatedFrom.isAfter(updatedTo);
  }

  public SearchCriteria(
      String query,
      List<String> skills,
      List<String> employers,
      List<String> roles,
      List<String> projects,
      List<String> certificates,
      List<String> languages,
      List<String> degrees,
      List<String> cities,
      List<String> industries,
      List<String> tags,
      BigDecimal minWorkYears,
      String sourceChannel,
      String intention,
      String gender,
      Integer minAge,
      Integer maxAge,
      Instant updatedFrom,
      Instant updatedTo,
      String naturalCondition) {
    skills = copy(skills);
    employers = copy(employers);
    roles = copy(roles);
    projects = copy(projects);
    certificates = copy(certificates);
    languages = copy(languages);
    degrees = copy(degrees);
    cities = copy(cities);
    industries = copy(industries);
    tags = copy(tags);
    this.query = query;
    this.skills = skills;
    this.employers = employers;
    this.roles = roles;
    this.projects = projects;
    this.certificates = certificates;
    this.languages = languages;
    this.degrees = degrees;
    this.cities = cities;
    this.industries = industries;
    this.tags = tags;
    this.minWorkYears = minWorkYears;
    this.sourceChannel = sourceChannel;
    this.intention = intention;
    this.gender = gender;
    this.minAge = minAge;
    this.maxAge = maxAge;
    this.updatedFrom = updatedFrom;
    this.updatedTo = updatedTo;
    this.naturalCondition = naturalCondition;
  }

  private static List<String> copy(List<String> values) {
    return values == null
        ? List.of()
        : values.stream()
            .filter(v -> v != null && !v.isBlank())
            .map(String::strip)
            .distinct()
            .toList();
  }

  private static int size(List<?> values) {
    return values == null ? 0 : values.size();
  }
}
