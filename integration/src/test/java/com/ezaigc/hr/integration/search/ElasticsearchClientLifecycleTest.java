package com.ezaigc.hr.integration.search;

import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.integration.config.IntegrationProperties;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

/** 实际 Spring 销毁与 JDK HTTP 生命周期；本地服务仅为合成协议夹具。 */
class ElasticsearchClientLifecycleTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void contextCloseTerminatesIdleOrInFlightClientAndRejectsNewCalls(boolean inFlight)
      throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var headers = new CountDownLatch(1);
    var release = new CountDownLatch(inFlight ? 1 : 0);
    try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
      server.setExecutor(workers);
      server.createContext("/approved-physical", exchange -> {
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
      });
      server.createContext("/_bulk", exchange -> {
        try {
          exchange.getRequestBody().readAllBytes();
          byte[] response = "{\"errors\":false,\"items\":[{\"update\":{\"status\":201}}]}"
              .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response, 0, 1);
          exchange.getResponseBody().flush();
          headers.countDown();
          release.await();
          exchange.getResponseBody().write(response, 1, response.length - 1);
        } catch (InterruptedException failure) {
          Thread.currentThread().interrupt();
        } finally {
          exchange.close();
        }
      });
      server.start();
      HttpClient client = null;
      Future<?> request = null;
      try (var context = context(server.getAddress().getPort())) {
        var adapter = context.getBean(ElasticsearchSearchIndexAdapter.class);
        client = (HttpClient) ReflectionTestUtils.getField(adapter, "http");
        assertThat(client).isNotNull();
        var document = new SearchDocument();
        document.setCandidateId("synthetic-candidate");
        document.setProjectionVersion((1L << 32) | 1);
        request = workers.submit(() -> adapter.upsertBatch(new SearchIndexTarget("approved-physical", 0), List.of(document)));
        assertThat(headers.await(3, TimeUnit.SECONDS)).isTrue();
        if (!inFlight) request.get(3, TimeUnit.SECONDS);
        assertThat(client.isTerminated()).isFalse();

        workers.submit(context::close).get(3, TimeUnit.SECONDS);

        assertThat(client.awaitTermination(Duration.ofSeconds(2)))
            .as("Spring 关闭必须终止其拥有的客户端").isTrue();
        if (inFlight) {
          Future<?> pendingRequest = request;
          assertThatThrownBy(() -> pendingRequest.get(2, TimeUnit.SECONDS))
              .isInstanceOf(ExecutionException.class).cause().isInstanceOf(PlatformException.class);
        }
        assertThatThrownBy(() -> adapter.upsertBatch(new SearchIndexTarget("approved-physical", 0), List.of(document)))
            .isInstanceOfSatisfying(PlatformException.class,
                failure -> assertThat(failure.code()).isEqualTo("SEARCH_INDEX_UNAVAILABLE"));
      } finally {
        release.countDown();
        server.stop(0);
        if (request != null) request.cancel(true);
        if (client != null) {
          client.shutdownNow();
          client.close();
        }
      }
    } finally {
      release.countDown();
      server.stop(0);
    }
  }

  private static AnnotationConfigApplicationContext context(int port) {
    var properties = new IntegrationProperties();
    properties.getSearch().setMode("elasticsearch");
    var configuration = properties.getSearch().getElasticsearch();
    configuration.setBaseUrl("http://127.0.0.1:" + port);
    configuration.setAlias("synthetic-lifecycle");
    configuration.setTimeout(Duration.ofSeconds(20));
    var context = new AnnotationConfigApplicationContext();
    context.getEnvironment().getPropertySources().addFirst(
        new MapPropertySource("test", Map.of("hr.search.mode", "elasticsearch")));
    context.registerBean(IntegrationProperties.class, () -> properties);
    context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
    context.registerBean(CursorCodec.class,
        () -> new SignedCursorCodec("synthetic-lifecycle-cursor-key-00000001"));
    context.register(ElasticsearchSearchIndexAdapter.class);
    try {
      context.refresh();
      return context;
    } catch (RuntimeException | Error failure) {
      context.close();
      throw failure;
    }
  }
}
