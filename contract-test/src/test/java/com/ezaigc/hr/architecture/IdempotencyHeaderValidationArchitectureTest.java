package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

class IdempotencyHeaderValidationArchitectureTest {

  private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

  @Test
  void idempotencyHeadersHaveExplicitBeanValidationConstraints() throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

    List<String> unvalidatedParameters = new ArrayList<>();
    int idempotencyHeaderCount = 0;
    for (var beanDefinition : scanner.findCandidateComponents("com.ezaigc.hr")) {
      Class<?> controller = Class.forName(beanDefinition.getBeanClassName());
      for (Method method : controller.getDeclaredMethods()) {
        for (Parameter parameter : method.getParameters()) {
          if (!isIdempotencyHeader(parameter)) {
            continue;
          }
          idempotencyHeaderCount++;
          if (parameter.getAnnotation(ValidIdempotencyKey.class) == null) {
            unvalidatedParameters.add(controller.getName() + "#" + method.getName());
          }
        }
      }
    }

    assertTrue(idempotencyHeaderCount > 0, "应发现现有的 Idempotency-Key 请求头");
    assertTrue(
        unvalidatedParameters.isEmpty(),
        () ->
            "所有 Idempotency-Key 请求头必须声明统一 JSR-303 格式约束:\n"
                + String.join("\n", unvalidatedParameters));
  }

  private static boolean isIdempotencyHeader(Parameter parameter) {
    RequestHeader requestHeader = parameter.getAnnotation(RequestHeader.class);
    if (requestHeader == null || parameter.getType() != String.class) {
      return false;
    }
    String declaredName = requestHeader.name().isBlank() ? requestHeader.value() : requestHeader.name();
    String effectiveName = declaredName.isBlank() ? parameter.getName() : declaredName;
    return IDEMPOTENCY_HEADER.equalsIgnoreCase(effectiveName);
  }
}
