package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HttpResourceVersionContractTest {

  private static final Set<String> SCALAR_VERSION_TYPES =
      Set.of(
          String.class.getName(),
          "long",
          Long.class.getName(),
          "int",
          Integer.class.getName(),
          "short",
          Short.class.getName());

  @Test
  void requestAndResponseResourceVersionsUseStringsOnTheWire() {
    List<JavaField> versionFields =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ezaigc.hr")
            .stream()
            .filter(
                javaClass ->
                    javaClass.getPackageName().contains(".domain.request")
                        || javaClass.getPackageName().contains(".domain.response"))
            .filter(
                javaClass ->
                    javaClass.getSimpleName().endsWith("Request")
                        || javaClass.getSimpleName().endsWith("Response"))
            .flatMap(javaClass -> javaClass.getFields().stream())
            .filter(field -> field.getName().equals("version"))
            .filter(field -> SCALAR_VERSION_TYPES.contains(field.getRawType().getName()))
            .toList();

    assertFalse(versionFields.isEmpty(), "未发现 HTTP 资源 version 字段，契约守卫可能失效");

    List<String> violations =
        versionFields.stream()
            .filter(field -> !field.getRawType().isEquivalentTo(String.class))
            .map(field -> field.getFullName() + " -> " + field.getRawType().getName())
            .toList();

    assertTrue(
        violations.isEmpty(),
        () -> "HTTP Request/Response 的资源 version 必须使用十进制字符串:\n" + String.join("\n", violations));
  }
}
