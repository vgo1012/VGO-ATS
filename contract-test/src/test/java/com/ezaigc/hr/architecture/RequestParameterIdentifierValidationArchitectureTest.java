package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import jakarta.validation.constraints.Size;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class RequestParameterIdentifierValidationArchitectureTest {

  @Test
  void stringIdentifierRequestParametersDeclarePlatformIdentifierValidation()
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
          if (!isStringIdentifierRequestParameter(parameter)) {
            continue;
          }
          identifierCount++;
          if (!hasIdentifierConstraint(parameter)) {
            unvalidatedParameters.add(
                controller.getName() + "#" + method.getName() + ":" + parameter.getName());
          }
        }
      }
    }

    assertTrue(identifierCount > 0, "应发现现有的标识符查询参数");
    assertTrue(
        unvalidatedParameters.isEmpty(),
        () ->
            "所有 String 类型的 *Id 查询参数必须声明平台标识符校验:\n"
                + String.join("\n", unvalidatedParameters));
  }

  private static boolean hasIdentifierConstraint(Parameter parameter) {
    if (parameter.getAnnotation(PlatformIdentifier.class) != null
        || parameter.getAnnotation(OptionalPlatformIdentifier.class) != null) {
      return true;
    }
    Size size = parameter.getAnnotation(Size.class);
    return size != null && size.max() < Integer.MAX_VALUE;
  }

  private static boolean isStringIdentifierRequestParameter(Parameter parameter) {
    RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
    if (requestParam == null || parameter.getType() != String.class) {
      return false;
    }
    String declaredName = requestParam.name().isBlank() ? requestParam.value() : requestParam.name();
    String effectiveName = declaredName.isBlank() ? parameter.getName() : declaredName;
    return effectiveName.endsWith("Id");
  }
}
