package com.ezaigc.hr.integration.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.integration.config.IntegrationProperties;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchCriteria;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchRebuildAttempt;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentMatchers;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 仅在显式配置的集群使用本次独有索引和别名，不访问业务索引内容。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_ELASTICSEARCH_IT", matches = "true")
class ElasticsearchSearchIndexExternalIT {
  private final String alias = "hr-external-it-" + UUID.randomUUID().toString().replace("-", "");
  private final SearchRebuildAttempt firstAttempt = new SearchRebuildAttempt(alias, 1);
  private final SearchRebuildAttempt secondAttempt = new SearchRebuildAttempt(alias, 2);
  private final String firstIndex = firstAttempt.indexName();
  private final String secondIndex = secondAttempt.indexName();
  private final String fence = "resume-rebuild-" + Sha256Utils.hashUtf8(alias + ":" + alias);
  private final SearchIndexTarget firstTarget = new SearchIndexTarget(firstIndex, 1);
  private final SearchIndexTarget secondTarget = new SearchIndexTarget(secondIndex, 2);
  private final Set<String> allowed = new HashSet<>(Set.of(alias, firstIndex, secondIndex, fence,
      writeAlias(alias, firstIndex), writeAlias(alias, secondIndex)));
  private final Map<String, String> resourceIds = new HashMap<>();
  private final List<String> owned = new ArrayList<>();
  private final ObjectMapper json = new ObjectMapper();
  private URI base;
  private String authorization;

  private ElasticsearchSearchIndexAdapter adapterFor(String targetAlias) {
    var properties = new IntegrationProperties();
    var search = properties.getSearch();
    search.setMode("elasticsearch");
    var configuration = search.getElasticsearch();
    configuration.setBaseUrl(required("HR_ES_URL"));
    configuration.setAlias(targetAlias);
    configuration.setApiKey(System.getenv("HR_ES_API_KEY"));
    configuration.setUsername(System.getenv("HR_ES_USERNAME"));
    configuration.setPassword(System.getenv("HR_ES_PASSWORD"));
    assertThat(configuration.hasValidEndpoint()).isTrue();
    assertThat(configuration.hasCompleteBasicCredentials()).isTrue();
    String url = configuration.getBaseUrl();
    base = URI.create(url.endsWith("/") ? url : url + "/");
    authorization = configuration.getApiKey() != null && !configuration.getApiKey().isBlank()
        ? "ApiKey " + configuration.getApiKey()
        : configuration.getUsername() == null || configuration.getUsername().isBlank() ? null
        : "Basic " + Base64.getEncoder().encodeToString(
            (configuration.getUsername() + ":" + configuration.getPassword()).getBytes(StandardCharsets.UTF_8));
    var adapter = new ElasticsearchSearchIndexAdapter();
    ReflectionTestUtils.setField(adapter, "properties", properties);
    ReflectionTestUtils.setField(adapter, "json", json);
    ReflectionTestUtils.setField(adapter, "cursorCodec",
        new SignedCursorCodec("synthetic-external-it-cursor-key-00000001"));
    return adapter;
  }

  @Test
  void realMappingBulkFiltersCursorAndRebuildAliasRemainCompatible() throws Exception {
    var adapter = adapterFor(alias);
    try (var control = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      try {
        adapter.initializeClient();
        for (String resource : allowed) request(control, "HEAD", resource, 404);
        createOwned(adapter, firstAttempt, control);
        adapter.activateRebuild(firstAttempt);
        adapter.ensureApprovedIndex(firstTarget);
        assertAlias(control, firstIndex);

        Instant timestamp = Instant.parse("2026-09-01T00:00:00Z");
        var first = document("candidate-a", "Java", timestamp);
        var second = document("candidate-b", "Java", timestamp);
        var third = document("candidate-c", "Python", timestamp.minusSeconds(1));
        first.setWorkYears(new BigDecimal("2.99"));
        first.setSearchText("Synthetic Java 13800138000 synthetic.person@example.test");
        second.setWorkYears(new BigDecimal("3"));
        third.setWorkYears(new BigDecimal("1"));
        adapter.upsertBatch(firstTarget, List.of(third, second, first));
        adapter.upsertBatch(firstTarget, List.of(first));
        request(control, "POST", firstIndex + "/_refresh", 200);
        assertThat(json.readTree(request(control, "GET", firstIndex + "/_count", 200)).path("count").asInt())
            .isEqualTo(3);

        String firstWriteAlias = writeAlias(alias, firstIndex);
        request(control, "DELETE", firstIndex + "/_alias/" + firstWriteAlias, 200);
        assertThatThrownBy(() -> adapter.upsertBatch(firstTarget, List.of(first)))
            .isInstanceOfSatisfying(PlatformException.class,
                failure -> assertThat(failure.code()).isEqualTo("SEARCH_INDEX_UNAVAILABLE"));
        // require_alias 必须让缺失的 immutable write alias 失败，不能自动创建同名物理索引。
        request(control, "HEAD", firstWriteAlias, 404);
        assertThat(json.readTree(request(control, "GET", firstIndex + "/_count", 200)).path("count").asInt())
            .isEqualTo(3);
        adapter.ensureApprovedIndex(firstTarget);
        assertWriteAlias(control, alias, firstIndex);

        var criteria = new SearchCriteria();
        var page = adapter.search(firstTarget, criteria, null, 2);
        assertThat(page.getItems()).extracting(SearchDocument::getCandidateId)
            .containsExactly("candidate-a", "candidate-b");
        assertThat(page.isHasMore()).isTrue();
        assertThat(page.getNextCursor()).isNotBlank();
        var last = adapter.search(firstTarget, criteria, page.getNextCursor(), 2);
        assertThat(last.getItems()).extracting(SearchDocument::getCandidateId).containsExactly("candidate-c");
        assertThat(last.isHasMore()).isFalse();
        assertThat(last.getNextCursor()).isNull();

        var filtered = new SearchCriteria();
        filtered.setSkills(List.of("Java"));
        filtered.setCities(List.of("Shanghai"));
        assertThat(adapter.search(firstTarget, filtered, null, 10).getItems()).extracting(SearchDocument::getCandidateId)
            .containsExactly("candidate-a", "candidate-b");
        assertThatThrownBy(() -> adapter.search(firstTarget, filtered, page.getNextCursor(), 2))
            .isInstanceOfSatisfying(PlatformException.class,
                failure -> assertThat(failure.code()).isEqualTo("SEARCH_CURSOR_SCOPE_MISMATCH"));

        var scopedExperience = new SearchCriteria();
        scopedExperience.setCandidateIds(List.of("candidate-a", "candidate-b"));
        scopedExperience.setMaxWorkYears(new BigDecimal("3"));
        assertThat(adapter.search(firstTarget, scopedExperience, null, 10).getItems())
            .extracting(SearchDocument::getCandidateId)
            .containsExactly("candidate-a");
        var contact = new SearchCriteria();
        contact.setQuery("13800138000 synthetic.person@example.test");
        assertThat(adapter.search(firstTarget, contact, null, 10).getItems())
            .extracting(SearchDocument::getCandidateId)
            .containsExactly("candidate-a");

        long legacyVersion = (2L << 32) | 1;
        requestWithBody(control, "PUT",
            firstIndex + "/_doc/legacy?version=" + legacyVersion + "&version_type=external",
            "{\"candidateId\":\"legacy\",\"factSnapshotId\":\"legacy-fact\","
                + "\"searchText\":\"old contact\",\"updateTime\":\"2026-09-01T00:00:00Z\"}",
            200, 201);
        var contactUpdate = document("legacy", "Java", timestamp);
        contactUpdate.setProjectionVersion(legacyVersion);
        contactUpdate.setCandidateVersion(0);
        contactUpdate.setSearchText("new contact");
        adapter.upsertBatch(firstTarget, List.of(contactUpdate));
        var lateTask = document("legacy", "Java", timestamp);
        lateTask.setProjectionVersion(legacyVersion - 1);
        lateTask.setCandidateVersion(99);
        lateTask.setSearchText("late old task");
        adapter.upsertBatch(firstTarget, List.of(lateTask));
        requestWithBody(control, "PUT",
            firstIndex + "/_doc/legacy-tombstone?version=" + Long.MAX_VALUE
                + "&version_type=external",
            "{\"candidateId\":\"legacy-tombstone\",\"updateTime\":\"2026-09-01T00:00:00Z\"}",
            200, 201);
        var forbiddenRevival = document("legacy-tombstone", "Java", timestamp);
        forbiddenRevival.setProjectionVersion(Long.MAX_VALUE);
        forbiddenRevival.setCandidateVersion(Long.MAX_VALUE);
        adapter.upsertBatch(firstTarget, List.of(forbiddenRevival));
        request(control, "POST", firstIndex + "/_refresh", 200);
        var legacyOnly = new SearchCriteria();
        legacyOnly.setCandidateIds(List.of("legacy", "legacy-tombstone"));
        assertThat(adapter.search(firstTarget, legacyOnly, null, 10).getItems())
            .extracting(SearchDocument::getCandidateId, SearchDocument::getSearchText)
            .containsExactly(tuple("legacy", "new contact"));

        createOwned(adapter, secondAttempt, control);
        assertThatThrownBy(() -> adapter.activateRebuild(firstAttempt))
            .isInstanceOf(PlatformException.class);
        assertAlias(control, firstIndex);
        var rebuilt = document("rebuilt-candidate", "Rust", timestamp);
        var invalid = document("invalid-date", "Rust", Instant.MAX);
        assertThatThrownBy(() -> adapter.upsertBatch(secondTarget, List.of(rebuilt, invalid)))
            .isInstanceOfSatisfying(PlatformException.class, failure -> {
              assertThat(failure.code()).isEqualTo("SEARCH_INDEX_UNAVAILABLE");
              assertThat(failure.getMessage()).contains("批量写入包含失败项");
            });
        request(control, "POST", secondIndex + "/_refresh", 200);
        assertThat(json.readTree(request(control, "GET", secondIndex + "/_count", 200)).path("count").asInt())
            .isEqualTo(1);
        adapter.upsertBatch(secondTarget, List.of(rebuilt));
        request(control, "POST", secondIndex + "/_refresh", 200);
        assertThat(adapter.search(firstTarget, criteria, null, 10).getItems())
            .extracting(SearchDocument::getCandidateId)
            .containsExactlyInAnyOrder("candidate-a", "candidate-b", "candidate-c", "legacy");
        adapter.activateRebuild(secondAttempt);
        adapter.activateRebuild(secondAttempt);
        assertThatThrownBy(() -> adapter.beginRebuild(firstAttempt))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.activateRebuild(firstAttempt))
            .isInstanceOf(PlatformException.class);
        assertAlias(control, secondIndex);
        assertThat(adapter.search(secondTarget, criteria, null, 10).getItems()).extracting(SearchDocument::getCandidateId)
            .containsExactly("rebuilt-candidate");
        adapter.upsertBatch(secondTarget, List.of(document("after-switch", "Rust", timestamp)));
        request(control, "POST", secondIndex + "/_refresh", 200);
        assertThat(adapter.search(secondTarget, criteria, null, 10).getItems()).extracting(SearchDocument::getCandidateId)
            .containsExactly("after-switch", "rebuilt-candidate");
        assertThat(json.readTree(request(control, "GET", firstIndex + "/_count", 200)).path("count").asInt())
            .isEqualTo(5);
      } catch (Exception failure) {
        throw safeFailure(failure);
      } finally {
        try {
          for (String index : owned.reversed()) {
            adapter.retireIndex(index, resourceIds.get(index));
            request(control, "HEAD", index, 404);
            request(control, "HEAD", writeAlias(alias, index), 404);
          }
          if (!owned.isEmpty()) {
            request(control, "HEAD", alias, 404);
            request(control, "HEAD", fence, 404);
          }
        } finally {
          HttpClient adapterClient = (HttpClient) ReflectionTestUtils.getField(adapter, "http");
          if (adapterClient != null) {
            try {
              adapter.closeClient();
              assertThat(adapterClient.awaitTermination(Duration.ofSeconds(2)))
                  .as("真实集群调用后的客户端必须由生产回调终止").isTrue();
            } finally {
              adapterClient.shutdownNow();
              adapterClient.close();
            }
          }
        }
      }
    }
  }

  @ParameterizedTest
  @CsvSource({"false,false", "true,false", "false,true", "true,true"})
  void simultaneousInitializersKeepAliasesIsolatedAndLeaveOnlyTheWinningIndices(
      boolean sameAlias, boolean loseCreateResponse)
      throws Exception {
    String otherAlias = sameAlias ? alias : alias + "-other";
    List<String> names = List.of(alias, otherAlias);
    List<UUID> suffixes = List.of(UUID.randomUUID(), UUID.randomUUID());
    List<String> preciseTargets = suffixes.stream()
        .map(suffix -> "resume-facts-v" + suffix.toString().replace("-", "")).toList();
    var candidates = new HashSet<>(preciseTargets);
    var scopes = new HashMap<String, String>();
    for (int slot = 0; slot < 2; slot++) {
      String physical = preciseTargets.get(slot);
      scopes.put(physical, names.get(slot));
      resourceIds.put(physical, UUID.randomUUID().toString().replace("-", "").substring(0, 26));
      allowed.add(writeAlias(names.get(slot), physical));
    }
    allowed.addAll(names);
    allowed.addAll(candidates);
    var adapters = List.of(adapterFor(alias), adapterFor(otherAlias));
    var transports = new ArrayList<HttpClient>();
    var bothCreating = new CountDownLatch(2);
    try (var control = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      try {
        for (String resource : allowed) request(control, "HEAD", resource, 404);
        // 两个显式创建名称与 immutable write alias 已全部预检；失败不能当作成功重试。
        owned.addAll(candidates);
        for (var adapter : adapters) {
          adapter.initializeClient();
          var transport = (HttpClient) ReflectionTestUtils.getField(adapter, "http");
          transports.add(transport);
          var gate = mock(HttpClient.class);
          doAnswer(invocation -> {
            HttpRequest outgoing = invocation.getArgument(0);
            HttpResponse.BodyHandler<InputStream> handler = invocation.getArgument(1);
            String path = base.relativize(outgoing.uri()).getPath();
            if (outgoing.method().equals("PUT")
                && !path.endsWith("/_mapping") && !path.endsWith("/_settings")) {
              assertThat(candidates).as("初始化只能创建已预检的随机候选索引").contains(path);
              bothCreating.countDown();
              assertThat(bothCreating.await(3, TimeUnit.SECONDS)).isTrue();
            }
            // 响应完全来自真实集群；上方仅同步两个显式 PUT 的开始，不再依赖创建前 HEAD。
            return transport.sendAsync(outgoing, handler).thenApply(response -> {
              if (loseCreateResponse && outgoing.method().equals("PUT")
                  && candidates.contains(path) && response.statusCode() == 200) {
                throw new CompletionException(new IOException("synthetic committed creation response lost"));
              }
              return response;
            });
          }).when(gate).sendAsync(any(HttpRequest.class),
              ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any());
          ReflectionTestUtils.setField(adapter, "http", gate);
        }
        var failures = new ArrayList<String>();
        try (var workers = Executors.newFixedThreadPool(2)) {
          var calls = new ArrayList<Future<?>>();
          for (int index = 0; index < 2; index++) {
            int slot = index;
            calls.add(workers.submit(() -> {
              adapters.get(slot).createBootstrap(preciseTargets.get(slot), resourceIds.get(preciseTargets.get(slot)));
            }));
          }
          try {
            for (var call : calls) {
              try {
                call.get(20, TimeUnit.SECONDS);
              } catch (ExecutionException failure) {
                Throwable cause = failure.getCause();
                failures.add(cause instanceof PlatformException platform
                    ? platform.code() + ": " + platform.getMessage() : cause.getClass().getSimpleName());
              }
            }
          } finally {
            calls.forEach(call -> call.cancel(true));
          }
        }
        // 所有发送者 future 均已完成；只回收可验证 marker 的本例竞争 loser，不改变失败结果。
        var winners = new HashMap<String, String>();
        for (String name : new HashSet<>(names)) {
          JsonNode state = json.readTree(request(control, "GET", name + "/_alias", 200));
          assertThat(state.size()).isEqualTo(1);
          String winner = state.properties().iterator().next().getKey();
          assertThat(candidates).contains(winner);
          winners.put(name, winner);
        }
        // 未知响应不视为成功；所有原请求终止后，夹具显式核对/纳管精确 marker 再使用赢家。
        for (String name : new HashSet<>(names)) {
          String winner = winners.get(name);
          int slot = names.indexOf(name);
          assertOwnedMarker(control, winner, resourceIds.get(winner), name);
          if (loseCreateResponse) assertThat(adapters.get(slot).adoptIndex(winner, resourceIds.get(winner))).isTrue();
          assertWriteAlias(control, name, winner);
        }
        for (int slot = 0; slot < 2; slot++) {
          String physical = preciseTargets.get(slot);
          if (!physical.equals(winners.get(names.get(slot)))
              && response(control, "HEAD", physical, 200, 404).statusCode() == 200) {
            assertOwnedMarker(control, physical, resourceIds.get(physical), names.get(slot));
            adapters.get(slot).retireIndex(physical, resourceIds.get(physical));
          }
        }
        int existing = 0;
        for (String index : candidates) {
          if (response(control, "HEAD", index, 200, 404).statusCode() == 200) existing++;
        }
        assertThat(failures).as("显式创建的失败/未知响应不能被别名回查伪装成功")
            .hasSize(loseCreateResponse ? 2 : sameAlias ? 1 : 0);
        assertThat(existing).as("不能留下未绑定别名的竞争失败索引").isEqualTo(sameAlias ? 1 : 2);
        for (String name : new HashSet<>(names)) {
          JsonNode state = json.readTree(request(control, "GET", name + "/_alias", 200));
          assertThat(state.size()).isEqualTo(1);
          long writable = candidates.stream().filter(index -> state.path(index).path("aliases")
              .path(name).path("is_write_index").asBoolean()).count();
          assertThat(writable).isEqualTo(1);
        }
        Instant updated = Instant.parse("2026-09-01T00:00:00Z");
        for (int index = 0; index < 2; index++) {
          adapters.get(index).upsertBatch(new SearchIndexTarget(winners.get(names.get(index)), 1), List.of(document("initial-" + index, "Java", updated)));
          request(control, "POST", names.get(index) + "/_refresh", 200);
        }
        for (int index = 0; index < 2; index++) {
          var found = adapters.get(index).search(new SearchIndexTarget(winners.get(names.get(index)), 1), new SearchCriteria(), null, 10).getItems();
          assertThat(found).extracting(SearchDocument::getCandidateId).containsExactlyElementsOf(
              sameAlias ? List.of("initial-0", "initial-1") : List.of("initial-" + index));
        }
      } finally {
        try {
          for (String index : owned.reversed()) {
            int slot = preciseTargets.indexOf(index);
            adapters.get(slot).retireIndex(index, resourceIds.get(index));
            request(control, "HEAD", index, 404);
            request(control, "HEAD", writeAlias(scopes.get(index), index), 404);
          }
          if (!owned.isEmpty()) {
            for (String name : new HashSet<>(names)) request(control, "HEAD", name, 404);
          }
        } finally {
          for (int index = 0; index < transports.size(); index++) {
            ReflectionTestUtils.setField(adapters.get(index), "http", transports.get(index));
            adapters.get(index).closeClient();
            transports.get(index).close();
          }
        }
      }
    }
  }

  private void createOwned(ElasticsearchSearchIndexAdapter adapter, SearchRebuildAttempt attempt,
      HttpClient control) throws Exception {
    String index = attempt.indexName();
    assertThat(allowed).contains(index, writeAlias(alias, index));
    owned.add(index);
    String resourceId = UUID.randomUUID().toString().replace("-", "").substring(0, 26);
    resourceIds.put(index, resourceId);
    adapter.createRebuildTarget(attempt, resourceId);
    assertOwnedMarker(control, index, resourceId, alias);
    assertWriteAlias(control, alias, index);
    adapter.beginRebuild(attempt);
  }

  private void assertOwnedMarker(HttpClient control, String index, String resourceId, String ownerAlias)
      throws Exception {
    JsonNode mapping = json.readTree(request(control, "GET", index + "/_mapping", 200));
    assertThat(mapping.size()).isOne();
    assertThat(mapping.path(index).path("mappings").path("_meta").path("hr_ai_resource_id").asString())
        .isEqualTo(resourceId);
    assertThat(mapping.path(index).path("mappings").path("_meta").path("hr_ai_owner_alias").asString())
        .isEqualTo(ownerAlias);
  }

  private void assertWriteAlias(HttpClient control, String ownerAlias, String index) throws Exception {
    String writeAlias = writeAlias(ownerAlias, index);
    JsonNode state = json.readTree(request(control, "GET", writeAlias + "/_alias", 200));
    assertThat(state.size()).isOne();
    assertThat(state.path(index).path("aliases").path(writeAlias).path("is_write_index").asBoolean()).isTrue();
  }

  private static String writeAlias(String ownerAlias, String index) {
    return "resume-write-" + Sha256Utils.hashUtf8(ownerAlias + ":" + index);
  }

  private void assertAlias(HttpClient control, String expected) throws Exception {
    JsonNode response = json.readTree(request(control, "GET", alias + "/_alias", 200));
    assertThat(response.size()).isEqualTo(1);
    assertThat(response.path(expected).path("aliases").path(alias).path("is_write_index").asBoolean())
        .isTrue();
  }

  private String request(HttpClient control, String method, String path, Integer... expected) throws Exception {
    return response(control, method, path, expected).body();
  }

  private String requestWithBody(
      HttpClient control, String method, String path, String body, Integer... expected) throws Exception {
    assertThat(allowed).as("控制请求只能访问本次随机资源").contains(path.split("/", 2)[0]);
    var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(15))
        .header("Content-Type", "application/json")
        .method(method, HttpRequest.BodyPublishers.ofString(body));
    if (authorization != null) request.header("Authorization", authorization);
    var pending = control.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString());
    try {
      var response = pending.get(20, TimeUnit.SECONDS);
      assertThat(response.statusCode()).as("隔离资源控制请求状态").isIn(List.of(expected));
      return response.body();
    } finally {
      if (!pending.isDone()) pending.cancel(true);
    }
  }

  private HttpResponse<String> response(HttpClient control, String method, String path, Integer... expected)
      throws Exception {
    assertThat(allowed).as("控制请求只能访问本次随机资源").contains(path.split("/", 2)[0]);
    var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(15))
        .method(method, HttpRequest.BodyPublishers.noBody());
    if (authorization != null) request.header("Authorization", authorization);
    var pending = control.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString());
    try {
      var response = pending.get(20, TimeUnit.SECONDS);
      assertThat(response.statusCode()).as("隔离资源控制请求状态").isIn(List.of(expected));
      return response;
    } finally {
      if (!pending.isDone()) pending.cancel(true);
    }
  }

  private static SearchDocument document(String id, String skill, Instant timestamp) {
    var document = new SearchDocument();
    document.setCandidateId(id);
    document.setProjectionVersion((1L << 32) | 1);
    document.setResumeVersionId("synthetic-resume-" + id);
    document.setFactSnapshotId("synthetic-fact-" + id);
    document.setFullName("Synthetic Candidate");
    document.setSkills(List.of(skill));
    document.setCity("Shanghai");
    document.setSearchText("Synthetic " + skill);
    document.setUpdateTime(timestamp);
    return document;
  }

  private static AssertionError safeFailure(Exception failure) {
    String category = failure instanceof PlatformException platform ? platform.code()
        : failure.getClass().getSimpleName();
    return new AssertionError("真实 Elasticsearch 隔离验证失败，未输出供应商载荷：" + category);
  }

  private static String required(String name) {
    String value = System.getenv(name);
    assertThat(value).as("必须显式配置 %s", name).isNotBlank();
    return value;
  }
}
