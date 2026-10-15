package com.ezaigc.hr.recruiting.interview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.recruiting.interview.application.manager.InterviewApplicationManager;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewFeedbackBatchResponse;
import jakarta.validation.constraints.Size;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

class InterviewFeedbackBatchControllerTest {
  private static final String INTERVIEW_ID = "I".repeat(26);

  @Test
  void endpointUsesBoundedValidatedRepeatedIdsAndNoStoreResponse() throws Exception {
    InterviewApplicationManager manager = mock(InterviewApplicationManager.class);
    InterviewController controller = new InterviewController();
    ReflectionTestUtils.setField(controller, "manager", manager);
    InterviewFeedbackBatchResponse body =
        new InterviewFeedbackBatchResponse(List.of(INTERVIEW_ID), List.of(), null, false);
    when(manager.feedbackBatchResponses(List.of(INTERVIEW_ID), null, 50)).thenReturn(body);

    var response = controller.feedbackBatch(List.of(INTERVIEW_ID), null, 50);

    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(response.getBody()).isSameAs(body);
    verify(manager).feedbackBatchResponses(List.of(INTERVIEW_ID), null, 50);

    Method method = InterviewController.class.getDeclaredMethod(
        "feedbackBatch", List.class, String.class, Integer.class);
    assertThat(method.getAnnotation(GetMapping.class).value())
        .containsExactly("/interview-feedback-batch");
    Size size = method.getParameters()[0].getAnnotation(Size.class);
    assertThat(size.min()).isEqualTo(1);
    assertThat(size.max()).isEqualTo(100);
    RequestParam idsParameter = method.getParameters()[0].getAnnotation(RequestParam.class);
    assertThat(idsParameter.required()).isTrue();
    AnnotatedParameterizedType listType =
        (AnnotatedParameterizedType) method.getParameters()[0].getAnnotatedType();
    assertThat(listType.getAnnotatedActualTypeArguments()[0].isAnnotationPresent(PlatformIdentifier.class))
        .isTrue();
    assertThat(method.getParameters()[1].isAnnotationPresent(ValidCursor.class)).isTrue();
    assertThat(method.getParameters()[2].isAnnotationPresent(StandardPageLimit.class)).isTrue();
  }
}
