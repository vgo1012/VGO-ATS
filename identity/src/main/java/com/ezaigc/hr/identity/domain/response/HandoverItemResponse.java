package com.ezaigc.hr.identity.domain.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HandoverItemResponse {
  /** 主键 ID。 */
  private String id;
  /** 批次 ID。 */
  private String batchId;
  /** 资源类型。 */
  private String resourceType;
  /** 资源 ID。 */
  private String resourceId;
  /** 当前状态。 */
  private String status;
  /** 是否为强制项。 */
  private boolean mandatory;
  /** 错误编码。 */
  private String errorCode;
  /** 错误信息。 */
  private String errorMessage;
  /** 变更前负责人用户 ID。 */
  private String fromOwnerUserId;
  /** 变更后负责人用户 ID。 */
  private String toOwnerUserId;
  /** 归属变更历史记录 ID。 */
  private String ownershipHistoryId;
}
