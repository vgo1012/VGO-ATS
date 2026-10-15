package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NginxSecurityHeaderContractTest {

  @Test
  void everyHttpsFrontendResponseKeepsCentralSecurityHeaders() throws IOException {
    Path root = repositoryRoot();
    String headers = Files.readString(root.resolve("deploy/security-headers.conf"));
    String nginx = Files.readString(root.resolve("deploy/nginx.conf")).replace("\r\n", "\n");
    String compose = Files.readString(root.resolve("deploy/docker-compose.prod.yml"));

    assertTrue(headers.contains("Strict-Transport-Security"));
    assertTrue(headers.contains("X-Content-Type-Options \"nosniff\""));
    assertTrue(headers.contains("X-Frame-Options \"SAMEORIGIN\""));
    assertTrue(headers.contains("Referrer-Policy \"strict-origin-when-cross-origin\""));
    assertTrue(headers.contains("Permissions-Policy \"camera=(), microphone=(), geolocation=()\""));
    assertTrue(headers.contains("Content-Security-Policy"));
    assertTrue(headers.contains("frame-src 'self' blob:"));
    assertFalse(headers.contains("'unsafe-eval'"));

    assertEquals(
        2,
        nginx.split("include /etc/nginx/conf.d/security-headers.inc;", -1).length - 1,
        "两个 HTTPS server 必须加载安全头；静态服务的缓存头由独立容器提供");
    assertTrue(
        compose.contains("- /opt/vgo/shared/edge:/etc/nginx/conf.d:ro"));
    assertTrue(compose.contains("- web_sockets:/run/web-sockets:ro"));
    assertTrue(compose.contains("WEB_SLOT: blue"));
    assertTrue(compose.contains("WEB_SLOT: green"));
    assertTrue(locationBlock(nginx, "location / {")
        .contains("proxy_pass http://unix:/run/web-sockets/active.sock:;"));
  }

  @Test
  void fixedAssetPathsRevalidateWhileHashedBuildAssetsStayImmutable() throws IOException {
    String nginx = Files.readString(repositoryRoot().resolve("deploy/nginx-static.conf")).replace("\r\n", "\n");
    String vite = Files.readString(repositoryRoot().resolve("ui/vite.config.ts"));
    String dockerfile = Files.readString(repositoryRoot().resolve("ui/Dockerfile"));

    assertTrue(vite.contains("assetsDir: '_build'"));
    assertTrue(dockerfile.contains("COPY deploy/nginx-static.conf /etc/nginx/static.conf.template"));
    assertTrue(dockerfile.contains("ENTRYPOINT [\"/usr/local/bin/web-static-entrypoint\"]"));
    assertTrue(nginx.contains("listen unix:/run/web-sockets/@WEB_SLOT@.sock;"));
    assertTrue(
        locationBlock(nginx, "location ^~ /_build/")
            .contains("add_header Cache-Control \"public, max-age=31536000, immutable\";"));
    assertTrue(
        locationBlock(nginx, "location ~* \\.(?:css|js|svg|png")
            .contains("add_header Cache-Control \"no-cache\";"));
  }

  @Test
  void loginRateLimitUsesUnspoofablePeerAndLeavesOtherApisOutOfItsZone() throws IOException {
    String nginx = Files.readString(repositoryRoot().resolve("deploy/nginx.conf"));

    assertTrue(nginx.contains("map $uri $login_rate_key {"));
    assertTrue(nginx.contains("default \"\";"));
    assertTrue(nginx.contains("/api/v1/auth/login $binary_remote_addr;"));
    assertTrue(nginx.contains("limit_req_zone $login_rate_key zone=login_rate:10m rate=10r/s;"));
    assertTrue(locationBlock(nginx, "location /api/")
        .contains("limit_req zone=login_rate burst=20 nodelay;"));
    assertTrue(locationBlock(nginx, "location /api/").contains("limit_req_status 429;"));
    assertFalse(nginx.contains("limit_req_zone $http_x_forwarded_for"));
  }

  private static String locationBlock(String nginx, String location) {
    int start = nginx.lastIndexOf(location);
    assertTrue(start >= 0, "缺少静态资源 location: " + location);
    int end = nginx.indexOf("\n    }", start);
    assertTrue(end >= 0, "静态资源 location 未闭合: " + location);
    return nginx.substring(start, end);
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
