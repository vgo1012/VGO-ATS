package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

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

class QueryParameterValidationArchitectureTest {

  @Test
  void freeTextQueryParametersHaveExplicitBeanValidationBounds() throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

    List<String> unboundedParameters = new ArrayList<>();
    int queryParameterCount = 0;
    for (var beanDefinition : scanner.findCandidateComponents("com.ezaigc.hr")) {
      Class<?> controller = Class.forName(beanDefinition.getBeanClassName());
      for (Method method : controller.getDeclaredMethods()) {
        for (Parameter parameter : method.getParameters()) {
          if (!isFreeTextQuery(parameter)) {
            continue;
          }
          queryParameterCount++;
          Size size = parameter.getAnnotation(Size.class);
          if (size == null || size.max() == Integer.MAX_VALUE) {
            unboundedParameters.add(controller.getName() + "#" + method.getName());
          }
        }
      }
    }

    assertTrue(queryParameterCount > 0, "应发现现有的 query 查询参数");
    assertTrue(
        unboundedParameters.isEmpty(),
        () ->
            "进入数据库或搜索引擎的自由文本 query 必须声明 JSR-303 长度上限:\n"
                + String.join("\n", unboundedParameters));
  }

  private static boolean isFreeTextQuery(Parameter parameter) {
    RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
    if (requestParam == null || parameter.getType() != String.class) {
      return false;
    }
    String declaredName = requestParam.name().isBlank() ? requestParam.value() : requestParam.name();
    String effectiveName = declaredName.isBlank() ? parameter.getName() : declaredName;
    return "query".equals(effectiveName);
  }
}
