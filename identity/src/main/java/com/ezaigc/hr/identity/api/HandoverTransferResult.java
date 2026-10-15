package com.ezaigc.hr.identity.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HandoverTransferResult {
  /** 成功。 */
  private boolean succeeded;
  /** 归属变更历史记录 ID。 */
  private String ownershipHistoryId;
  /** 错误编码。 */
  private String errorCode;
  /** 错误信息。 */
  private String errorMessage;

  public static HandoverTransferResult success(String ownershipHistoryId) {
    return new HandoverTransferResult(true, ownershipHistoryId, null, null);
  }

  public static HandoverTransferResult failure(String errorCode, String errorMessage) {
    return new HandoverTransferResult(false, null, errorCode, errorMessage);
  }
}
