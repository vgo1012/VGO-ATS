package com.ezaigc.hr.recruiting.activity.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.recruiting.activity.domain.dto.ActivityRecordQuery;
import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ActivityRecordSqlTest {
  @Test void rendersBoundCursorAndFilterWithoutInterpolatingInput() {
    var query = new ActivityRecordQuery("application", "candidate", "INTERVIEW", Instant.EPOCH, "cursor", 100);
    var source = new XMLLanguageDriver().createSqlSource(new Configuration(), ActivityRecordSql.SELECT, Map.class);
    var bound = source.getBoundSql(Map.of("q", query));
    assertThat(bound.getSql()).contains("GROUP BY group_id", "MIN(occurred_at) occurred_at", "WHERE category=?", "ORDER BY occurred_at DESC,id DESC LIMIT ?")
        .contains("COALESCE(CONVERT(o.action USING utf8mb4),f.category) category")
        .contains("JOIN demand_client c ON c.id=e.client_id", "'companyName',c.name", "COALESCE(e.source_application_id,s.application_id)", "CONCAT(o.create_id,':',o.action,':',o.operation_id)", "o.application_id=?", "occurred_at < ?", "e.version_no=f.current_version_no", "f.status='SUBMITTED'");
    assertThat(bound.getParameterMappings()).extracting(value -> value.getProperty())
        .contains("q.applicationId", "q.candidateId", "q.type", "q.cursorTime", "q.cursorId", "q.limit");
  }
}
