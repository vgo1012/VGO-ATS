package com.ezaigc.hr.integration.search;

import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

/** 使用真实 HTTP 连接验证截止时间；该服务仅是测试夹具，不是 Elasticsearch 实例。 */
class ElasticsearchHttpDeadlineTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void configuredTimeoutAlsoBoundsAStalledResponseBody(boolean stallBody) throws Exception {
    boundedResponse(stallBody, false);
  }

  @Test
  void finiteRequestLeaseAlsoBoundsAStalledBodyWithALongerConfiguredTimeout() throws Exception {
    boundedResponse(true, true);
  }

  private void boundedResponse(boolean stallBody, boolean finiteLease) throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var headersSent = new CountDownLatch(1);
    var releaseBody = new CountDownLatch(stallBody ? 1 : 0);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor();
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
      server.setExecutor(executor);
      server.createContext("/approved-physical", exchange -> {
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
      });
      server.createContext("/_bulk", exchange -> {
        try {
          exchange.getRequestBody().readAllBytes();
          byte[] response = "{\"errors\":false,\"items\":[{\"update\":{\"status\":201}}]}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response, 0, 1);
          exchange.getResponseBody().flush();
          headersSent.countDown();
          releaseBody.await(5, TimeUnit.SECONDS);
          exchange.getResponseBody().write(response, 1, response.length - 1);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        } finally {
          exchange.close();
        }
      });
      server.start();
      var adapter = new ElasticsearchSearchIndexAdapter();
      ReflectionTestUtils.setField(adapter, "http", client);
      ReflectionTestUtils.setField(adapter, "json", new ObjectMapper());
      ReflectionTestUtils.setField(adapter, "base", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
      ReflectionTestUtils.setField(adapter, "alias", "resume-facts-current");
      ReflectionTestUtils.setField(adapter, "timeout", finiteLease ? Duration.ofSeconds(5) : Duration.ofMillis(500));
      var document = new SearchDocument();
      document.setCandidateId("C".repeat(26));
      document.setProjectionVersion((1L << 32) | 1);
      var target = finiteLease
          ? new SearchIndexTarget("approved-physical", 0, System.nanoTime() + Duration.ofMillis(500).toNanos())
          : new SearchIndexTarget("approved-physical", 0);
      var call = executor.submit(() -> adapter.upsertBatch(target, List.of(document)));
      try {
        assertThat(headersSent.await(2, TimeUnit.SECONDS)).isTrue();
        if (stallBody) {
          assertThatThrownBy(() -> call.get(3, TimeUnit.SECONDS))
              .isInstanceOf(ExecutionException.class)
              .cause().isInstanceOf(PlatformException.class)
              .extracting("code").isEqualTo("SEARCH_INDEX_UNAVAILABLE");
        } else {
          call.get(3, TimeUnit.SECONDS);
        }
      } finally {
        releaseBody.countDown();
        server.stop(0);
        call.cancel(true);
      }
    } finally {
      server.stop(0);
    }
  }
}
