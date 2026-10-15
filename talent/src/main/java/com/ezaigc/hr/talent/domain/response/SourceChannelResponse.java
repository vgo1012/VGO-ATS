package com.ezaigc.hr.talent.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SourceChannelResponse {
  /** 主键 ID。 */
  private String id;
  /** 业务编码。 */
  private String code;
  /** 名称。 */
  private String name;
  /** 渠道类型。 */
  private String channelType;
  /** 当前状态。 */
  private String status;
  /** 技术乐观锁版本号。 */
  private String version;
  private String createId;
  /** 创建时间。 */
  private Instant createTime;
  /** 更新时间。 */
  private Instant updateTime;
}
