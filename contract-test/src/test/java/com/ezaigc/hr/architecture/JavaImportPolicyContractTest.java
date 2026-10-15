package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JavaImportPolicyContractTest {
  @Test
  void productionAndTestSourcesUseImportsForUnambiguousTypes() throws IOException {
    Path root = repositoryRoot();
    List<String> violations = JavaImportPolicy.scan(JavaImportPolicy.repositorySources(root)).stream()
        .flatMap(source -> source.violations().stream().map(reference ->
            root.relativize(source.path()) + ":" + reference.line() + " -> " + reference.name()))
        .toList();
    assertTrue(violations.isEmpty(), () ->
        "请使用 import 和类型短名；只有真实同名冲突允许完整包名:\n" + String.join("\n", violations));
  }

  @Test
  void detectsAnnotationsSignaturesStaticCallsClassLiteralsAndNestedTypes() throws IOException {
    var source = inspect("""
        package demo;
        @org.example.Annotation
        class Example {
          java.time.Instant instant = java.time.Instant.now();
          java.util.List<String> items;
          Class<?> type = java.lang.String.class;
          java.util.Map.Entry<String, String> entry;
        }
        """);
    assertEquals(6, source.violations().size());
    String fixed = source.normalized();
    assertTrue(fixed.contains("import org.example.Annotation;"));
    assertTrue(fixed.contains("Instant instant = Instant.now()"));
    assertTrue(fixed.contains("Map.Entry<String, String>"));
    assertTrue(inspect(fixed).violations().isEmpty());
  }

  @Test
  void leavesSqlReflectionStringsCommentsAndInstanceMembersUntouched() throws IOException {
    var source = inspect("""
        package demo;
        // java.time.Instant must stay in this comment.
        /** See java.util.List. */
        class Example {
          String name = "com.example.Entity";
          String sql = ""\"
            SELECT 'java.util.List' FROM sample
            ""\";
          Object holder;
          Object value = holder.inner.CONSTANT;
        }
        """);
    assertTrue(source.violations().isEmpty());
    assertEquals(source.text(), source.normalized());
  }

  @Test
  void preservesRealImportAndTypeParameterCollisions() throws IOException {
    var source = inspect("""
        package demo;
        import java.util.Date;
        class Example<Instant> {
          java.sql.Date sqlDate;
          java.util.Date utilDate;
          java.time.Instant instant;
        }
        """);
    assertEquals(1, source.violations().size());
    assertEquals(2, source.collisions().size());
    assertTrue(source.normalized().contains("java.sql.Date sqlDate"));
    assertTrue(source.normalized().contains("java.time.Instant instant"));
    assertTrue(inspect(source.normalized()).violations().isEmpty());
  }

  @Test
  void choosesOneImportWhenTwoQualifiedTypesShareAName() throws IOException {
    var source = inspect("package demo; class Example { java.util.Date a; java.sql.Date b; }");
    String fixed = source.normalized();
    assertTrue(fixed.contains("import java.util.Date;"));
    assertTrue(fixed.contains("java.sql.Date b"));
    assertTrue(inspect(fixed).violations().isEmpty());
    assertEquals(fixed, inspect(fixed).normalized());
  }

  @Test
  void samePackageAndJavaLangReferencesNeedNoImport() throws IOException {
    var source = inspect("package demo; class Example { demo.Example self; java.lang.String name; }");
    assertEquals(2, source.violations().size());
    assertFalse(source.normalized().contains("import "));
    assertTrue(source.normalized().contains("Example self; String name;"));
  }

  @Test
  void wildcardImportsDoNotChangeAnExistingTypeBinding() throws IOException {
    var source = inspect("package demo; import java.util.*; class Example { Date a; java.sql.Date b; }");
    assertEquals(1, source.collisions().size());
    assertTrue(source.violations().isEmpty());
  }

  @Test
  void preservesImplicitJavaLangAndSamePackageTypeBindings() throws IOException {
    var sources = JavaImportPolicy.scan(Map.of(
        Path.of("Peer.java"), "package demo; class Peer {}",
        Path.of("Example.java"),
        "package demo; class Example { Peer local; other.Peer external; other.String text; }"));
    var source = sources.stream().filter(value -> value.path().toString().equals("Example.java"))
        .findFirst().orElseThrow();
    assertEquals(2, source.collisions().size());
    assertTrue(source.violations().isEmpty());
  }

  @Test
  void packageAnnotationsAreCheckedWithoutMovingThePackageDeclaration() throws IOException {
    var source = inspect("@org.example.Annotation package demo;");
    assertEquals(1, source.violations().size());
    assertTrue(source.normalized().startsWith("@Annotation package demo;"));
    assertTrue(inspect(source.normalized()).violations().isEmpty());
  }

  private static JavaImportPolicy.Source inspect(String source) throws IOException {
    return JavaImportPolicy.scan(Map.of(Path.of("Example.java"), source)).getFirst();
  }

  private static Path repositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isDirectory(current.resolve("contract-test"))
          && Files.isRegularFile(current.resolve("pom.xml"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("无法定位仓库根目录");
  }
}
