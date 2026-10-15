package com.ezaigc.hr.talent.domain.response;

import java.time.Instant;
import com.ezaigc.hr.talent.enums.ResumeIntakeImportStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResumeIntakeResponse {
  /** 主键 ID。 */
  private String id;

  /** 批次 ID。 */
  private String batchId;

  /** 批次关联的岗位版本，可空。 */
  private String jdVersionId;

  /** 当前有权查看的目标岗位名称。 */
  private String jdTitle;
  /** 有权限查看的岗位业务编号。 */
  private String jdCode;

  /** 来源渠道 ID。 */
  private String sourceChannelId;

  /** 来源招聘顾问用户 ID。 */
  private String sourceRecruiterUserId;

  private String defaultOwnerUserId;

  /** 业务负责人用户 ID。 */
  private String ownerUserId;

  /** 业务归属部门 ID。 */
  private String ownerDepartmentId;

  /** 处理人用户 ID。 */
  private String handledByUserId;

  /** 上传时的原始文件名。 */
  private String originalFileName;

  /** 文件媒体类型。 */
  private String mediaType;

  /** 文件大小（字节）。 */
  private long sizeBytes;

  /** 内容 SHA-256 摘要。 */
  private String contentSha256;

  /** 对象存储键。 */
  private String objectKey;

  /** 同内容文件共享的解析处理 ID。 */
  private String processingId;

  /** 共享解析处理状态。 */
  private String processingStatus;

  /** 共享解析已尝试次数。 */
  private Integer processingAttempt;

  /** 解析出的姓名，供身份复核预填。 */
  private String parsedFullName;

  /** 解析出的邮箱，供身份复核预填。 */
  private String parsedEmail;

  /** 解析出的手机号，供身份复核预填。 */
  private String parsedPhone;

  /** 是否存在同内容历史收件；不表示候选人身份已确认或存在事实冲突。 */
  private boolean duplicateContent;

  /** 安全扫描状态。 */
  private String scanStatus;

  /** 当前状态。 */
  private String status;

  /** 页面三态：待入库、解析中、已入库。 */
  private ResumeIntakeImportStatus importStatus;

  /** 失败编码。 */
  private String failureCode;

  /** 候选人 ID。 */
  private String candidateId;

  /** 简历版本 ID。 */
  private String resumeVersionId;

  /** 关联岗位的评分运行 ID。 */
  private String scoreRunId;

  /** 关联岗位评分状态。 */
  private String scoreStatus;

  /** 技术乐观锁版本号。 */
  private String version;

  /** 创建时间。 */
  private Instant createTime;

  /** 更新时间。 */
  private Instant updateTime;
}
