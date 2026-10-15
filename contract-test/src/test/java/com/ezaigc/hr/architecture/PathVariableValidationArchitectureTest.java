package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

class PathVariableValidationArchitectureTest {

  private static final Set<String> SPECIAL_PATH_VARIABLES = Set.of("action", "roleCode", "token");

  @Test
  void platformIdentifierPathVariablesUseSharedBeanValidationConstraint()
      throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

    List<String> unvalidatedParameters = new ArrayList<>();
    int identifierCount = 0;
    for (var beanDefinition : scanner.findCandidateComponents("com.ezaigc.hr")) {
      Class<?> controller = Class.forName(beanDefinition.getBeanClassName());
      for (Method method : controller.getDeclaredMethods()) {
        for (Parameter parameter : method.getParameters()) {
          if (!isPlatformIdentifier(parameter)) {
            continue;
          }
          identifierCount++;
          if (parameter.getAnnotation(PlatformIdentifier.class) == null) {
            unvalidatedParameters.add(
                controller.getName() + "#" + method.getName() + ":" + effectiveName(parameter));
          }
        }
      }
    }

    assertTrue(identifierCount > 0, "应发现现有的平台资源路径 ID");
    assertTrue(
        unvalidatedParameters.isEmpty(),
        () ->
            "所有平台资源路径 ID 必须声明统一 JSR-303 约束:\n"
                + String.join("\n", unvalidatedParameters));
  }

  private static boolean isPlatformIdentifier(Parameter parameter) {
    return parameter.getType() == String.class
        && parameter.getAnnotation(PathVariable.class) != null
        && !SPECIAL_PATH_VARIABLES.contains(effectiveName(parameter));
  }

  private static String effectiveName(Parameter parameter) {
    PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
    String declaredName = pathVariable.name().isBlank() ? pathVariable.value() : pathVariable.name();
    return declaredName.isBlank() ? parameter.getName() : declaredName;
  }
}
