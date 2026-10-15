package com.ezaigc.hr.integration.search;

import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

/** 本地协议夹具模拟 HEAD 与 bulk 之间退役；不连接或删除真实 Elasticsearch 数据。 */
class ElasticsearchRetiredTargetWriteTest {
  @Test
  void retirementAfterExistenceCheckCannotLetLateBulkAutomaticallyRecreateItsTarget()
      throws Exception {
    var targetExists = new AtomicBoolean(true);
    var bulkQuery = new AtomicReference<String>();
    var bulkBody = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor();
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
      server.setExecutor(executor);
      server.createContext("/approved-physical", exchange -> {
        // 检查返回时存在，bulk 到达前 GC 已删除物理索引及其不可变写别名。
        targetExists.set(false);
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
      });
      server.createContext("/_bulk", exchange -> {
        bulkBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        bulkQuery.set(exchange.getRequestURI().getRawQuery());
        boolean aliasRequired = "require_alias=true".equals(bulkQuery.get());
        if (!aliasRequired) targetExists.set(true);
        byte[] response = (aliasRequired
            ? "{\"error\":{\"type\":\"index_not_found_exception\"},\"status\":404}"
            : "{\"errors\":false,\"items\":[{\"update\":{\"status\":201}}]}")
            .getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(aliasRequired ? 404 : 200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
      });
      server.start();
      var adapter = new ElasticsearchSearchIndexAdapter();
      ReflectionTestUtils.setField(adapter, "http", client);
      ReflectionTestUtils.setField(adapter, "json", new ObjectMapper());
      ReflectionTestUtils.setField(adapter, "base",
          URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
      ReflectionTestUtils.setField(adapter, "alias", "resume-facts-current");
      ReflectionTestUtils.setField(adapter, "timeout", Duration.ofSeconds(2));
      var document = new SearchDocument();
      document.setCandidateId("C".repeat(26));
      document.setProjectionVersion(1);

      assertThatThrownBy(() -> adapter.upsertBatch(new SearchIndexTarget("approved-physical", 0), List.of(document)))
          .isInstanceOf(PlatformException.class)
          .extracting("code").isEqualTo("SEARCH_INDEX_UNAVAILABLE");
      assertThat(bulkQuery.get()).isEqualTo("require_alias=true");
      assertThat(bulkBody.get()).contains("\"_index\":\"resume-write-"
          + Sha256Utils.hashUtf8("resume-facts-current:approved-physical") + "\"")
          .doesNotContain("\"_index\":\"approved-physical\"");
      assertThat(targetExists).isFalse();
    } finally {
      server.stop(0);
    }
  }
}
