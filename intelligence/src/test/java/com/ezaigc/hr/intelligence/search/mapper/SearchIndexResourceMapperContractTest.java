package com.ezaigc.hr.intelligence.search.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.ezaigc.hr.intelligence.search.enums.SearchIndexResourceStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SearchIndexResourceMapperContractTest {
  private final MybatisConfiguration configuration = configuration();

  @Test
  void capacityReadsUseCurrentRowLocksAfterAnEarlierRepeatableReadSnapshot() {
    String live = sql("liveCount", Map.of("deleted", SearchIndexResourceStatus.DELETED.getCode()));
    String unresolved = sql("unresolvedCount", Map.of("creating", SearchIndexResourceStatus.CREATING.getCode(),
        "unknown", SearchIndexResourceStatus.CREATE_UNKNOWN.getCode()));
    assertThat(live).contains("select id", "status <> ?", "order by status,id", "limit 101 for update").doesNotContain("count(");
    assertThat(unresolved).contains("status in (?,?)", "order by status,id", "limit 11 for update").doesNotContain("count(");
  }

  @Test
  void retirementAtomicallyChecksTechnicalVersionManagedStateAndDatabasePinExpiry() {
    String statement = sql("retiring", Map.of("id", "resource", "version", 8, "actor", "actor",
        "ready", SearchIndexResourceStatus.READY.getCode(), "retiring", SearchIndexResourceStatus.RETIRING.getCode()));
    assertThat(statement).contains("version=version+1", "version=?", "managed=1", "status=?",
        "retired_at=current_timestamp(6)", "update_time=current_timestamp(6)",
        "pinned_until is null or pinned_until<=current_timestamp(6)").doesNotContain("utc_timestamp");
  }

  @Test
  void aRequestPinNeverShortensPriorPinsAndDoesNotInvalidateCreationOrAuditCasVersions() {
    String statement = sql("pin", Map.of("id", "resource", "micros", 120_000_000L,
        "ready", SearchIndexResourceStatus.READY.getCode()));
    assertThat(statement).contains("greatest(coalesce(pinned_until,current_timestamp(6))",
        "timestampadd(microsecond,?,current_timestamp(6))", "status=?").doesNotContain("version=", "utc_timestamp");
  }

  @Test
  void candidateAgesUseTheTimestampSessionClockRatherThanUtcWallClock() {
    String statement = sql("candidates", Map.of("ageSeconds", 600L, "limit", 8,
        "ready", SearchIndexResourceStatus.READY.getCode(), "retiring", SearchIndexResourceStatus.RETIRING.getCode()));
    assertThat(statement).contains("ready_at<=timestampadd(second,-?,current_timestamp(6))")
        .doesNotContain("utc_timestamp");
  }

  private String sql(String method, Map<String, Object> params) {
    return configuration.getMappedStatement(SearchIndexResourceMapper.class.getName() + "." + method)
        .getBoundSql(params).getSql().replaceAll("\\s+", " ").toLowerCase();
  }

  private static MybatisConfiguration configuration() {
    var result = new MybatisConfiguration(); result.addMapper(SearchIndexResourceMapper.class); return result;
  }
}
