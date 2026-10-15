package com.ezaigc.hr.architecture;

import static com.ezaigc.hr.common.web.request.RequiredIdentifier.IDENTIFIER_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.constraints.Size;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class RequestBodyIdentifierValidationArchitectureTest {

  private static final Set<String> NON_PLATFORM_IDENTIFIER_FIELDS =
      Set.of(
          "com.ezaigc.hr.recruiting.demand.domain.request.CreateJdResumeSourceRequest#sourceMessageId",
          "com.ezaigc.hr.recruiting.interview.domain.request.InterviewCreateRequest#timezoneId",
          "com.ezaigc.hr.recruiting.interview.domain.request.InterviewUpdateRequest#timezoneId",
          "com.ezaigc.hr.recruiting.interview.domain.request.InterviewRescheduleRequest#timezoneId",
          "com.ezaigc.hr.recruiting.interview.domain.request.InterviewManualSendRequest#externalMessageId",
          "com.ezaigc.hr.recruiting.interview.domain.request.InterviewExternalCalendarReconciliationRequest#externalObjectId");

  @Test
  void reconciledExternalCalendarIdentifiersRetainTheirVendorLengthBoundary()
      throws ReflectiveOperationException {
    Class<?> request = Class.forName(
        "com.ezaigc.hr.recruiting.interview.domain.request.InterviewExternalCalendarReconciliationRequest");
    Size boundary = request.getDeclaredField("externalObjectId").getAnnotation(Size.class);
    assertTrue(boundary != null, "外部对象标识仍须有独立长度上限");
    assertEquals(200, boundary.max(), "企微标识不能错误使用平台26位长度");
  }

  @Test
  void requestBodyIdentifiersUseThePlatformLengthContract() throws ClassNotFoundException {
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
    int identifierFieldCount = 0;
    for (Class<?> requestBodyType : requestBodyTypes) {
      for (Field field : requestBodyType.getDeclaredFields()) {
        if (field.getType() == String.class && field.getName().endsWith("Id")) {
          String fieldKey = requestBodyType.getName() + "#" + field.getName();
          if (NON_PLATFORM_IDENTIFIER_FIELDS.contains(fieldKey)) {
            continue;
          }
          identifierFieldCount++;
          if (!hasExactIdentifierLength(field.getAnnotations())) {
            invalidFields.add(fieldKey);
          }
          continue;
        }
        if (Collection.class.isAssignableFrom(field.getType()) && field.getName().endsWith("Ids")) {
          identifierFieldCount++;
          if (!hasConstrainedStringElement(field.getAnnotatedType())) {
            invalidFields.add(requestBodyType.getName() + "#" + field.getName() + "[]");
          }
        }
      }
    }

    assertTrue(identifierFieldCount > 0, "应发现现有请求体中的平台标识字段");
    assertTrue(
        invalidFields.isEmpty(),
        () ->
            "请求体平台标识必须在 HTTP 边界约束为 26 位，避免无效值进入查询和业务流程:\n"
                + String.join("\n", invalidFields));
  }

  private static boolean hasConstrainedStringElement(AnnotatedType annotatedType) {
    if (!(annotatedType instanceof AnnotatedParameterizedType parameterizedType)) {
      return false;
    }
    AnnotatedType[] typeArguments = parameterizedType.getAnnotatedActualTypeArguments();
    return typeArguments.length == 1
        && typeArguments[0].getType() == String.class
        && hasExactIdentifierLength(typeArguments[0].getAnnotations());
  }

  private static boolean hasExactIdentifierLength(Annotation[] annotations) {
    for (Annotation annotation : annotations) {
      Size size = annotation.annotationType().getAnnotation(Size.class);
      if (size == null && annotation instanceof Size directSize) {
        size = directSize;
      }
      if (size != null && size.min() == IDENTIFIER_LENGTH && size.max() == IDENTIFIER_LENGTH) {
        return true;
      }
    }
    return false;
  }
}
