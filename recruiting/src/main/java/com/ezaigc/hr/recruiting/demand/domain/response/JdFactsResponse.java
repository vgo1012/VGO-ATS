package com.ezaigc.hr.recruiting.demand.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import tools.jackson.databind.JsonNode;

/** 当前 JD 事实快照响应。 */
@Data
@AllArgsConstructor
public class JdFactsResponse {
  /** 事实快照 ID。 */
  private String factSnapshotId;
  /** 职位描述（JD）版本 ID。 */
  private String jdVersionId;
  /** 快照版本。 */
  private int snapshotVersion;
  /** 数据来源类型。 */
  private String sourceType;
  /** 数据结构版本号。 */
  private String schemaVersion;
  /** 事实列表。 */
  private JsonNode facts;
  /** 创建时间。 */
  private Instant createTime;
}
