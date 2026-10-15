package com.ezaigc.hr.talent.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateSourceChannelDTO {
  /** 名称。 */
  private String name;
  /** 渠道类型。 */
  private String channelType;
}
