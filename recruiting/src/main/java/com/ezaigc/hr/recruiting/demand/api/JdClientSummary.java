package com.ezaigc.hr.recruiting.demand.api;

import lombok.Value;
import lombok.AllArgsConstructor;

/** 历史 JD 版本及客户展示信息；内部招聘的客户字段为空。 */
@Value
@AllArgsConstructor
public class JdClientSummary {
  String clientId;
  String clientName;
  String jdTitle;
  String jdCode;

  public JdClientSummary(String clientId, String clientName) {
    this(clientId, clientName, null, null);
  }
}
