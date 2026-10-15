package com.ezaigc.hr.talent.domain.response;

import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
public class FactSnapshotResponse {
  /** 主键 ID。 */
  private String id;
  /** 简历版本 ID。 */
  private String resumeVersionId;
  /** 解析快照 ID。 */
  private String parseSnapshotId;
  private String previousSnapshotId;
  /** 快照版本。 */
  private int snapshotVersion;
  /** 数据来源类型。 */
  private String sourceType;
  /** 当前记录唯一标记。 */
  private String currentMarker;
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
  /** 更正操作人用户 ID。 */
  private String correctedByUserId;
  /** 更正原因。 */
  private String correctionReason;
  /** 创建时间。 */
  private Instant createTime;
  /** 出生信息，保留 YYYY、YYYY-MM 或 YYYY-MM-DD 精度。 */
  @ToString.Exclude private String birthDate;
  /** 年龄对应的事实日期；历史快照未记录时为空。 */
  private LocalDate ageAsOf;

  public FactSnapshotResponse(
      String id, String resumeVersionId, String parseSnapshotId, String previousSnapshotId,
      int snapshotVersion, String sourceType, String currentMarker, String fullName, String email,
      String phone, String currentRole, String city, Integer age, String gender, String jobIntention,
      String correctedByUserId, String correctionReason, Instant createTime) {
    this(id, resumeVersionId, parseSnapshotId, previousSnapshotId, snapshotVersion, sourceType,
        currentMarker, fullName, email, phone, currentRole, city, age, gender, jobIntention,
        correctedByUserId, correctionReason, createTime, null, null);
  }
}
