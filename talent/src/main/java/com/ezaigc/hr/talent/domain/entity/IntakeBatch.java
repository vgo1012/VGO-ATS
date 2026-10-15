package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("talent_intake_batch")
public class IntakeBatch extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 来源渠道 ID。 */
  @TableField("source_channel_id")
  private String sourceChannelId;

  /** 来源招聘顾问用户 ID。 */
  @TableField("source_recruiter_user_id")
  private String sourceRecruiterUserId;

  @TableField("default_owner_user_id")
  private String defaultOwnerUserId;

  /** 业务归属部门 ID。 */
  @TableField("owner_department_id")
  private String ownerDepartmentId;

  /** 上传时选择的唯一岗位业务版本。 */
  @TableField("jd_version_id")
  private String jdVersionId;

  /** 当前状态。 */
  @TableField("status")
  private String status;

  /** 总记录数。 */
  @TableField("total_count")
  private int totalCount;

  /** 成功记录数。 */
  @TableField("succeeded_count")
  private int succeededCount;

  /** 失败记录数。 */
  @TableField("failed_count")
  private int failedCount;

  /** 已接收文件中仍在解析或入库的数量；不包含等待人工重新启动的文件。 */
  @TableField(exist = false)
  private int parsingCount;

  /** 解析、入库或评分仍在进行的文件数量；不含需要人工处理的异常。 */
  @TableField(exist = false)
  private int processingCount;

  /** 共享解析已成功的文件数量。 */
  @TableField(exist = false)
  private int parsedCount;

  /** 已入库且评分成功的文件数量。 */
  @TableField(exist = false)
  private int completedCount;

  /** 已从收件列表移出的文件数量。 */
  @TableField(exist = false)
  private int ignoredCount;

  /** 需要人工查看或重试的文件数量。 */
  @TableField(exist = false)
  private int attentionCount;
}
