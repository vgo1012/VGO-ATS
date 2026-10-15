package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SharedHelperArchitectureTest {

  private static final Pattern DUPLICATE_MANAGER_HELPER =
      Pattern.compile(
          "(?m)private\\s+static\\s+(?:<T>\\s+)?[^\\r\\n{]+\\s+"
              + "(?:requireText|requireOneOf|requireChanged|json|jsonArray|quote|escape|"
              + "safeSteps|optional|normalizeNullable|versionConflict|page)\\s*\\(");
  private static final Pattern DUPLICATE_REQUIRED_TEXT =
      Pattern.compile(
          "(?m)private\\s+static\\s+String\\s+required\\s*\\("
              + "String\\s+\\w+\\s*,\\s*String\\s+field\\s*,\\s*int\\s+\\w+\\s*\\)");
  private static final String EXPERIMENTAL_LOMBOK_DELEGATE = "lombok.experimental.Delegate";
  private static final Pattern RAW_PENDING_STATUS = Pattern.compile("[\"']PENDING[\"']");
  private static final Pattern RAW_STATUS_VALUE_ARGUMENT =
      Pattern.compile(
          "(?s)(?:\\.set[A-Za-z0-9_]*Status\\s*\\("
              + "|[A-Za-z0-9_]+::get[A-Za-z0-9_]*Status\\s*,)"
              + "\\s*[\"'][A-Z][A-Z0-9_]*[\"']");
  private static final Pattern RAW_STATUS_SQL =
      Pattern.compile(
          "(?is)(?:\\bstatus|[a-z_]+_status)\\s*"
              + "(?:=(?!=)|(?:NOT\\s+)?IN\\s*\\()\\s*"
              + "['\"][A-Z][A-Z0-9_]*['\"]");
  private static final Pattern INLINE_ENUM_STRING_SET =
      Pattern.compile("Set\\.of\\(\\s*\"[A-Z][A-Z0-9_]*\"");
  private static final Pattern DIRECT_ENUM_LITERAL_COMPARISON =
      Pattern.compile(
          "(?:equals(?:IgnoreCase)?\\(\\s*\"(?<argument>[A-Z][A-Z0-9_]+)\"\\s*\\)"
              + "|\"(?<receiver>[A-Z][A-Z0-9_]+)\"\\.equals(?:IgnoreCase)?\\()");
  private static final Pattern STRING_SWITCH_CASE =
      Pattern.compile("case\\s+\"[A-Z][A-Z0-9_]+\"");
  private static final Pattern STRING_COLLECTION_DECLARATION =
      Pattern.compile("(?:Set|List|Map)\\s*<[^;>{}]*String");

  @Test
  void managersReuseSharedTechnicalHelpers() throws IOException {
    Path root = repositoryRoot();
    List<String> violations = new ArrayList<>();
    try (Stream<Path> paths = JavaImportPolicy.repositorySources(root).keySet().stream()) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith("Manager.java"))
          .filter(SharedHelperArchitectureTest::isProductionSource)
          .forEach(
              path -> {
                String source = read(path);
                if (DUPLICATE_MANAGER_HELPER.matcher(source).find()
                    || DUPLICATE_REQUIRED_TEXT.matcher(source).find()) {
                  violations.add(root.relativize(path).toString());
                }
                if (source.contains("return \"{\\\"")
                    || source.contains("Collectors.joining(\",\", \"[\", \"]\")")) {
                  violations.add(root.relativize(path) + " -> 手写 JSON");
                }
              });
    }

    assertTrue(
        violations.isEmpty(),
        () ->
            "Manager 必须复用公共技术能力，并禁止手写 JSON 编码:\n"
                + String.join("\n", violations));
  }

  @Test
  void productionCodeDoesNotUseExperimentalLombokDelegate() throws IOException {
    Path root = repositoryRoot();
    List<String> violations = new ArrayList<>();
    try (Stream<Path> paths = JavaImportPolicy.repositorySources(root).keySet().stream()) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .filter(SharedHelperArchitectureTest::isProductionSource)
          .filter(path -> read(path).contains(EXPERIMENTAL_LOMBOK_DELEGATE))
          .forEach(path -> violations.add(root.relativize(path).toString()));
    }

    assertTrue(
        violations.isEmpty(),
        () ->
            "生产代码不得使用实验性的 lombok.experimental.Delegate，应显式实现转发方法:\n"
                + String.join("\n", violations));
  }

  @Test
  void lifecycleStatusCodesAreOwnedByEnums() throws IOException {
    Path root = repositoryRoot();
    List<String> violations = new ArrayList<>();
    try (Stream<Path> paths = JavaImportPolicy.repositorySources(root).keySet().stream()) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .filter(SharedHelperArchitectureTest::isProductionSource)
          .forEach(
              path -> {
                String source = read(path);
                if (!source.contains("public enum")
                    && (RAW_PENDING_STATUS.matcher(source).find()
                        || RAW_STATUS_VALUE_ARGUMENT.matcher(source).find()
                        || RAW_STATUS_SQL.matcher(source).find())) {
                  violations.add(root.relativize(path).toString());
                }
              });
    }

    assertTrue(
        violations.isEmpty(),
        () ->
            "生命周期状态编码必须由所属枚举提供，生产代码不得在状态赋值、过滤或 SQL 中直接写字面量:\n"
                + String.join("\n", violations));
  }

  @Test
  void constantsDoNotOwnClosedStringVocabularies() throws IOException {
    Path root = repositoryRoot();
    List<String> violations = new ArrayList<>();
    try (Stream<Path> paths = JavaImportPolicy.repositorySources(root).keySet().stream()) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().matches(".*Constants?\\.java"))
          .filter(SharedHelperArchitectureTest::isProductionSource)
          .forEach(
              path -> {
                String source = read(path);
                if (STRING_COLLECTION_DECLARATION.matcher(source).find()) {
                  violations.add(root.relativize(path).toString());
                }
              });
    }

    assertTrue(
        violations.isEmpty(),
        () ->
            "常量类不得用字符串集合模拟封闭业务词汇，应由所属模块枚举建模:\n"
                + String.join("\n", violations));
  }

  @Test
  void productionCodeDoesNotCompareClosedVocabularyAsRawStrings() throws IOException {
    Path root = repositoryRoot();
    List<String> violations = new ArrayList<>();
    try (Stream<Path> paths = JavaImportPolicy.repositorySources(root).keySet().stream()) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .filter(SharedHelperArchitectureTest::isProductionSource)
          .filter(path -> !containsSegment(path, "enums"))
          .forEach(
              path -> {
                String source = read(path);
                if (INLINE_ENUM_STRING_SET.matcher(source).find()
                    || STRING_SWITCH_CASE.matcher(source).find()
                    || hasDirectEnumLiteralComparison(source)) {
                  violations.add(root.relativize(path).toString());
                }
              });
    }

    assertTrue(
        violations.isEmpty(),
        () ->
            "生产代码不得用字符串集合、字符串 switch 或直接 equals 比较封闭业务词汇，应先使用枚举:\n"
                + String.join("\n", violations));
  }

  @Test
  void sourceBoundaryExcludesRuntimeAndGeneratedCopies() {
    Path source = Path.of("recruiting", "src", "main", "java", "Example.java");
    assertTrue(isProductionSource(source));
    assertFalse(isProductionSource(Path.of("recruiting", "src", "test", "java", "Example.java")));
    for (String excluded : List.of(".runtime", ".planning", ".git", "target")) {
      assertFalse(isProductionSource(Path.of(excluded, "checkout").resolve(source)), excluded);
    }
  }

  private static boolean isProductionSource(Path path) {
    return containsSegment(path, "src")
        && containsSegment(path, "main")
        && !containsSegment(path, "target")
        && !containsSegment(path, ".planning")
        // 本地验证副本和 Git 元数据不是仓库生产源码，规则仍覆盖所有正式模块。
        && !containsSegment(path, ".runtime")
        && !containsSegment(path, ".git");
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException exception) {
      throw new IllegalStateException("读取源码文件失败: " + path, exception);
    }
  }

  private static Path repositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("pom.xml"))
          && Files.isDirectory(current.resolve("contract-test"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("无法定位仓库根目录");
  }

  private static boolean containsSegment(Path path, String segment) {
    for (Path part : path) {
      if (part.toString().equals(segment)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasDirectEnumLiteralComparison(String source) {
    Matcher matcher = DIRECT_ENUM_LITERAL_COMPARISON.matcher(source);
    while (matcher.find()) {
      String literal = matcher.group("argument");
      if (literal == null) {
        literal = matcher.group("receiver");
      }
      if (!"Y".equals(literal)) {
        return true;
      }
    }
    return false;
  }
}
