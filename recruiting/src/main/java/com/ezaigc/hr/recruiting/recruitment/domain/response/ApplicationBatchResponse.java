package com.ezaigc.hr.recruiting.recruitment.domain.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApplicationBatchResponse {
  private List<ApplicationBatchItemResponse> items;
}
