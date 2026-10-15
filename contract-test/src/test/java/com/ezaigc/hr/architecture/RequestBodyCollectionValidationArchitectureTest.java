package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class RequestBodyCollectionValidationArchitectureTest {

  @Test
  void requestBodyCollectionsDeclareAnUpperBound() throws ClassNotFoundException {
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

    Deque<Class<?>> pendingTypes = new ArrayDeque<>(requestBodyTypes);
    Set<Class<?>> visitedTypes = new LinkedHashSet<>();
    List<String> unboundedFields = new ArrayList<>();
    int collectionFieldCount = 0;
    while (!pendingTypes.isEmpty()) {
      Class<?> requestBodyType = pendingTypes.removeFirst();
      if (!visitedTypes.add(requestBodyType)) {
        continue;
      }
      for (Field field : requestBodyType.getDeclaredFields()) {
        if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        if (Collection.class.isAssignableFrom(field.getType())
            || Map.class.isAssignableFrom(field.getType())) {
          collectionFieldCount++;
          Size size = field.getAnnotation(Size.class);
          if (size == null || size.max() == Integer.MAX_VALUE) {
            unboundedFields.add(requestBodyType.getName() + "#" + field.getName());
          }
        }
        enqueueCascadedTypes(field, pendingTypes);
      }
    }

    assertTrue(collectionFieldCount > 0, "应发现现有请求体中的集合字段");
    assertTrue(
        unboundedFields.isEmpty(),
        () ->
            "请求体集合必须通过 @Size 声明有限上限，避免超大请求放大内存和下游处理:\n"
                + String.join("\n", unboundedFields));
  }

  private static void enqueueCascadedTypes(Field field, Deque<Class<?>> pendingTypes) {
    if (field.getAnnotation(Valid.class) != null && isApplicationType(field.getType())) {
      pendingTypes.addLast(field.getType());
    }
    if (!(field.getAnnotatedType() instanceof AnnotatedParameterizedType parameterizedType)) {
      return;
    }
    for (AnnotatedType argument : parameterizedType.getAnnotatedActualTypeArguments()) {
      if ((field.getAnnotation(Valid.class) != null || argument.getAnnotation(Valid.class) != null)
          && argument.getType() instanceof Class<?> argumentClass
          && isApplicationType(argumentClass)) {
        pendingTypes.addLast(argumentClass);
      }
    }
  }

  private static boolean isApplicationType(Class<?> type) {
    Package typePackage = type.getPackage();
    return typePackage != null && typePackage.getName().startsWith("com.ezaigc.hr");
  }
}
