package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ezaigc.hr.common.web.request.ExtendedPageLimit;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class PageLimitValidationArchitectureTest {

  @Test
  void numericLimitRequestParametersDeclareBeanValidationRange() throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

    List<String> unboundedParameters = new ArrayList<>();
    int limitCount = 0;
    for (var beanDefinition : scanner.findCandidateComponents("com.ezaigc.hr")) {
      Class<?> controller = Class.forName(beanDefinition.getBeanClassName());
      for (Method method : controller.getDeclaredMethods()) {
        for (Parameter parameter : method.getParameters()) {
          if (!isNumericLimit(parameter)) {
            continue;
          }
          limitCount++;
          if (!hasRangeConstraint(parameter)) {
            unboundedParameters.add(controller.getName() + "#" + method.getName());
          }
        }
      }
    }

    assertTrue(limitCount > 0, "应发现现有的 limit 查询参数");
    assertTrue(
        unboundedParameters.isEmpty(),
        () ->
            "所有数值 limit 查询参数必须声明 JSR-303 最小值和最大值:\n"
                + String.join("\n", unboundedParameters));
  }

  private static boolean hasRangeConstraint(Parameter parameter) {
    return parameter.getAnnotation(StandardPageLimit.class) != null
        || parameter.getAnnotation(ExtendedPageLimit.class) != null
        || (parameter.getAnnotation(Min.class) != null
            && parameter.getAnnotation(Max.class) != null);
  }

  private static boolean isNumericLimit(Parameter parameter) {
    RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
    if (requestParam == null
        || (parameter.getType() != int.class && parameter.getType() != Integer.class)) {
      return false;
    }
    String declaredName = requestParam.name().isBlank() ? requestParam.value() : requestParam.name();
    String effectiveName = declaredName.isBlank() ? parameter.getName() : declaredName;
    return "limit".equals(effectiveName);
  }
}
