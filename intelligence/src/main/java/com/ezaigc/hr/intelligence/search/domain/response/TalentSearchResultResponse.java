package com.ezaigc.hr.intelligence.search.domain.response;

import com.ezaigc.hr.recruiting.recruitment.api.EngagementLockQueryService.LockSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TalentSearchResultResponse {
  /** 候选人 ID。 */
  private String candidateId;
  /** 简历版本 ID。 */
  private String resumeVersionId;
  /** 事实快照 ID。 */
  private String factSnapshotId;
  /** 姓名。 */
  private String fullName;
  /** 邮箱地址。 */
  @ToString.Exclude private String email;
  /** 联系电话。 */
  @ToString.Exclude private String phone;
  /** 当前角色。 */
  private String currentRole;
  /** 所在城市。 */
  private String city;
  /** 年龄（仅保存明确证据）。 */
  private Integer age;
  /** 性别（仅保存明确证据）。 */
  private String gender;
  /** 求职意向。 */
  private String jobIntention;
  /** 来源渠道。 */
  private String sourceChannel;
  /** 事实更新时间。 */
  private Instant factUpdatedAt;
  /** 技能。 */
  private List<String> skills;
  /** 任职公司列表。 */
  private List<String> employers;
  /** 角色列表。 */
  private List<String> roles;
  /** 项目列表。 */
  private List<String> projects;
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
  /** 跟进归属锁定。 */
  private LockSummary engagementLock;
  private List<ConditionMatchResponse> conditionMatches;
  /** 应聘流程摘要列表。 */
  private List<Object> applicationSummaries;
  /** 面试摘要列表。 */
  private List<Object> interviewSummaries;
  private List<Object> clientSubmissionSummaries;
}
