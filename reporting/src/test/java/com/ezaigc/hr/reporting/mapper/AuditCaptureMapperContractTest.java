package com.ezaigc.hr.reporting.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import java.time.Instant;

class AuditCaptureMapperContractTest {

  @Test
  void checkpointInitializationIgnoresOnlyDuplicateKeys() throws NoSuchMethodException {
    Method method =
        AuditCaptureCheckpointMapper.class.getDeclaredMethod(
            "insertIfAbsent",
            String.class,
            String.class,
            String.class,
            String.class,
            String.class,
            Instant.class);
    String sql = sql(method.getAnnotation(Insert.class).value()).toLowerCase();

    assertThat(sql).doesNotContain("insert ignore").contains("on duplicate key update");
  }

  @Test
  void sourcePageQueriesAreKeysetBounded() {
    var methods =
        Arrays.stream(AuditCaptureCheckpointMapper.class.getDeclaredMethods())
            .filter(method -> method.getName().startsWith("next"))
            .toList();

    assertThat(methods).hasSize(8);
    assertThat(methods)
        .allSatisfy(
            method -> {
              String sql = sql(method.getAnnotation(Select.class).value()).toLowerCase();
              assertThat(sql).contains("id > #{afterid}", "order by id", "limit #{limit}");
            });
  }

  @Test
  void projectionWritesUseOnlyTheSelectedKeysetRange() {
    var methods =
        Arrays.stream(AuditCaptureCheckpointMapper.class.getDeclaredMethods())
            .filter(method -> method.getName().startsWith("capture"))
            .toList();

    assertThat(methods).hasSize(8);
    assertThat(methods)
        .allSatisfy(
            method -> {
              String sql = sql(method.getAnnotation(Insert.class).value()).toLowerCase();
              assertThat(sql).contains("id > #{afterid}", "id <= #{throughid}");
            });
  }

  private static String sql(String[] fragments) {
    return String.join(" ", fragments).replaceAll("\\s+", " ");
  }
}
