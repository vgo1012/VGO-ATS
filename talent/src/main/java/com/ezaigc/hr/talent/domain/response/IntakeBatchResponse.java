package com.ezaigc.hr.talent.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IntakeBatchResponse {
  /** 主键 ID。 */
  private String id;

  /** 来源渠道 ID。 */
  private String sourceChannelId;

  /** 来源招聘顾问用户 ID。 */
  private String sourceRecruiterUserId;

  private String defaultOwnerUserId;

  /** 业务归属部门 ID。 */
  private String ownerDepartmentId;

  /** 上传时选择的岗位业务版本，可空。 */
  private String jdVersionId;

  /** 当前状态。 */
  private String status;

  /** 总记录数。 */
  private int totalCount;

  /** 成功记录数。 */
  private int succeededCount;

  /** 失败记录数。 */
  private int failedCount;

  /** 仍在解析或入库的已接收文件数。 */
  private int parsingCount;

  /** 解析、入库或评分仍在进行的文件数；与已入库、需处理、已忽略互斥。 */
  private int processingCount;

  /** 共享解析已成功的文件数。 */
  private int parsedCount;

  /** 已入库且评分成功的文件数。 */
  private int completedCount;

  /** 已从收件列表移出的文件数。 */
  private int ignoredCount;

  /** 需要人工查看或重试的文件数。 */
  private int attentionCount;

  /** 创建时间。 */
  private Instant createTime;

  /** 更新时间。 */
  private Instant updateTime;
}
