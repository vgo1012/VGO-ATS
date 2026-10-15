package com.ezaigc.hr.intelligence.search.domain.request;

import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.CANDIDATE_GENDER_PATTERN;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_CANDIDATE_AGE;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_CURSOR_LENGTH;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_FILTER_VALUE_LENGTH;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_FILTER_VALUES;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_NATURAL_CONDITION_LENGTH;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_QUERY_LENGTH;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_SEARCH_PAGE_SIZE;
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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Data;

@Data
public class TalentSearchRequest {
  /** 搜索关键词。 */
  private @Size(max = MAX_QUERY_LENGTH) String q;
  /** 自定义全文条件；每项与 q 同时满足，最多 10 项，每项 200 字。 */
  private @Size(max = 10) List<@NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String> conditions;
  /** 技能。 */
  private @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      skills;
  /** 任职公司列表。 */
  private @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      employers;
  /** 角色列表。 */
  private @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      roles;
  /** 项目列表。 */
  private @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      projects;
  /** 证书列表。 */
  private @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      certificates;
  /** 语言能力列表。 */
  private @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      languages;
  /** 学历列表。 */
  private @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      degrees;
  /** 城市列表。 */
  private @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      cities;
  /** 行业列表。 */
  private @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      industries;
  /** 标签列表。 */
  private @Size(max = MAX_FILTER_VALUES) List<
          @NotBlank @Size(max = MAX_FILTER_VALUE_LENGTH) String>
      tags;
  /** 最小工作年限。 */
  private @DecimalMin(MIN_WORK_YEARS_DECIMAL) @DecimalMax(MAX_WORK_YEARS_DECIMAL) BigDecimal
      minWorkYears;
  /** 最大工作年限（排他）。 */
  private @DecimalMin(MIN_WORK_YEARS_DECIMAL) @DecimalMax(MAX_WORK_YEARS_DECIMAL) BigDecimal
      maxWorkYears;
  /** 搜索范围；个人池按当前角色的数据范围实时裁剪。 */
  private @Pattern(regexp = "SHARED|PERSONAL_POOL|MY_TASKS") String scope = "SHARED";
  /** 逻辑 JD，覆盖历史版本；待处置按本人未完成任务所属招聘活动过滤，其他范围按简历关联岗位过滤。 */
  private @Size(max = 64) @Pattern(regexp = "\\S+") String jdId;
  /** 个人池队列分类，在数据库回查和分页前应用。 */
  private @Pattern(regexp = "PENDING_SCREENING|PENDING_DISPOSAL") String poolStage;
  /** 当前范围允许搜索的候选人。 */
  private @Size(max = 200) List<@NotBlank @Size(max = 64) String> candidateIds;
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
  /** 游标。 */
  private @Size(max = MAX_CURSOR_LENGTH) String cursor;

  /** 上限。 */
  @Min(1)
  @Max(MAX_SEARCH_PAGE_SIZE)
  private int limit = 50;

  @AssertTrue(message = "关联岗位筛选仅适用于共享库、个人待筛选或待处置")
  @JsonIgnore
  public boolean isJdScopeValid() {
    return jdId == null || TalentSearchScope.PERSONAL_POOL.matches(scope)
        || TalentSearchScope.SHARED.matches(scope) || TalentSearchScope.MY_TASKS.matches(scope);
  }

  /** 防止反向年龄区间进入搜索提供者并产生含义不明的查询。 */
  @AssertTrue(message = "最小年龄不能大于最大年龄")
  @JsonIgnore
  public boolean isAgeRangeValid() {
    return minAge == null || maxAge == null || minAge <= maxAge;
  }

  /** 防止空区间或反向工作年限区间进入搜索提供者。 */
  @AssertTrue(message = "最小工作年限必须小于最大工作年限")
  @JsonIgnore
  public boolean isWorkYearsRangeValid() {
    return minWorkYears == null
        || maxWorkYears == null
        || minWorkYears.compareTo(maxWorkYears) < 0;
  }

  /** 防止反向更新时间区间进入搜索提供者并产生含义不明的查询。 */
  @AssertTrue(message = "更新时间起点不能晚于终点")
  @JsonIgnore
  public boolean isUpdatedRangeValid() {
    return updatedFrom == null || updatedTo == null || !updatedFrom.isAfter(updatedTo);
  }
}
