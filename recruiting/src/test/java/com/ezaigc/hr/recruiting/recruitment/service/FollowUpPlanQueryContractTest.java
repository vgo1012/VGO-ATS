package com.ezaigc.hr.recruiting.recruitment.service;
import static org.assertj.core.api.Assertions.assertThat;
import com.ezaigc.hr.recruiting.recruitment.mapper.FollowUpPlanMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.FollowUpPlanHistoryMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
class FollowUpPlanQueryContractTest {
  @Test void statusIsBoundAndFilteredBeforeCalendarPagination() {
    String sql = sql(FollowUpPlanMapper.class);
    var values = new HashMap<String,Object>(); values.put("status","SCHEDULED"); values.put("limit",51);
    var bound = new XMLLanguageDriver().createSqlSource(new Configuration(),sql,Map.class).getBoundSql(values);
    assertThat(bound.getSql()).contains("p.status=?");
    assertThat(bound.getSql().indexOf("p.status=?")).isLessThan(bound.getSql().indexOf("LIMIT"));
    assertThat(bound.getParameterMappings()).anyMatch(parameter -> parameter.getProperty().equals("status"));
    assertThat(bound.getSql()).doesNotContain("SCHEDULED");
  }
  @Test void historyQueryUsesApplicationScopeAndStableCursorWithoutChangingSnapshots() {
    String sql = sql(FollowUpPlanHistoryMapper.class);
    var values = new HashMap<String,Object>(); values.put("applicationId","A".repeat(26)); values.put("limit",51);
    values.put("cursorTime",Instant.parse("2026-09-09T01:00:00Z")); values.put("cursorId","H".repeat(26));
    var bound = new XMLLanguageDriver().createSqlSource(new Configuration(),sql,Map.class).getBoundSql(values);
    assertThat(bound.getSql()).contains("p.application_id = ?","h.create_time < ?","h.id < ?","ORDER BY h.create_time DESC, h.id DESC LIMIT ?");
    assertThat(bound.getSql()).doesNotContain("A".repeat(26));
  }
  private String sql(Class<?> mapper) {
    return Arrays.stream(mapper.getDeclaredMethods()).filter(method -> method.isAnnotationPresent(Select.class))
        .map(method -> String.join("\n",method.getAnnotation(Select.class).value())).findFirst().orElseThrow();
  }
}
