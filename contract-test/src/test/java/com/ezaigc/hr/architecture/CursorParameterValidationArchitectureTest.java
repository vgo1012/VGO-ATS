package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ezaigc.hr.common.web.request.ValidCursor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class CursorParameterValidationArchitectureTest {

  @Test
  void cursorRequestParametersUseSharedBeanValidationConstraint() throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

    List<String> unvalidatedParameters = new ArrayList<>();
    int cursorCount = 0;
    for (var beanDefinition : scanner.findCandidateComponents("com.ezaigc.hr")) {
      Class<?> controller = Class.forName(beanDefinition.getBeanClassName());
      for (Method method : controller.getDeclaredMethods()) {
        for (Parameter parameter : method.getParameters()) {
          if (!isCursor(parameter)) {
            continue;
          }
          cursorCount++;
          if (parameter.getAnnotation(ValidCursor.class) == null) {
            unvalidatedParameters.add(controller.getName() + "#" + method.getName());
          }
        }
      }
    }

    assertTrue(cursorCount > 0, "应发现现有的 cursor 查询参数");
    assertTrue(
        unvalidatedParameters.isEmpty(),
        () ->
            "所有 cursor 查询参数必须声明统一 JSR-303 约束:\n"
                + String.join("\n", unvalidatedParameters));
  }

  private static boolean isCursor(Parameter parameter) {
    RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
    if (requestParam == null || parameter.getType() != String.class) {
      return false;
    }
    String declaredName = requestParam.name().isBlank() ? requestParam.value() : requestParam.name();
    String effectiveName = declaredName.isBlank() ? parameter.getName() : declaredName;
    return "cursor".equals(effectiveName);
  }
}
