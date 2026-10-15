package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class RequestBodyVersionValidationArchitectureTest {

  @Test
  void requestBodyResourceVersionsUseTheSharedConstraint() throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

    Set<Class<?>> requestBodyTypes = new LinkedHashSet<>();
    for (var beanDefinition : scanner.findCandidateComponents("com.ezaigc.hr")) {
      Class<?> controller = Class.forName(beanDefinition.getBeanClassName());
      for (Method method : controller.getDeclaredMethods()) {
        for (Parameter parameter : method.getParameters()) {
          if (parameter.getAnnotation(RequestBody.class) != null) {
            requestBodyTypes.add(parameter.getType());
          }
        }
      }
    }

    List<String> invalidFields = new ArrayList<>();
    int versionFieldCount = 0;
    for (Class<?> requestBodyType : requestBodyTypes) {
      for (Field field : requestBodyType.getDeclaredFields()) {
        if (!field.getName().equals("version")) {
          continue;
        }
        versionFieldCount++;
        if (field.getAnnotation(ValidResourceVersion.class) == null) {
          invalidFields.add(requestBodyType.getName() + "#" + field.getName());
        }
      }
    }

    assertTrue(versionFieldCount > 0, "应发现现有请求体中的资源版本字段");
    assertTrue(
        invalidFields.isEmpty(),
        () ->
            "请求体 version 字段必须使用 @ValidResourceVersion，避免格式错误或 long 溢出进入业务层:\n"
                + String.join("\n", invalidFields));
  }
}
