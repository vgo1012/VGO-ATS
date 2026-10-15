package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;

class LocalDockerDeliveryContractTest {

  @Test
  void composeBuildsTheBackendAndLocalWebDelivery() throws IOException {
    Path root = repositoryRoot();
    var compose = new YamlPropertySourceLoader()
        .load("compose", new FileSystemResource(root.resolve("compose.yaml"))).getFirst();
    assertEquals(".", compose.getProperty("services.backend.build.context"));
    assertEquals(".", compose.getProperty("services.web.build.context"));
    Path backendDockerfile = root.resolve(
        compose.getProperty("services.backend.build.dockerfile").toString());
    Path webDockerfile = root.resolve(
        compose.getProperty("services.web.build.dockerfile").toString());
    String backend = Files.readString(backendDockerfile);
    String web = Files.readString(webDockerfile);
    assertTrue(backend.contains("-pl bootstrap -am clean package"));
    assertTrue(backend.contains("USER 10001:10001"));
    assertTrue(backend.contains("ENTRYPOINT [\"java\", \"-jar\", \"/app/app.jar\"]"));
    assertEquals("local", compose.getProperty("services.web.build.target"));
    assertTrue(web.contains(" AS local\n"));
    assertTrue(web.contains("COPY deploy/nginx-local.conf /etc/nginx/conf.d/default.conf"));
    assertTrue(web.contains("COPY deploy/security-headers.conf /etc/nginx/security-headers.inc"));
    assertTrue(web.contains("COPY --from=build /workspace/ui/dist/ /usr/share/nginx/html/"));
    assertTrue(Files.isRegularFile(root.resolve("deploy/security-headers.conf")));
  }

  @Test
  void localWebRoutesPagesAndApiAndChecksBackendReadiness() throws IOException {
    String nginx = Files.readString(repositoryRoot().resolve("deploy/nginx-local.conf"));
    assertTrue(nginx.contains("set $backend_upstream backend:8080;"));
    assertTrue(nginx.contains("proxy_pass http://$backend_upstream/actuator/health/readiness;"));
    assertTrue(nginx.contains("location /api/ {"));
    assertTrue(nginx.contains("proxy_pass http://$backend_upstream;"));
    assertTrue(nginx.contains("try_files $uri $uri/ /index.html;"));
    assertTrue(nginx.contains("include /etc/nginx/security-headers.inc;"));
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
