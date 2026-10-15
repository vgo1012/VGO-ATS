package com.ezaigc.hr.integration.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ElasticsearchSearchIndexAdapterMappingTest {

  @Test
  void mappingDoesNotCreateReversibleContactIndexes() {
    String mapping =
        (String) ReflectionTestUtils.getField(ElasticsearchSearchIndexAdapter.class, "MAPPING");

    assertThat(mapping).isNotNull()
        .contains("\"contentVersion\":{\"type\":\"long\",\"index\":false}",
            "\"candidateVersion\":{\"type\":\"long\",\"index\":false}")
        .doesNotContain("\"email\"", "\"phone\"");
  }

  @Test
  void mappingUsesConfiguredReplicaCount() {
    var adapter = new ElasticsearchSearchIndexAdapter();
    ReflectionTestUtils.setField(adapter, "numberOfReplicas", 0);

    String mapping = ReflectionTestUtils.invokeMethod(adapter, "mapping");

    assertThat(mapping).contains("\"number_of_replicas\":0").doesNotContain("%d");
  }
}
