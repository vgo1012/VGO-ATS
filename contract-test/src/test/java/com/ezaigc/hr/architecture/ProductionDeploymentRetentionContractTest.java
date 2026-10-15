package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductionDeploymentRetentionContractTest {

  @Test
  void successfulDeploymentKeepsRollbackReleasesAndBoundsRebuildableStorage()
      throws IOException {
    String script =
        Files.readString(repositoryRoot().resolve("deploy/deploy.sh")).replace("\r\n", "\n");

    int switchPosition = script.indexOf("mv -Tf \"$temporary_link\" \"$current_link\"");
    int cleanupPosition = script.indexOf("if ! cleanup_old_releases; then", switchPosition);
    assertTrue(switchPosition >= 0);
    assertTrue(cleanupPosition > switchPosition, "只能在新版本健康并切换 current 后清理旧版本");

    assertTrue(script.contains("retain_release_count=5"));
    assertTrue(script.contains("protected_releases[\"$release_id\"]=1"));
    assertTrue(script.contains("protected_releases[\"$previous_id\"]=1"));
    assertTrue(script.contains("[[ \"$candidate\" =~ ^[0-9a-f]{7,40}$ ]]"));
    assertTrue(script.contains("realpath -e -- \"$candidate_path\""));
    assertTrue(script.contains("rm -rf -- \"$resolved\""));
    assertTrue(script.contains("vgo-backend:$candidate"));
    assertTrue(script.contains("vgo-web:$candidate"));
    assertTrue(script.contains("docker builder prune --all --force --max-used-space 8GB"));
    assertFalse(script.contains("docker volume prune"));
    assertFalse(script.contains("docker system prune"));
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
}
