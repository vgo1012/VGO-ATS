package com.ezaigc.hr.intelligence.matching.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class RemovedMatchingEndpointsTest {
  @Test void historyAndFeedbackEndpointsStayRemoved() {
    var methods = Arrays.asList(MatchRunController.class.getDeclaredMethods());

    assertThat(methods).noneMatch(method -> hasPath(method.getAnnotation(GetMapping.class), "/match-runs"));
    assertThat(methods).noneMatch(method -> hasPath(method.getAnnotation(PostMapping.class), "/base-evaluations/{id}/feedback"));
    assertThatThrownBy(() -> Class.forName("com.ezaigc.hr.intelligence.matching.controller.MatchFeedbackController"))
        .isInstanceOf(ClassNotFoundException.class);
  }

  private static boolean hasPath(GetMapping mapping, String path) {
    return mapping != null && Arrays.asList(mapping.value()).contains(path);
  }

  private static boolean hasPath(PostMapping mapping, String path) {
    return mapping != null && Arrays.asList(mapping.value()).contains(path);
  }
}
