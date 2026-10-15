package com.ezaigc.hr.recruiting.interview.domain.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 多个面试的可见面评游标页。 */
@Data
@AllArgsConstructor
public class InterviewFeedbackBatchResponse {
  /** 本次请求中当前操作者可读取的面试 ID。 */
  private List<String> visibleInterviewIds;
  /** 当前页面评，按创建时间和 ID 降序排列。 */
  private List<InterviewFeedbackResponse> items;
  /** 下一页游标，无更多数据时为空。 */
  private String nextCursor;
  /** 是否还有更多面评。 */
  private boolean hasMore;
}
