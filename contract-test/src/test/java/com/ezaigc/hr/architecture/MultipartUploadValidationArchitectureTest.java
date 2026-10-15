package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Constraint;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

class MultipartUploadValidationArchitectureTest {

  @Test
  void multipartFilesUseBeanValidationAtTheHttpBoundary() throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
    List<String> unconstrainedParameters = new ArrayList<>();
    int multipartParameterCount = 0;

    for (var beanDefinition : scanner.findCandidateComponents("com.ezaigc.hr")) {
      Class<?> controller = Class.forName(beanDefinition.getBeanClassName());
      for (Method method : controller.getDeclaredMethods()) {
        for (Parameter parameter : method.getParameters()) {
          if (!MultipartFile.class.isAssignableFrom(parameter.getType())) continue;
          multipartParameterCount++;
          if (!hasConstraint(parameter)) {
            unconstrainedParameters.add(controller.getName() + "#" + method.getName());
          }
        }
      }
    }

    assertTrue(multipartParameterCount > 0, "应发现现有 MultipartFile HTTP 入参");
    assertTrue(
        unconstrainedParameters.isEmpty(),
        () ->
            "MultipartFile 入参必须声明 Bean Validation 约束，提前限制空文件、大小和文件名:\n"
                + String.join("\n", unconstrainedParameters));
  }

  private static boolean hasConstraint(Parameter parameter) {
    for (Annotation annotation : parameter.getAnnotations()) {
      if (annotation.annotationType().getAnnotation(Constraint.class) != null) return true;
    }
    return false;
  }
}
