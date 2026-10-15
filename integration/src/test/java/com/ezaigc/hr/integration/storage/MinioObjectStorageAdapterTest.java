package com.ezaigc.hr.integration.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.ezaigc.hr.integration.config.IntegrationProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.minio.MinioClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

/** 真实MinIO SDK连接本地S3协议夹具；不启动中间件或连接业务对象桶。 */
class MinioObjectStorageAdapterTest {
  @ParameterizedTest
  @ValueSource(strings = {"headers", "body", "drip"})
  void requestDeadlineBoundsHeadersAndTheEntireResponseBody(String phase) throws Exception {
    try (var fixture = new S3Fixture(phase);
        var context = fixture.context()) {
      var adapter = context.getBean(MinioObjectStorageAdapter.class);
      MinioClient client = sdkClient(adapter);
      var bodyOpened = new CountDownLatch(phase.equals("headers") ? 0 : 1);
      var call = fixture.executor.submit(() -> {
        if (phase.equals("headers")) return adapter.exists("synthetic.txt");
        try (var content = adapter.open("synthetic.txt")) {
          assertThat(content.read()).isEqualTo('x');
          bodyOpened.countDown();
          return content.readAllBytes().length == 63;
        }
      });
      try {
        assertThat(fixture.started.await(3, TimeUnit.SECONDS)).as("SDK已进入被测HTTP阶段").isTrue();
        assertThat(bodyOpened.await(2, TimeUnit.SECONDS)).as("下载已读到实际对象首字节").isTrue();
        assertThatThrownBy(() -> call.get(2, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class).cause().isInstanceOf(IOException.class);
      } finally {
        fixture.release.countDown();
        fixture.server.stop(0);
        call.cancel(true);
        // 原实现没有销毁回调时，失败回归本身仍须释放SDK持有的线程和连接。
        client.close();
      }
    }
  }

  @Test
  void closingSpringContextReleasesTheSdkExecutorAndIdleConnections() throws Exception {
    try (var fixture = new S3Fixture("normal");
        var context = fixture.context()) {
      var adapter = context.getBean(MinioObjectStorageAdapter.class);
      MinioClient client = sdkClient(adapter);
      Object async = ReflectionTestUtils.getField(client, "asyncClient");
      var http = (OkHttpClient) ReflectionTestUtils.getField(async, "httpClient");
      try {
        assertThat(adapter.exists("synthetic.txt")).isTrue();
        try (var content = adapter.open("synthetic.txt")) {
          assertThat(content.readAllBytes()).isEqualTo("x".repeat(64).getBytes(StandardCharsets.UTF_8));
        }
        assertThat(http.dispatcher().executorService().isShutdown()).isFalse();
        assertThat(http.connectionPool().connectionCount()).isPositive();
        context.close();
        assertAll("Spring销毁后必须回收SDK实际使用的资源",
            () -> assertThat(http.dispatcher().executorService().isShutdown()).isTrue(),
            () -> assertThat(http.connectionPool().connectionCount()).isZero());
      } finally {
        client.close();
      }
    }
  }

  private static MinioClient sdkClient(MinioObjectStorageAdapter adapter) {
    return (MinioClient) ReflectionTestUtils.getField(adapter, "client");
  }

  @Test
  void closingContextCancelsAnActiveDownloadBeforeItsLongTimeout() throws Exception {
    try (var fixture = new S3Fixture("body");
        var context = fixture.context("10s")) {
      var adapter = context.getBean(MinioObjectStorageAdapter.class);
      MinioClient client = sdkClient(adapter);
      var bodyOpened = new CountDownLatch(1);
      var call = fixture.executor.submit(() -> {
        try (var content = adapter.open("synthetic.txt")) {
          assertThat(content.read()).isEqualTo('x');
          bodyOpened.countDown();
          return content.readAllBytes();
        }
      });
      try {
        assertThat(bodyOpened.await(3, TimeUnit.SECONDS)).isTrue();
        context.close();
        assertThatThrownBy(() -> call.get(2, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class).cause().isInstanceOf(IOException.class);
      } finally {
        fixture.release.countDown();
        fixture.server.stop(0);
        call.cancel(true);
        client.close();
      }
    }
  }

  private static final class S3Fixture implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final String phase;

    private S3Fixture(String phase) throws IOException {
      this.phase = phase;
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.setExecutor(executor);
      server.createContext("/", this::respond);
      server.start();
    }

    private AnnotationConfigApplicationContext context() {
      return context("500ms");
    }

    private AnnotationConfigApplicationContext context(String requestTimeout) {
      var source = new MapConfigurationPropertySource(Map.of(
          "hr.object-storage.mode", "minio",
          "hr.object-storage.endpoint", "http://127.0.0.1:" + server.getAddress().getPort(),
          "hr.object-storage.bucket", "synthetic-test",
          "hr.object-storage.access-key", "synthetic-access",
          "hr.object-storage.secret-key", "synthetic-secret-only",
          "hr.object-storage.connect-timeout", "1s",
          "hr.object-storage.read-timeout", "5s",
          "hr.object-storage.write-timeout", "5s",
          "hr.object-storage.request-timeout", requestTimeout));
      IntegrationProperties properties = new Binder(source).bind("hr", Bindable.of(IntegrationProperties.class)).get();
      var context = new AnnotationConfigApplicationContext();
      context.getEnvironment().getPropertySources().addFirst(
          new MapPropertySource("synthetic-minio", Map.of("hr.object-storage.mode", "minio")));
      context.registerBean(IntegrationProperties.class, () -> properties);
      context.register(MinioObjectStorageAdapter.class);
      context.refresh();
      return context;
    }

    private void respond(HttpExchange exchange) throws IOException {
      try {
        if (exchange.getRequestURI().getRawQuery() != null
            && exchange.getRequestURI().getRawQuery().startsWith("location")) {
          byte[] location = "<LocationConstraint xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">us-east-1</LocationConstraint>"
              .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/xml");
          exchange.sendResponseHeaders(200, location.length);
          exchange.getResponseBody().write(location);
          return;
        }
        if (!exchange.getRequestURI().getPath().equals("/synthetic-test/synthetic.txt")) {
          exchange.sendResponseHeaders(400, -1);
          return;
        }
        if (phase.equals("headers")) {
          started.countDown();
          release.await(5, TimeUnit.SECONDS);
        }
        exchange.getResponseHeaders().set("Last-Modified", "Mon, 07 Sep 2026 00:00:00 GMT");
        exchange.getResponseHeaders().set("ETag", "\"synthetic-etag\"");
        if (exchange.getRequestMethod().equals("HEAD")) {
          exchange.getResponseHeaders().set("Content-Length", "64");
          exchange.sendResponseHeaders(200, -1);
          return;
        }
        exchange.sendResponseHeaders(200, 64);
        exchange.getResponseBody().write('x');
        exchange.getResponseBody().flush();
        started.countDown();
        if (phase.equals("body")) release.await(5, TimeUnit.SECONDS);
        for (int i = 1; i < 64; i++) {
          if (phase.equals("drip") && release.await(100, TimeUnit.MILLISECONDS)) break;
          exchange.getResponseBody().write('x');
          exchange.getResponseBody().flush();
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      } finally {
        exchange.close();
      }
    }

    @Override
    public void close() {
      release.countDown();
      server.stop(0);
      executor.close();
    }
  }
}
