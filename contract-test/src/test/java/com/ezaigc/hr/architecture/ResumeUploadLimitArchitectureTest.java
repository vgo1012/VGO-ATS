package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ResumeUploadLimitArchitectureTest {

  private static final Pattern DUPLICATED_TEN_MEBIBYTE_LIMIT =
      Pattern.compile("\\b10L?\\s*\\*\\s*1024\\s*\\*\\s*1024\\b");

  @Test
  void resumeSizeLimitHasOnePublicBusinessContract() throws IOException {
    Path repositoryRoot = Path.of("..").toAbsolutePath().normalize();
    Path policySource =
        repositoryRoot.resolve(
            "talent/src/main/java/com/ezaigc/hr/talent/api/ResumeUploadPolicy.java");
    List<String> duplicatedLimits = new ArrayList<>();

    for (String module : productionModules(repositoryRoot)) {
      Path sourceRoot = repositoryRoot.resolve(module).resolve("src/main/java");
      if (!Files.isDirectory(sourceRoot)) continue;
      try (Stream<Path> sources = Files.walk(sourceRoot)) {
        sources
            .filter(path -> path.toString().endsWith(".java"))
            .filter(path -> !path.equals(policySource))
            .forEach(path -> collectDuplicatedLimit(path, repositoryRoot, duplicatedLimits));
      }
    }

    assertTrue(Files.isRegularFile(policySource), "简历上传大小上限必须由 talent.api 公开契约持有");
    assertTrue(
        duplicatedLimits.isEmpty(),
        () ->
            "生产代码禁止复制 10 MiB 简历上限，统一引用 ResumeUploadPolicy:\n"
                + String.join("\n", duplicatedLimits));
  }

  private static List<String> productionModules(Path repositoryRoot) throws IOException {
    try (Stream<Path> children = Files.list(repositoryRoot)) {
      return children
          .filter(Files::isDirectory)
          .filter(path -> Files.isDirectory(path.resolve("src/main/java")))
          .map(path -> path.getFileName().toString())
          .toList();
    }
  }

  private static void collectDuplicatedLimit(
      Path source, Path repositoryRoot, List<String> duplicatedLimits) {
    try {
      if (DUPLICATED_TEN_MEBIBYTE_LIMIT.matcher(Files.readString(source)).find()) {
        duplicatedLimits.add(repositoryRoot.relativize(source).toString());
      }
    } catch (IOException exception) {
      throw new IllegalStateException("无法检查生产源码: " + source, exception);
    }
  }
}
