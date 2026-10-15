package com.ezaigc.hr.intelligence.search.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.pagination.CursorCount;
import com.ezaigc.hr.intelligence.search.application.assembler.TalentSearchAssembler;
import com.ezaigc.hr.intelligence.search.application.manager.TalentSearchApplicationManager;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchCriteria;
import com.ezaigc.hr.intelligence.search.domain.request.TalentSearchRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import tools.jackson.databind.ObjectMapper;

class TalentSearchCountControllerTest {
  @Test
  void countRoutePreservesSearchFiltersAndSerializesOnlyCountMetadata() throws Exception {
    var controller = new TalentSearchController();
    var manager = mock(TalentSearchApplicationManager.class);
    ReflectionTestUtils.setField(controller, "assembler", new TalentSearchAssembler());
    ReflectionTestUtils.setField(controller, "manager", manager);
    var criteria = ArgumentCaptor.forClass(SearchCriteria.class);
    when(manager.count(criteria.capture(), eq("next"))).thenReturn(new CursorCount(3, "after", true));
    var request = new TalentSearchRequest();
    request.setQ("工程师");
    request.setConditions(List.of("Java", "上海"));
    request.setScope("PERSONAL_POOL");
    request.setPoolStage("PENDING_SCREENING");
    request.setJdId("jd-selected");
    request.setCursor("next");

    var response = controller.count(request);

    assertThat(criteria.getValue().getQuery()).isEqualTo("工程师");
    assertThat(criteria.getValue().getConditions()).containsExactly("Java", "上海");
    assertThat(criteria.getValue().getScope()).isEqualTo("PERSONAL_POOL");
    assertThat(criteria.getValue().getPoolStage()).isEqualTo("PENDING_SCREENING");
    assertThat(criteria.getValue().getJdId()).isEqualTo("jd-selected");
    var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(response));
    assertThat(json.size()).isEqualTo(3);
    assertThat(json.path("count").asLong()).isEqualTo(3);
    assertThat(json.path("nextCursor").asString()).isEqualTo("after");
    assertThat(json.path("hasMore").asBoolean()).isTrue();
    assertThat(TalentSearchController.class.getDeclaredMethod("count", TalentSearchRequest.class)
        .getAnnotation(GetMapping.class).value()).containsExactly("/talent-search/count");
  }
}
