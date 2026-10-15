package com.ezaigc.hr.bootstrap.web;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.recruiting.submission.application.assembler.SubmissionAssembler;
import com.ezaigc.hr.recruiting.submission.application.manager.SubmissionApplicationManager;
import com.ezaigc.hr.recruiting.submission.controller.SubmissionController;
import com.ezaigc.hr.recruiting.submission.domain.dto.DisableClientRestrictionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SubmissionRestrictionIntentControllerTest {
  private static final String ID = "01J" + "A".repeat(23);
  private final SubmissionApplicationManager manager = mock(SubmissionApplicationManager.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    SubmissionController controller = new SubmissionController();
    ReflectionTestUtils.setField(controller, "manager", manager);
    ReflectionTestUtils.setField(controller, "assembler", new SubmissionAssembler());
    mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
  }

  @ParameterizedTest
  @ValueSource(strings = {"DO_NOT_SUBMIT", "CLIENT_EXPLICIT_BAN"})
  void existingPageCodesReachBothWriteCommandsUnchanged(String intent) throws Exception {
    mvc.perform(post("/api/v1/client-submissions/{id}/feedback", ID).header("Idempotency-Key", "intent-contract")
        .contentType(MediaType.APPLICATION_JSON).content(feedbackBody(intent))).andExpect(status().isCreated());
    verify(manager).addFeedback(eq(ID), argThat(value -> intent.equals(value.getRestrictionIntent())), eq("intent-contract"));
    mvc.perform(post("/api/v1/client-restrictions/{id}/corrections", ID).contentType(MediaType.APPLICATION_JSON)
        .content(correctionBody(intent))).andExpect(status().isOk());
    verify(manager).correctRestriction(eq(ID), argThat(value -> intent.equals(value.getRestrictionIntent())));
  }

  @ParameterizedTest
  @ValueSource(strings = {"UNKNOWN", "CLIENT_BAN", "NO_FUTURE_SUBMISSION", ""})
  void invalidCodesAreRejectedBeforeBusinessCommands(String intent) throws Exception {
    mvc.perform(post("/api/v1/client-submissions/{id}/feedback", ID).header("Idempotency-Key", "intent-contract")
        .contentType(MediaType.APPLICATION_JSON).content(feedbackBody(intent))).andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/client-restrictions/{id}/corrections", ID).contentType(MediaType.APPLICATION_JSON)
        .content(correctionBody(intent))).andExpect(status().isBadRequest());
    verifyNoInteractions(manager);
  }

  @Test
  void revisionHeadersReachIdempotentCommandsAndLegacyDisableStillWorks() throws Exception {
    mvc.perform(post("/api/v1/client-restrictions/{id}/corrections", ID).header("Idempotency-Key", "correction-once")
        .contentType(MediaType.APPLICATION_JSON).content(correctionBody("DO_NOT_SUBMIT"))).andExpect(status().isOk());
    verify(manager).correctRestriction(eq(ID), argThat(value -> "DO_NOT_SUBMIT".equals(value.getRestrictionIntent())), eq("correction-once"));
    mvc.perform(post("/api/v1/client-restrictions/{id}/disable", ID).header("Idempotency-Key", "disable-once")
        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"解除\"}")).andExpect(status().isOk());
    verify(manager).disableRestriction(eq(ID), argThat(value -> "解除".equals(value.getReason())), eq("disable-once"));
    mvc.perform(post("/api/v1/client-restrictions/{id}/disable", ID)
        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"旧页面解除\"}")).andExpect(status().isOk());
    verify(manager).disableRestriction(eq(ID), argThat((DisableClientRestrictionDTO value) -> "旧页面解除".equals(value.getReason())));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "bad key", "invalid/key"})
  void malformedRevisionHeadersNeverFallBackToLegacyCommands(String key) throws Exception {
    mvc.perform(post("/api/v1/client-restrictions/{id}/corrections", ID).header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON).content(correctionBody("DO_NOT_SUBMIT"))).andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/client-restrictions/{id}/disable", ID).header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"解除\"}")).andExpect(status().isBadRequest());
    verifyNoInteractions(manager);
  }

  private static String feedbackBody(String intent) {
    return """
        {"result":"REJECTED","reasonCategory":"明确禁推","nextAction":"结束推荐","note":"客户明确拒绝未来推荐",
         "sourceChannel":"EMAIL","explicitClientBan":true,"restrictionIntent":"%s"}
        """.formatted(intent);
  }

  @Test
  void ordinaryFeedbackDoesNotRequireARestrictionIntent() throws Exception {
    mvc.perform(post("/api/v1/client-submissions/{id}/feedback", ID).header("Idempotency-Key", "ordinary-feedback")
        .contentType(MediaType.APPLICATION_JSON).content("""
            {"result":"REJECTED","reasonCategory":"能力不匹配","nextAction":"结束推荐",
             "sourceChannel":"EMAIL","explicitClientBan":false}
            """)).andExpect(status().isCreated());
    verify(manager).addFeedback(eq(ID), argThat(value -> !value.isExplicitClientBan() && value.getRestrictionIntent() == null),
        eq("ordinary-feedback"));
  }

  private static String correctionBody(String intent) {
    return """
        {"restrictionIntent":"%s","reasonDetail":"更正禁推证据","sourceChannel":"EMAIL"}
        """.formatted(intent);
  }
}
