package com.ezaigc.hr.recruiting.interview.domain.entity;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateVersion {
  /** 主键 ID。 */
  private String id;
  /** 模板 ID。 */
  private String templateId;
  /** 业务版本号。 */
  private int versionNo;
  /** 主题模板。 */
  private String subjectTemplate;
  /** 正文模板。 */
  private String bodyTemplate;
  private String createId;
  /** 创建时间。 */
  private Instant createTime;
}
