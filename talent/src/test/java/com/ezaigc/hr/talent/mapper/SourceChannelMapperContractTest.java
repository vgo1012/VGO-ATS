package com.ezaigc.hr.talent.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.Test;

class SourceChannelMapperContractTest {

  @Test
  void defaultChannelSeedAtomicallyHonorsDeletionTombstone() throws NoSuchMethodException {
    Insert insert =
        SourceChannelMapper.class
            .getMethod(
                "insertIfAbsent",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Instant.class)
            .getAnnotation(Insert.class);
    String sql = String.join(" ", Arrays.asList(insert.value()));

    assertThat(sql)
        .contains("WHERE NOT EXISTS")
        .contains("event_type = 'SOURCE_CHANNEL_DELETED'")
        .contains("target_type = 'SOURCE_CHANNEL'")
        .contains("target_id = #{id}");
  }
}
