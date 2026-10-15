package com.ezaigc.hr.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class LoggingArchitectureTest {

  private final JavaClasses productionClasses =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.ezaigc.hr");

  @Test
  void productionCodeDoesNotUseSystemLogger() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("java.lang.System$Logger")
        .because("生产日志必须统一经过 SLF4J 门面")
        .check(productionClasses);
  }

  @Test
  void productionCodeDoesNotDependOnAlternativeLoggingApisOrImplementations() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "java.util.logging..",
            "org.apache.commons.logging..",
            "org.apache.logging.log4j..",
            "ch.qos.logback..")
        .because("业务代码只能依赖 SLF4J API，日志实现由运行时组合根选择")
        .check(productionClasses);
  }
}
