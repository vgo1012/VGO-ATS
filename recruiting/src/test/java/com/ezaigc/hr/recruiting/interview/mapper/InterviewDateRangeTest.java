package com.ezaigc.hr.recruiting.interview.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class InterviewDateRangeTest {
  @Test
  void dateRangeIsAppliedBeforePagination() {
    var method = Arrays.stream(InterviewMapper.class.getMethods())
        .filter(value -> value.getName().equals("findInterviews")).findFirst().orElseThrow();
    String sql = String.join(" ", method.getAnnotation(Select.class).value());
    assertThat(sql).contains("i.application_id=#{applicationId}", "i.scheduled_start_at>=#{from}", "i.scheduled_start_at<#{to}");
    assertThat(sql.indexOf("i.application_id=#{applicationId}")).isLessThan(sql.indexOf("LIMIT #{limit}"));
    assertThat(sql.indexOf("i.scheduled_start_at>=#{from}")).isLessThan(sql.indexOf("LIMIT #{limit}"));
  }
}
