package com.ezaigc.hr.bootstrap.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.recruiting.recruitment.application.manager.RecruitmentApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.controller.ApplicationController;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationCommunicationResponse;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApplicationCommunicationControllerTest {
  private static final String ID = "01J" + "A".repeat(23);
  private static final String COMMUNICATION_ID = "01J" + "B".repeat(23);
  private static final String BODY = """
      {"channel":"PHONE","summary":"已沟通","result":"CONTINUE","nextPlan":"安排下一步"}
      """;
  private final RecruitmentApplicationManager manager = mock(RecruitmentApplicationManager.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    ApplicationController controller = new ApplicationController();
    ReflectionTestUtils.setField(controller, "manager", manager);
    mvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new GlobalExceptionHandler()).build();
  }

  @Test
  void rejectsMissingIdempotencyKeyBeforeManager() throws Exception {
    mvc.perform(post("/api/v1/applications/{id}/communications", ID)
        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isBadRequest());
    verifyNoInteractions(manager);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "invalid key", "非法键"})
  void rejectsMalformedIdempotencyKeyBeforeManager(String key) throws Exception {
    mvc.perform(post("/api/v1/applications/{id}/communications", ID)
        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(manager);
  }

  @Test
  void forwardsStableKeyAndNullInputTimeAndReturnsStoredResult() throws Exception {
    Instant when = Instant.parse("2026-09-12T00:00:00Z");
    when(manager.communicateResponse(ID, "PHONE", "已沟通", "CONTINUE", "安排下一步", null, "communication-key"))
        .thenReturn(new ApplicationCommunicationResponse(COMMUNICATION_ID, ID, "PHONE", "已沟通",
            "CONTINUE", "安排下一步", when, ID, when));
    mvc.perform(post("/api/v1/applications/{id}/communications", ID)
        .header("Idempotency-Key", "communication-key")
        .contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(COMMUNICATION_ID));
    verify(manager).communicateResponse(ID, "PHONE", "已沟通", "CONTINUE", "安排下一步", null, "communication-key");
  }
}
