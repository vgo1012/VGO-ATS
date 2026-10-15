package com.ezaigc.hr.bootstrap.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.recruiting.activity.application.manager.ActivityRecordManager;
import com.ezaigc.hr.recruiting.activity.controller.ActivityRecordController;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ActivityRecordControllerMockMvcTest {
  private static final String ID = "01J" + "A".repeat(23);
  private final ActivityRecordManager manager = mock(ActivityRecordManager.class);
  private MockMvc mvc;

  @BeforeEach void setup() {
    var controller = new ActivityRecordController();
    ReflectionTestUtils.setField(controller, "manager", manager);
    mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
  }

  @Test void activityFactsAreNotCachedAndDefaultToTwentyRecords() throws Exception {
    when(manager.records(ID, null, null, 20)).thenReturn(new CursorPage<>(List.of(), null, false));
    mvc.perform(get("/api/v1/applications/{id}/activity-records", ID))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.hasMore").value(false));
    verify(manager).records(ID, null, null, 20);
  }

  @Test void invalidIdentifiersLimitsAndCursorsNeverReachTheQuery() throws Exception {
    for (String limit : List.of("0", "101", "-1", "2147483648")) {
      mvc.perform(get("/api/v1/applications/{id}/activity-records", ID).param("limit", limit))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(get("/api/v1/applications/{id}/activity-records", "invalid")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/applications/{id}/activity-records", ID).param("cursor", "invalid*cursor"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(manager);
  }
}
