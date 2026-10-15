package com.ezaigc.hr.integration.search;

import static org.assertj.core.api.Assertions.assertThat;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchCriteria;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 验证发出的真实 HTTP 请求DSL，不冒充外部 Elasticsearch 执行测试。 */
class ElasticsearchFacetQueryTest {
  @Test void sendsOneOrClausePerFacetAndKeepsSkillsConjunctiveBeforePagination() throws Exception {
    var json = new ObjectMapper();
    var captured = new AtomicReference<JsonNode>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/approved-physical/_search", exchange -> {
      captured.set(json.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
      byte[] response = "{\"timed_out\":false,\"_shards\":{\"failed\":0},\"hits\":{\"hits\":[]}}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response); exchange.close();
    });
    server.start();
    try (var http = HttpClient.newHttpClient()) {
      var adapter = new ElasticsearchSearchIndexAdapter();
      ReflectionTestUtils.setField(adapter, "http", http);
      ReflectionTestUtils.setField(adapter, "json", json);
      ReflectionTestUtils.setField(adapter, "base", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
      ReflectionTestUtils.setField(adapter, "alias", "resume-facts-current");
      ReflectionTestUtils.setField(adapter, "timeout", Duration.ofSeconds(5));
      var target = new SearchIndexTarget("approved-physical", 4);
      var criteria = new SearchCriteria();
      criteria.setDegrees(List.of("本科", "硕士"));
      criteria.setCities(List.of("上海", "北京"));
      criteria.setSkills(List.of("Java", "SQL"));
      criteria.setQuery("高压电工证");
      criteria.setConditions(List.of("低压电工证", "Java"));
      criteria.setMinWorkYears(new BigDecimal("1"));
      criteria.setMaxWorkYears(new BigDecimal("3"));
      criteria.setCandidateIds(List.of("candidate-a", "candidate-b"));
      adapter.search(target, criteria, null, 10);
      var request = captured.get();
      assertThat(request.path("query").path("bool").path("must").valueStream()
          .map(item -> item.path("multi_match").path("query").asString()).toList())
          .containsExactly("高压电工证", "低压电工证", "Java");
      var filters = request.path("query").path("bool").path("filter");
      assertThat(filters.valueStream().filter(item -> item.has("terms")).toList()).hasSize(3);
      assertThat(filters.valueStream().filter(item -> item.path("terms").has("candidateId"))
          .findFirst().orElseThrow().path("terms").path("candidateId").valueStream()
          .map(JsonNode::asString).toList()).containsExactly("candidate-a", "candidate-b");
      assertThat(filters.valueStream().filter(item -> item.path("terms").has("degrees")).findFirst().orElseThrow()
          .path("terms").path("degrees").valueStream().map(JsonNode::asString).toList())
          .contains("本科", "硕士", "BACHELOR", "Bachelor", "bachelor", "MASTER", "硕士研究生")
          .doesNotContain("Diploma", "专科", "博士");
      assertThat(filters.valueStream().filter(item -> item.path("terms").has("city")).findFirst().orElseThrow()
          .path("terms").path("city").valueStream().map(JsonNode::asString).toList()).containsExactly("上海", "北京");
      assertThat(filters.valueStream().filter(item -> item.path("term").has("skills.keyword")).toList()).hasSize(2);
      var workYears = filters.valueStream()
          .filter(item -> item.path("range").has("workYears")).findFirst().orElseThrow()
          .path("range").path("workYears");
      assertThat(workYears.path("gte").decimalValue()).isEqualByComparingTo("1");
      assertThat(workYears.path("lt").decimalValue()).isEqualByComparingTo("3");
      assertThat(workYears.has("lte")).isFalse();
      assertThat(request.path("size").asInt()).isEqualTo(11);
      criteria.setDegrees(List.of("专科", "本科", "硕士", "博士"));
      adapter.search(target, criteria, null, 10);
      assertThat(captured.get().path("query").path("bool").path("filter").valueStream()
          .filter(item -> item.path("terms").has("degrees")).findFirst().orElseThrow()
          .path("terms").path("degrees").valueStream().map(JsonNode::asString).toList())
          .contains("大专", "ASSOCIATE", "BACHELOR", "Bachelor's degree", "MASTER", "PHD", "PhD")
          .doesNotContain("Diploma", "本科在读");
      criteria.setDegrees(List.of("Diploma"));
      adapter.search(target, criteria, null, 10);
      assertThat(captured.get().path("query").path("bool").path("filter").valueStream()
          .filter(item -> item.path("terms").has("degrees")).findFirst().orElseThrow()
          .path("terms").path("degrees").valueStream().map(JsonNode::asString).toList())
          .containsExactly("Diploma");
      criteria.setDegrees(List.of()); criteria.setCities(List.of()); criteria.setCandidateIds(List.of());
      adapter.search(target, criteria, null, 10);
      assertThat(captured.get().path("query").path("bool").path("filter").valueStream()
          .filter(item -> item.has("terms")).toList()).isEmpty();
    } finally { server.stop(0); }
  }
}
