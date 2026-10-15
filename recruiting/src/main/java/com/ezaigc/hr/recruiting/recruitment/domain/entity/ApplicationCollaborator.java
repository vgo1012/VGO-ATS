package com.ezaigc.hr.recruiting.recruitment.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("recruitment_application_collaborator")
public class ApplicationCollaborator extends StandardVersionedPersistenceFields {
  /** 应聘流程 ID。 */
  @TableField("application_id")
  private String applicationId;
  /** 用户 ID。 */
  @TableField("user_id")
  private String userId;
  /** 添加操作人用户 ID。 */
  @TableField("added_by_user_id")
  private String addedByUserId;
}
