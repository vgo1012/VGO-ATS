package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Valid;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class RequestBodyCascadeValidationArchitectureTest {

  @Test
  void nestedRequestBodyObjectsActivateBeanValidation() throws ClassNotFoundException {
    Deque<Class<?>> pendingTypes = new ArrayDeque<>(requestBodyTypes());
    Set<Class<?>> visitedTypes = new LinkedHashSet<>();
    List<String> missingCascades = new ArrayList<>();
    int nestedTypeCount = 0;

    while (!pendingTypes.isEmpty()) {
      Class<?> requestType = pendingTypes.removeFirst();
      if (!visitedTypes.add(requestType)) {
        continue;
      }
      for (Field field : requestType.getDeclaredFields()) {
        if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        if (isNestedInputType(field.getType())) {
          nestedTypeCount++;
          if (field.getAnnotation(Valid.class) == null) {
            missingCascades.add(requestType.getName() + "#" + field.getName());
          }
          pendingTypes.addLast(field.getType());
        }
        if (field.getAnnotatedType() instanceof AnnotatedParameterizedType parameterizedType) {
          for (AnnotatedType argument : parameterizedType.getAnnotatedActualTypeArguments()) {
            if (argument.getType() instanceof Class<?> argumentClass
                && isNestedInputType(argumentClass)) {
              nestedTypeCount++;
              if (field.getAnnotation(Valid.class) == null
                  && argument.getAnnotation(Valid.class) == null) {
                missingCascades.add(requestType.getName() + "#" + field.getName() + "[]");
              }
              pendingTypes.addLast(argumentClass);
            }
          }
        }
      }
    }

    assertTrue(nestedTypeCount > 0, "应发现现有请求体中的嵌套对象");
    assertTrue(
        missingCascades.isEmpty(),
        () ->
            "请求体嵌套对象必须使用 @Valid 激活级联约束，避免子字段绕过 HTTP 校验:\n"
                + String.join("\n", missingCascades));
  }

  private static Set<Class<?>> requestBodyTypes() throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

    Set<Class<?>> requestTypes = new LinkedHashSet<>();
    for (var beanDefinition : scanner.findCandidateComponents("com.ezaigc.hr")) {
      Class<?> controller = Class.forName(beanDefinition.getBeanClassName());
      for (Method method : controller.getDeclaredMethods()) {
        for (Parameter parameter : method.getParameters()) {
          if (parameter.getAnnotation(RequestBody.class) != null) {
            requestTypes.add(parameter.getType());
          }
        }
      }
    }
    return requestTypes;
  }

  private static boolean isNestedInputType(Class<?> type) {
    Package typePackage = type.getPackage();
    return !type.isEnum()
        && typePackage != null
        && typePackage.getName().startsWith("com.ezaigc.hr");
  }
}
