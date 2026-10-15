package com.ezaigc.hr.recruiting.interview.domain.entity;

import java.time.Instant;
import lombok.Data;

/** 本人受邀面试涉及的岗位版本投影，不包含 JD 原文或候选人信息。 */
@Data
public class InterviewJobOption {
  private String jdId;
  private String jdVersionId;
  private int jdVersionNo;
  private String title;
  private Instant sortTime;
}
