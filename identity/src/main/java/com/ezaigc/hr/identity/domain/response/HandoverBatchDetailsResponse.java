package com.ezaigc.hr.identity.domain.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HandoverBatchDetailsResponse {
  /** 批次。 */
  private HandoverBatchResponse batch;
  /** 条目列表。 */
  private List<HandoverItemResponse> items;

}
