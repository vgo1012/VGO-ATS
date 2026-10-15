package com.ezaigc.hr.recruiting.demand.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.identity.api.IdentityDirectoryService;
import com.ezaigc.hr.recruiting.demand.domain.entity.MailJdRoutingRow;
import com.ezaigc.hr.recruiting.demand.service.MailJdRoutingStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class MailJdRoutingAdapterTest {

  @Test
  void selectedRegionOverridesParsedFactsAndLegacyRowsKeepTheirFacts() {
    var mapper = mock(MailJdRoutingStore.class);
    var identities = mock(IdentityDirectoryService.class);
    var adapter = new MailJdRoutingAdapter();
    ReflectionTestUtils.setField(adapter, "mapper", mapper);
    ReflectionTestUtils.setField(adapter, "identities", identities);
    ReflectionTestUtils.setField(adapter, "json", new ObjectMapper());
    when(identities.isRecruiterOwner("owner")).thenReturn(true);
    var selected = row("selected");
    selected.setWorkRegionName("  杭州  ");
    selected.setFactsJson("{\"fulfillmentConditions\":{\"locations\":[\"上海\"]}}");
    var legacy = row("legacy");
    legacy.setFactsJson("{\"fulfillmentConditions\":{\"locations\":[\"东莞\"]}}");
    var noFacts = row("no-facts");
    noFacts.setWorkRegionName("杭州");
    when(mapper.findActiveTargets("PUBLISHED", "ACTIVE")).thenReturn(List.of(selected, legacy, noFacts));

    assertThat(adapter.activeTargets()).extracting(target -> target.regions())
        .containsExactly(List.of("杭州"), List.of("东莞"), List.of("杭州"));
    when(mapper.findCurrentTargetForShare("selected", "PUBLISHED", "ACTIVE")).thenReturn(selected);
    assertThat(adapter.requireCurrent("selected").regions()).containsExactly("杭州");
  }

  private MailJdRoutingRow row(String id) {
    var row = new MailJdRoutingRow();
    row.setJdId(id);
    row.setOwnerUserId("owner");
    row.setOwnerDepartmentId("department");
    return row;
  }
}
