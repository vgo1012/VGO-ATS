package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Constraint;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class RequestParameterConstraintArchitectureTest {

  @Test
  void scalarRequestParametersDeclareBeanValidationConstraints() throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

    List<String> unvalidatedParameters = new ArrayList<>();
    int scalarParameterCount = 0;
    for (var beanDefinition : scanner.findCandidateComponents("com.ezaigc.hr")) {
      Class<?> controller = Class.forName(beanDefinition.getBeanClassName());
      for (Method method : controller.getDeclaredMethods()) {
        for (Parameter parameter : method.getParameters()) {
          if (!isScalarRequestParameter(parameter)) {
            continue;
          }
          scalarParameterCount++;
          if (!hasBeanValidationConstraint(parameter)) {
            unvalidatedParameters.add(
                controller.getName() + "#" + method.getName() + ":" + parameter.getName());
          }
        }
      }
    }

    assertTrue(scalarParameterCount > 0, "应发现现有的标量查询参数");
    assertTrue(
        unvalidatedParameters.isEmpty(),
        () ->
            "所有 String/数值查询参数必须声明至少一个 JSR-303 约束:\n"
                + String.join("\n", unvalidatedParameters));
  }

  private static boolean hasBeanValidationConstraint(Parameter parameter) {
    return Arrays.stream(parameter.getAnnotations())
        .map(Annotation::annotationType)
        .anyMatch(annotationType -> annotationType.getAnnotation(Constraint.class) != null);
  }

  private static boolean isScalarRequestParameter(Parameter parameter) {
    if (parameter.getAnnotation(RequestParam.class) == null) {
      return false;
    }
    Class<?> type = parameter.getType();
    return type == String.class
        || type == byte.class
        || type == short.class
        || type == int.class
        || type == long.class
        || Number.class.isAssignableFrom(type);
  }
}
