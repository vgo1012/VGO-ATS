package com.ezaigc.hr.recruiting.demand.domain.response;
import java.time.Instant;
import lombok.Value;
import lombok.ToString;
/** 精确版本的附件来源记录；不代表候选人匹配或录用结果。 */
@Value
public class JdResumeSourceResponse {
  @ToString.Exclude String id;
  @ToString.Exclude String mailSourceId;
  @ToString.Exclude String candidateId;
  @ToString.Exclude String candidateName;
  @ToString.Exclude String resumeVersionId;
  @ToString.Exclude String originalFileName;
  @ToString.Exclude String contentSha256;
  @ToString.Exclude Instant createTime;
}
