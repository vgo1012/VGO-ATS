package com.ezaigc.hr.talent.domain.request;

import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import lombok.Data;

/** 启动 AI 解析并入库，不要求预先填写候选人身份。 */
@Data
public class TalentIntakeImportRequest {
  private @ValidResourceVersion String version;
}
