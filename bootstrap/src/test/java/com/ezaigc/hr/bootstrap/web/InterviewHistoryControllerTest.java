package com.ezaigc.hr.bootstrap.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.recruiting.interview.application.manager.InterviewApplicationManager;
import com.ezaigc.hr.recruiting.interview.controller.InterviewController;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewDetailsResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InterviewHistoryControllerTest {
  private static final String ID = "01J" + "A".repeat(23);
  private final InterviewApplicationManager manager = mock(InterviewApplicationManager.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    InterviewController controller = new InterviewController();
    ReflectionTestUtils.setField(controller, "manager", manager);
    mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
  }

  @ParameterizedTest
  @ValueSource(strings = {"feedback", "notifications", "schedule-history"})
  void returnsTheCommonCursorEnvelopeWithDefaultLimit(String kind) throws Exception {
    when(manager.feedbackResponses(ID, null, 20)).thenReturn(new CursorPage<>(List.of(), null, false));
    when(manager.notificationResponses(ID, null, 20)).thenReturn(new CursorPage<>(List.of(), null, false));
    when(manager.historyResponses(ID, null, 20)).thenReturn(new CursorPage<>(List.of(), null, false));
    mvc.perform(get("/api/v1/interviews/{id}/" + kind, ID)).andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray()).andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.hasMore").value(false)).andExpect(jsonPath("$.nextCursor").isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"feedback", "notifications", "schedule-history"})
  void rejectsMalformedBoundariesBeforeCallingTheManager(String kind) throws Exception {
    for (String limit : List.of("0", "101", "-1", "2147483648")) {
      mvc.perform(get("/api/v1/interviews/{id}/" + kind, ID).param("limit", limit)).andExpect(status().isBadRequest());
    }
    mvc.perform(get("/api/v1/interviews/{id}/" + kind, ID).param("cursor", "invalid*cursor")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/interviews/{id}/" + kind, "invalid")).andExpect(status().isBadRequest());
    verifyNoInteractions(manager);
  }

  @Test
  void summaryJsonDoesNotKeepEmptyLegacyHistoryFields() throws Exception {
    when(manager.getResponse(ID)).thenReturn(new InterviewDetailsResponse(null, List.of()));
    mvc.perform(get("/api/v1/interviews/{id}", ID)).andExpect(status().isOk())
        .andExpect(jsonPath("$.participants").isArray()).andExpect(jsonPath("$.feedback").doesNotExist())
        .andExpect(jsonPath("$.scheduleHistory").doesNotExist()).andExpect(jsonPath("$.notifications").doesNotExist());
  }
  @Test
  void forwardsExactWorkflowStepFilterAndRejectsMalformedIds() throws Exception {
    when(manager.listResponses(null, null, null, null, null, null, null, null, null, null,
        20, null, null, ID, ID)).thenReturn(new CursorPage<>(List.of(), null, false));
    mvc.perform(get("/api/v1/interviews").param("applicationId", ID).param("workflowStepId", ID))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    verify(manager).listResponses(null, null, null, null, null, null, null, null, null, null,
        20, null, null, ID, ID);
    mvc.perform(get("/api/v1/interviews").param("workflowStepId", "invalid"))
        .andExpect(status().isBadRequest());
  }

}
