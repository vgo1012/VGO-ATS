package com.ezaigc.hr.intelligence.search.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SearchDocument {
  /** 仅用于索引写入的外部版本元数据，不进入可搜索内容或 API 响应。 */
  @JsonIgnore
  private long projectionVersion;
  private long candidateVersion;
  /** 候选人 ID。 */
  private String candidateId;
  /** 简历版本 ID。 */
  private String resumeVersionId;
  /** 事实快照 ID。 */
  private String factSnapshotId;
  /** 姓名。 */
  private String fullName;
  /** 当前角色。 */
  private String currentRole;
  /** 所在城市。 */
  private String city;
  /** 任职公司列表。 */
  private List<String> employers;
  /** 角色列表。 */
  private List<String> roles;
  /** 项目列表。 */
  private List<String> projects;
  /** 技能。 */
  private List<String> skills;
  /** 证书列表。 */
  private List<String> certificates;
  /** 语言能力列表。 */
  private List<String> languages;
  /** 学历列表。 */
  private List<String> degrees;
  /** 行业列表。 */
  private List<String> industries;
  /** 标签列表。 */
  private List<String> tags;
  /** 工作年限。 */
  private BigDecimal workYears;
  /** 来源渠道。 */
  private String sourceChannel;
  /** 意向。 */
  private String intention;
  /** 性别（仅保存明确证据）。 */
  private String gender;
  /** 年龄（仅保存明确证据）。 */
  private Integer age;
  /** 搜索文本。 */
  private String searchText;
  /** 更新时间。 */
  private Instant updateTime;

  public SearchDocument(
      String candidateId,
      String resumeVersionId,
      String factSnapshotId,
      String fullName,
      String currentRole,
      String city,
      List<String> employers,
      List<String> roles,
      List<String> projects,
      List<String> skills,
      List<String> certificates,
      List<String> languages,
      List<String> degrees,
      List<String> industries,
      List<String> tags,
      BigDecimal workYears,
      String sourceChannel,
      String intention,
      String gender,
      Integer age,
      String searchText,
      Instant updateTime) {
    employers = copy(employers);
    roles = copy(roles);
    projects = copy(projects);
    skills = copy(skills);
    certificates = copy(certificates);
    languages = copy(languages);
    degrees = copy(degrees);
    industries = copy(industries);
    tags = copy(tags);
    this.candidateId = candidateId;
    this.resumeVersionId = resumeVersionId;
    this.factSnapshotId = factSnapshotId;
    this.fullName = fullName;
    this.currentRole = currentRole;
    this.city = city;
    this.employers = employers;
    this.roles = roles;
    this.projects = projects;
    this.skills = skills;
    this.certificates = certificates;
    this.languages = languages;
    this.degrees = degrees;
    this.industries = industries;
    this.tags = tags;
    this.workYears = workYears;
    this.sourceChannel = sourceChannel;
    this.intention = intention;
    this.gender = gender;
    this.age = age;
    this.searchText = searchText;
    this.updateTime = updateTime;
  }

  private static List<String> copy(List<String> values) {
    return values == null ? List.of() : List.copyOf(values);
  }
}
