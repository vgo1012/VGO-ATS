package com.ezaigc.hr.talent.domain.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 当前生效年度日历；未发布时 revisionId 为空且 days 为空，不推断班休。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublishedWorkCalendarResponse {
  private int year;
  private String revisionId;
  private List<WorkCalendarDayResponse> days;
}
