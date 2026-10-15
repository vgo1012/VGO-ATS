package com.ezaigc.hr.recruiting.interview.domain.response;

import lombok.Value;

@Value
public class InterviewJobOptionResponse {
  String jdId;
  String jdVersionId;
  int jdVersionNo;
  String title;
}
