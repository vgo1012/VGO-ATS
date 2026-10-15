package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Annotation;
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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class RequestBodyTextLengthValidationArchitectureTest {

  @Test
  void requestBodyTextFieldsDeclareAFiniteUpperBound() throws ClassNotFoundException {
    Set<Class<?>> rootRequestTypes = requestBodyTypes();
    Set<Class<?>> visitedTypes = new LinkedHashSet<>();
    Deque<Class<?>> pendingTypes = new ArrayDeque<>(rootRequestTypes);
    List<String> unboundedFields = new ArrayList<>();
    int[] textFieldCount = {0};

    while (!pendingTypes.isEmpty()) {
      Class<?> requestType = pendingTypes.removeFirst();
      if (!visitedTypes.add(requestType)) {
        continue;
      }
      for (Field field : requestType.getDeclaredFields()) {
        if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        inspectField(requestType, field, pendingTypes, unboundedFields, textFieldCount);
      }
    }

    assertTrue(textFieldCount[0] > 0, "应发现现有请求体中的文本字段");
    assertTrue(
        unboundedFields.isEmpty(),
        () ->
            "请求体文本必须通过 @Size 或组合约束声明有限上限，避免超大字符串放大内存和下游处理:\n"
                + String.join("\n", unboundedFields));
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

  private static void inspectField(
      Class<?> requestType,
      Field field,
      Deque<Class<?>> pendingTypes,
      List<String> unboundedFields,
      int[] textFieldCount) {
    if (field.getType() == String.class) {
      recordTextField(
          requestType, field.getName(), field.getAnnotations(), unboundedFields, textFieldCount);
      return;
    }
    if (field.getAnnotatedType() instanceof AnnotatedParameterizedType parameterizedType) {
      inspectTypeArgument(
          requestType,
          field,
          parameterizedType.getAnnotatedActualTypeArguments(),
          pendingTypes,
          unboundedFields,
          textFieldCount);
      return;
    }
    if (field.getAnnotation(Valid.class) != null && isApplicationType(field.getType())) {
      pendingTypes.addLast(field.getType());
    }
  }

  private static void inspectTypeArgument(
      Class<?> requestType,
      Field field,
      AnnotatedType[] typeArguments,
      Deque<Class<?>> pendingTypes,
      List<String> unboundedFields,
      int[] textFieldCount) {
    if (!Collection.class.isAssignableFrom(field.getType()) || typeArguments.length != 1) {
      return;
    }
    AnnotatedType elementType = typeArguments[0];
    if (elementType.getType() == String.class) {
      recordTextField(
          requestType,
          field.getName() + "[]",
          elementType.getAnnotations(),
          unboundedFields,
          textFieldCount);
      return;
    }
    if ((field.getAnnotation(Valid.class) != null || elementType.getAnnotation(Valid.class) != null)
        && elementType.getType() instanceof Class<?> elementClass
        && isApplicationType(elementClass)) {
      pendingTypes.addLast(elementClass);
    }
  }

  private static void recordTextField(
      Class<?> requestType,
      String fieldName,
      Annotation[] annotations,
      List<String> unboundedFields,
      int[] textFieldCount) {
    textFieldCount[0]++;
    if (!hasFiniteSize(annotations)) {
      unboundedFields.add(requestType.getName() + "#" + fieldName);
    }
  }

  private static boolean hasFiniteSize(Annotation[] annotations) {
    for (Annotation annotation : annotations) {
      Size size = annotation.annotationType().getAnnotation(Size.class);
      if (size == null && annotation instanceof Size directSize) {
        size = directSize;
      }
      if (size != null && size.max() < Integer.MAX_VALUE) {
        return true;
      }
    }
    return false;
  }

  private static boolean isApplicationType(Class<?> type) {
    Package typePackage = type.getPackage();
    return typePackage != null && typePackage.getName().startsWith("com.ezaigc.hr");
  }
}
