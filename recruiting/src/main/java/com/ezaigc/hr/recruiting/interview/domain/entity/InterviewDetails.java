package com.ezaigc.hr.recruiting.interview.domain.entity;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewDetails {
  /** 面试。 */
  private Interview interview;
  /** 参与者列表。 */
  private List<Participant> participants;
}
