package com.ezaigc.hr.integration.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchCriteria;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchRebuildAttempt;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import org.mockito.ArgumentMatchers;

class ElasticsearchSearchIndexAdapterResponseTest {
  @ParameterizedTest
  @MethodSource("unconfirmedCreates")
  void indexCreationRequiresAnExplicitAcknowledgementForTheExactPhysicalTarget(boolean bootstrap, String body) {
    var http = mock(HttpClient.class);
    var result = response(200);
    var attempt = new SearchRebuildAttempt("ack-run", 1);
    String name = bootstrap ? "resume-facts-vack-bootstrap" : attempt.indexName();
    when(result.body()).thenReturn(body.replace("EXPECTED_INDEX", name));
    doReturn(CompletableFuture.completedFuture(result)).when(http).sendAsync(any(HttpRequest.class),
        ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    var adapter = adapter(http);
    assertThatExceptionOfType(PlatformException.class).isThrownBy(() -> {
      if (bootstrap) adapter.createBootstrap(name, "resource-ack");
      else adapter.createRebuildTarget(attempt, "resource-ack");
    }).satisfies(failure -> assertThat(failure.code()).isEqualTo("SEARCH_INDEX_UNAVAILABLE"));
    var requests = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).sendAsync(requests.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThat(requests.getValue().method()).isEqualTo("PUT");
    assertThat(requests.getValue().uri().getPath()).isEqualTo("/" + name);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void acknowledgedCreationIsKnownEvenWhenItsShardsStillNeedPreparation(boolean bootstrap) {
    var http = mock(HttpClient.class);
    var attempt = new SearchRebuildAttempt("shards-pending", 1);
    String name = bootstrap ? "resume-facts-vshards-bootstrap" : attempt.indexName();
    doReturn(CompletableFuture.completedFuture(creationResponse(name, false))).when(http)
        .sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    var adapter = adapter(http);
    if (bootstrap) adapter.createBootstrap(name, "resource-shards");
    else adapter.createRebuildTarget(attempt, "resource-shards");
    verify(http).sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
  }

  private static Stream<Arguments> unconfirmedCreates() {
    return Stream.of(true, false).flatMap(bootstrap -> Stream.of(
        "{\"acknowledged\":false,\"shards_acknowledged\":false,\"index\":\"EXPECTED_INDEX\"}",
        "{\"shards_acknowledged\":true,\"index\":\"EXPECTED_INDEX\"}",
        "{\"acknowledged\":true,\"index\":\"foreign-physical\"}")
        .map(body -> Arguments.of(bootstrap, body)));
  }


  @Test
  void retiredTargetCannotBeRecreatedByALateBegin() {
    var http = mock(HttpClient.class);
    doReturn(CompletableFuture.completedFuture(response(404))).when(http).sendAsync(any(HttpRequest.class),
        ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThatExceptionOfType(PlatformException.class).isThrownBy(() ->
        adapter(http).beginRebuild(new SearchRebuildAttempt("retired-run", 1)));
    var requests = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).sendAsync(requests.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThat(requests.getValue().method()).isEqualTo("HEAD");
  }

  @Test
  void alreadyMissingOwnedTargetIsAnIdempotentRetirementWithoutDeletionRequests() {
    var http = mock(HttpClient.class);
    doReturn(CompletableFuture.completedFuture(response(404))).when(http).sendAsync(any(HttpRequest.class),
        ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    adapter(http).retireIndex("retired-physical", "resource-1");
    var requests = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).sendAsync(requests.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThat(requests.getValue().method()).isEqualTo("GET");
    assertThat(requests.getValue().uri().getPath()).isEqualTo("/retired-physical/_mapping");
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"hr_ai_resource_id\":\"other\",\"hr_ai_owner_alias\":\"resume-facts-current\"}",
      "{\"hr_ai_resource_id\":\"resource-1\",\"hr_ai_owner_alias\":\"foreign\"}"})
  void retirementRejectsMissingOrForeignOwnershipBeforeDeletingAnything(String metadata) {
    var http = mock(HttpClient.class);
    var result = response(200);
    when(result.body()).thenReturn("{\"retired-physical\":{\"mappings\":{\"_meta\":" + metadata + "}}}");
    doReturn(CompletableFuture.completedFuture(result)).when(http).sendAsync(any(HttpRequest.class),
        ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThatExceptionOfType(PlatformException.class).isThrownBy(() ->
        adapter(http).retireIndex("retired-physical", "resource-1"));
    verify(http).sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
  }

  @Test
  void ownedRetirementVerifiesTheExactPhysicalMarkerAndTreatsDelete404AsSuccess() {
    var http = mock(HttpClient.class);
    var mapping = response(200);
    when(mapping.body()).thenReturn("{\"retired-physical\":{\"mappings\":{\"_meta\":{"
        + "\"hr_ai_resource_id\":\"resource-1\",\"hr_ai_owner_alias\":\"resume-facts-current\"}}}}");
    doReturn(CompletableFuture.completedFuture(mapping), CompletableFuture.completedFuture(response(404)))
        .when(http).sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    adapter(http).retireIndex("retired-physical", "resource-1");
    var requests = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http, times(2)).sendAsync(requests.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThat(requests.getAllValues()).extracting(HttpRequest::method).containsExactly("GET", "DELETE");
    assertThat(requests.getAllValues().getLast().uri().getPath()).isEqualTo("/retired-physical");
  }

  @Test
  void expiredPublicRequestCannotStartAnyHttpOperation() {
    var http = mock(HttpClient.class);
    var expired = new SearchIndexTarget("approved-physical", 4, System.nanoTime() - 1);
    assertThatExceptionOfType(PlatformException.class).isThrownBy(() ->
        adapter(http).search(expired, emptyCriteria(), null, 10));
    verifyNoInteractions(http);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"hr_ai_managed_bootstrap\":true}",
      "{\"hr_ai_managed_bootstrap\":true,\"hr_ai_bootstrap_alias\":\"other-deployment\"}",
      "{\"hr_ai_managed_bootstrap\":\"true\",\"hr_ai_bootstrap_alias\":\"resume-facts-current\"}"})
  void unboundOrForeignBootstrapMarkersCannotAuthorizeInitialImport(String metadata) {
    var http = mock(HttpClient.class);
    var response = response(200);
    when(response.body()).thenReturn("{\"approved-physical\":{\"mappings\":{\"_meta\":" + metadata + "}}}");
    doReturn(CompletableFuture.completedFuture(response)).when(http).sendAsync(any(HttpRequest.class),
        ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThat(adapter(http).isManagedBootstrap("approved-physical")).isFalse();
  }

  @Test
  void aBootstrapMarkerMustBindTheExactConfiguredAlias() {
    var http = mock(HttpClient.class);
    var response = response(200);
    when(response.body()).thenReturn("{\"approved-physical\":{\"mappings\":{\"_meta\":{"
        + "\"hr_ai_managed_bootstrap\":true,\"hr_ai_bootstrap_alias\":\"resume-facts-current\"}}}}");
    doReturn(CompletableFuture.completedFuture(response)).when(http).sendAsync(any(HttpRequest.class),
        ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThat(adapter(http).isManagedBootstrap("approved-physical")).isTrue();
  }

  @Test
  void aGenerationChangeRejectsThePreviousCursorBeforeSendingHttp() {
    var http = mock(HttpClient.class);
    var response = response(200);
    when(response.body()).thenReturn("{\"timed_out\":false,\"_shards\":{\"failed\":0},\"hits\":{\"hits\":["
        + "{\"_source\":{\"candidateId\":\"a\",\"factSnapshotId\":\"fact-a\",\"updateTime\":\"2026-09-01T00:00:00Z\"}},"
        + "{\"_source\":{\"candidateId\":\"b\",\"factSnapshotId\":\"fact-b\",\"updateTime\":\"2026-09-01T00:00:00Z\"}}]}}");
    doReturn(CompletableFuture.completedFuture(response)).when(http).sendAsync(any(HttpRequest.class),
        ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    var adapter = adapter(http);
    ReflectionTestUtils.setField(adapter, "cursorCodec", new SignedCursorCodec("synthetic-approval-cursor-key-0000001"));
    var first = adapter.search(new SearchIndexTarget("approved-physical", 4), emptyCriteria(), null, 1);
    assertThat(first.getNextCursor()).isNotBlank();
    assertThatExceptionOfType(PlatformException.class).isThrownBy(() ->
        adapter.search(new SearchIndexTarget("approved-physical", 5), emptyCriteria(), first.getNextCursor(), 1))
        .satisfies(failure -> assertThat(failure.code()).isEqualTo("SEARCH_CURSOR_SCOPE_MISMATCH"));
    verify(http).sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
  }

  @Test
  void approvedPhysicalSearchDoesNotConsultOrRouteThroughTheBusinessAlias() {
    var http = mock(HttpClient.class);
    var response = response(200);
    when(response.body()).thenReturn("{\"timed_out\":false,\"_shards\":{\"failed\":0},\"hits\":{\"hits\":[]}}");
    doReturn(CompletableFuture.completedFuture(response)).when(http).sendAsync(any(HttpRequest.class),
        ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThat(adapter(http).search(new SearchIndexTarget("approved-physical", 4), emptyCriteria(), null, 10)
        .getItems()).isEmpty();
    var request = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).sendAsync(request.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThat(request.getValue().uri().getPath()).isEqualTo("/approved-physical/_search");
  }

  @Test
  void missingApprovedPhysicalIndexFailsClosedWithoutAliasFallbackOrRecreation() {
    var http = mock(HttpClient.class);
    doReturn(CompletableFuture.completedFuture(response(404))).when(http).sendAsync(any(HttpRequest.class),
        ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThatExceptionOfType(PlatformException.class)
        .isThrownBy(() -> adapter(http).ensureApprovedIndex(new SearchIndexTarget("approved-physical", 0)));
    var request = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).sendAsync(request.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThat(request.getValue().method()).isEqualTo("HEAD");
    assertThat(request.getValue().uri().getPath()).isEqualTo("/approved-physical");
  }

  @Test
  void onlyNewBootstrapCreationCarriesTheManagedInitializationMarker() {
    var http = mock(HttpClient.class);
    doReturn(CompletableFuture.completedFuture(creationResponse("resume-facts-vbootstrap", true)))
        .when(http).sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    adapter(http).createBootstrap("resume-facts-vbootstrap", "resource-bootstrap");
    var requests = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).sendAsync(requests.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThat(requestBody(requests.getValue()))
        .contains("\"hr_ai_managed_bootstrap\":true", "\"hr_ai_bootstrap_alias\":\"resume-facts-current\"");
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"_shards\":{\"failed\":1,\"successful\":1}}",
      "{\"_shards\":{\"failed\":0,\"successful\":0}}", "{\"_shards\":{\"successful\":1}}"})
  void incompleteRefreshCannotPrepareATargetForApproval(String body) {
    var http = mock(HttpClient.class);
    var response = response(200);
    when(response.body()).thenReturn(body);
    doReturn(CompletableFuture.completedFuture(response)).when(http).sendAsync(any(HttpRequest.class),
        ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThatExceptionOfType(PlatformException.class)
        .isThrownBy(() -> adapter(http).prepareRebuild(new SearchRebuildAttempt("run-1", 1)));
  }
  @ParameterizedTest
  @ValueSource(strings = {
      "{\"timed_out\":true,\"_shards\":{\"failed\":0},\"hits\":{\"hits\":[]}}",
      "{\"timed_out\":false,\"_shards\":{\"failed\":1},\"hits\":{\"hits\":[]}}",
      "{\"timed_out\":false,\"terminated_early\":true,\"_shards\":{\"failed\":0},\"hits\":{\"hits\":[]}}",
      "{\"hits\":{\"hits\":[]}}",
      "{\"timed_out\":\"false\",\"_shards\":{\"failed\":0},\"hits\":{\"hits\":[]}}",
      "{\"timed_out\":false,\"_shards\":{\"failed\":\"0\"},\"hits\":{\"hits\":[]}}"
  })
  void rejectsIncompleteSearchEvenWhenHttpStatusIsSuccessful(String body) {
    HttpClient http = mock(HttpClient.class);
    HttpResponse<String> result = response(200);
    when(result.body()).thenReturn(body);
    doReturn(CompletableFuture.completedFuture(result)).when(http).sendAsync(any(HttpRequest.class),
        ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThatExceptionOfType(PlatformException.class)
        .isThrownBy(() -> adapter(http).search(new SearchIndexTarget("approved-physical", 4), emptyCriteria(), null, 20))
        .satisfies(failure -> {
          assertThat(failure.code()).isEqualTo("SEARCH_INDEX_UNAVAILABLE");
          assertThat(failure.recoverable()).isTrue();
        });
  }

  @Test
  void createsRebuildIndexWithConfiguredReplicaCount() {
    HttpClient http = mock(HttpClient.class);
    doReturn(CompletableFuture.completedFuture(creationResponse(new SearchRebuildAttempt("synthetic-rebuild", 1).indexName(), true)))
        .when(http).sendAsync(any(HttpRequest.class),
            ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    var attempt = new SearchRebuildAttempt("synthetic-rebuild", 1);

    adapter(http).createRebuildTarget(attempt, "resource-rebuild");

    var requests = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).sendAsync(requests.capture(),
        ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    HttpRequest create = requests.getValue();
    assertThat(create.method()).isEqualTo("PUT");
    assertThat(create.uri().getPath()).isEqualTo("/" + attempt.indexName());
    assertThat(requestBody(create)).contains("\"number_of_replicas\":1").doesNotContain("%d");
    assertThat(requestBody(create)).doesNotContain("hr_ai_managed_bootstrap");
  }

  @Test
  void sendsAtomicVersionTupleUpdateAndRejectsRemainingConflict() {
    HttpClient http = mock(HttpClient.class);
    var conflict = response(200);
    when(conflict.body()).thenReturn("{\"errors\":true,\"items\":[{\"update\":{\"status\":409,"
        + "\"error\":{\"type\":\"version_conflict_engine_exception\"}}}]}");
    doReturn(CompletableFuture.completedFuture(conflict)).when(http).sendAsync(any(HttpRequest.class),
        ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    var document = new SearchDocument();
    document.setCandidateId("candidate");
    document.setProjectionVersion((2L << 32) | 1);
    document.setCandidateVersion(7);
    assertThatExceptionOfType(PlatformException.class)
        .isThrownBy(() -> adapter(http).upsertBatch(new SearchIndexTarget("approved-physical", 0), List.of(document)));
    var requests = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http, times(2)).sendAsync(requests.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThat(requests.getAllValues().getFirst().uri().getPath()).isEqualTo("/approved-physical");
    String body = requestBody(requests.getAllValues().getLast());
    assertThat(body)
        .contains("\"update\"", "\"retry_on_conflict\":3", "\"scripted_upsert\":true",
            "\"contentVersion\":8589934593", "\"candidateVersion\":7",
            "ctx._version", "ctx.op = 'noop'", "Long.MAX_VALUE")
        .doesNotContain("\"version_type\":\"external\"");
    assertThat(new ObjectMapper().writeValueAsString(document)).doesNotContain("projectionVersion");
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "{\"errors\":true,\"items\":[{\"update\":{\"status\":409,\"error\":{\"type\":\"other_conflict\"}}}]}",
      "{\"errors\":false,\"items\":[{\"update\":{\"status\":503}}]}",
      "{\"errors\":false,\"items\":[{\"update\":{}}]}",
      "{\"errors\":true,\"items\":[{\"update\":{\"status\":201}}]}",
      "{\"errors\":true,\"items\":[{\"update\":{\"status\":409,\"error\":{\"type\":\"version_conflict_engine_exception\"}}},"
          + "{\"update\":{\"status\":429}}]}"
  })
  void rejectsOtherConflictsMalformedItemsAndMixedFailures(String body) {
    HttpClient http = mock(HttpClient.class);
    var result = response(200);
    when(result.body()).thenReturn(body);
    doReturn(CompletableFuture.completedFuture(result)).when(http).sendAsync(any(HttpRequest.class),
        ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    var document = new SearchDocument();
    document.setCandidateId("candidate");
    document.setProjectionVersion((1L << 32) | 1);
    int count = new ObjectMapper().readTree(body).path("items").size();
    assertThatExceptionOfType(PlatformException.class).isThrownBy(() -> adapter(http)
        .upsertBatch(new SearchIndexTarget("approved-physical", 0), count == 1 ? List.of(document) : List.of(document, document)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "[]", "{\"a\":{},\"b\":{}}", "{\"a\":{\"aliases\":{}}}",
      "{\"a\":{\"aliases\":{\"resume-facts-current\":{\"is_write_index\":false}}}}",
      "{\"INVALID\":{\"aliases\":{\"resume-facts-current\":{}}}}"})
  void refusesMissingAmbiguousOrNonWritableIndexMetadata(String body) {
    HttpClient http = mock(HttpClient.class);
    var metadata = response(200);
    when(metadata.body()).thenReturn(body);
    doReturn(CompletableFuture.completedFuture(metadata)).when(http).sendAsync(any(HttpRequest.class),
        ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThatExceptionOfType(PlatformException.class).isThrownBy(() -> adapter(http).activeIndex())
        .satisfies(failure -> assertThat(failure.code()).isEqualTo("SEARCH_INDEX_UNAVAILABLE"));
  }

  @Test
  void resolvesAliasAgainForEachPageAndAcceptsASingleImplicitWriteIndex() {
    HttpClient http = mock(HttpClient.class);
    var before = response(200);
    var after = response(200);
    when(before.body()).thenReturn("{\"before\":{\"aliases\":{\"resume-facts-current\":{}}}}");
    when(after.body()).thenReturn("{\"after\":{\"aliases\":{\"resume-facts-current\":{\"is_write_index\":true}}}}");
    doReturn(CompletableFuture.completedFuture(before), CompletableFuture.completedFuture(after))
        .when(http).sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    var adapter = adapter(http);
    assertThat(adapter.activeIndex()).isEqualTo("before");
    assertThat(adapter.activeIndex()).isEqualTo("after");
    var requests = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http, times(2)).sendAsync(requests.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThat(requests.getAllValues()).allSatisfy(request -> {
      assertThat(request.method()).isEqualTo("GET");
      assertThat(request.uri().getPath()).isEqualTo("/_alias/resume-facts-current");
    });
  }

  @ParameterizedTest
  @ValueSource(ints = {403, 503})
  void failedExplicitBootstrapCreationNeverAdoptsAnAliasOrStartsAnotherCreation(int status) {
    HttpClient http = mock(HttpClient.class);
    doReturn(CompletableFuture.completedFuture(response(status))).when(http).sendAsync(any(HttpRequest.class),
        ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThatExceptionOfType(PlatformException.class)
        .isThrownBy(() -> adapter(http).createBootstrap("resume-facts-vbootstrap", "resource-bootstrap"))
        .satisfies(failure -> {
          assertThat(failure.code()).isEqualTo("SEARCH_INDEX_UNAVAILABLE");
          assertThat(failure.getMessage()).contains("HTTP " + status);
        });
    var request = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).sendAsync(request.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThat(request.getValue().method()).isEqualTo("PUT");
    assertThat(request.getValue().uri().getPath()).isEqualTo("/resume-facts-vbootstrap");
  }

  @Test
  void initializationAppliesReplicaCountToAnExistingIndex() {
    HttpClient http = mock(HttpClient.class);
    doReturn(
            CompletableFuture.completedFuture(response(200)),
            CompletableFuture.completedFuture(response(200)),
            CompletableFuture.completedFuture(response(200)))
        .when(http)
        .sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    var adapter = adapter(http);
    ReflectionTestUtils.setField(adapter, "numberOfReplicas", 0);

    adapter.ensureApprovedIndex(new SearchIndexTarget("approved-physical", 0));

    var requests = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http, times(4))
        .sendAsync(requests.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThat(requests.getAllValues())
        .extracting(request -> request.method() + " " + request.uri().getPath())
        .containsExactly(
            "HEAD /approved-physical",
            "PUT /approved-physical/_mapping",
            "PUT /approved-physical/_settings",
            "POST /_aliases");
    assertThat(requests.getAllValues()).allSatisfy(request ->
        assertThat(request.uri().getPath()).doesNotContain("resume-facts-current"));
    assertThat(requestBody(requests.getAllValues().get(1))).contains("contentVersion", "candidateVersion");
    assertThat(requestBody(requests.getAllValues().get(2)))
        .isEqualTo("{\"index\":{\"number_of_replicas\":0}}");
    assertThat(requestBody(requests.getAllValues().get(3)))
        .contains("resume-write-", "approved-physical").doesNotContain("resume-facts-current");
  }

  private static HttpResponse<String> creationResponse(String indexName, boolean shardsAcknowledged) {
    var result = response(200);
    when(result.body()).thenReturn("{\"acknowledged\":true,\"shards_acknowledged\":"
        + shardsAcknowledged + ",\"index\":\"" + indexName + "\"}");
    return result;
  }

  private static HttpResponse<String> response(int status) {
    @SuppressWarnings("unchecked")
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    return response;
  }

  private static String requestBody(HttpRequest request) {
    var subscriber = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
    request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
      @Override public void onSubscribe(Flow.Subscription subscription) { subscriber.onSubscribe(subscription); }
      @Override public void onNext(ByteBuffer buffer) { subscriber.onNext(List.of(buffer)); }
      @Override public void onError(Throwable error) { subscriber.onError(error); }
      @Override public void onComplete() { subscriber.onComplete(); }
    });
    return subscriber.getBody().toCompletableFuture().join();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void cancelsUnderlyingRequestOnTimeoutOrInterruption(boolean interrupted) {
    HttpClient http = mock(HttpClient.class);
    var pending = new CompletableFuture<HttpResponse<String>>();
    doReturn(pending).when(http).sendAsync(any(HttpRequest.class),
        ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    var adapter = adapter(http);
    ReflectionTestUtils.setField(adapter, "timeout", Duration.ofMillis(10));

    try {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
      assertThatExceptionOfType(PlatformException.class)
          .isThrownBy(() -> adapter.search(new SearchIndexTarget("approved-physical", 4), emptyCriteria(), null, 20))
          .satisfies(exception -> {
            assertThat(exception.code()).isEqualTo("SEARCH_INDEX_UNAVAILABLE");
            assertThat(exception.recoverable()).isTrue();
            assertThat(exception).hasMessageContaining(interrupted ? "中断" : "超时");
          });
      assertThat(pending.isCancelled()).isTrue();
      assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupted);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void mapsTransportFailureWithoutExposingItsDetails() {
    HttpClient http = mock(HttpClient.class);
    var failure = new IOException("secret transport detail");
    doReturn(CompletableFuture.failedFuture(failure)).when(http)
        .sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());

    assertThatExceptionOfType(PlatformException.class)
        .isThrownBy(() -> adapter(http).search(new SearchIndexTarget("approved-physical", 4), emptyCriteria(), null, 20))
        .satisfies(exception -> {
          assertThat(exception.code()).isEqualTo("SEARCH_INDEX_UNAVAILABLE");
          assertThat(exception.recoverable()).isTrue();
          assertThat(exception).hasCause(failure);
          assertThat(exception.getMessage()).doesNotContain("secret transport detail");
        });
  }

  @Test
  void mapsMalformedSearchResponseStructureToSearchUnavailable() throws Exception {
    HttpClient http = mock(HttpClient.class);
    @SuppressWarnings("unchecked")
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn("{\"timed_out\":false,\"_shards\":{\"failed\":0},\"hits\":{}}");
    doReturn(CompletableFuture.completedFuture(response))
        .when(http)
        .sendAsync(
            any(HttpRequest.class),
            ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    ElasticsearchSearchIndexAdapter adapter = adapter(http);

    assertThatExceptionOfType(PlatformException.class)
        .isThrownBy(() -> adapter.search(new SearchIndexTarget("approved-physical", 4), emptyCriteria(), null, 20))
        .satisfies(
            exception -> {
              assertThat(exception.code()).isEqualTo("SEARCH_INDEX_UNAVAILABLE");
              assertThat(exception.recoverable()).isTrue();
              assertThat(exception).hasMessageContaining("无效响应");
            });
  }

  @Test
  void readsVersionMetadataWithoutExposingContentVersionAsADocumentField() {
    HttpClient http = mock(HttpClient.class);
    var response = response(200);
    when(response.body()).thenReturn(
        "{\"timed_out\":false,\"_shards\":{\"failed\":0},\"hits\":{\"hits\":[{\"_source\":{\"candidateId\":\"candidate\","
            + "\"factSnapshotId\":\"fact\",\"contentVersion\":4294967297,"
            + "\"candidateVersion\":0,\"updateTime\":\"2026-09-01T00:00:00Z\"}}]}}"
    );
    doReturn(CompletableFuture.completedFuture(response)).when(http).sendAsync(any(HttpRequest.class),
        ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());

    assertThat(adapter(http).search(new SearchIndexTarget("approved-physical", 4), emptyCriteria(), null, 10).getItems())
        .singleElement()
        .satisfies(document -> {
          assertThat(document.getCandidateId()).isEqualTo("candidate");
          assertThat(document.getCandidateVersion()).isZero();
        });
    ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).sendAsync(request.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThat(request.getValue().uri().getQuery()).isEqualTo("allow_partial_search_results=false");
  }

  @Test
  void rejectsPartialBulkFailureWithoutLeakingVendorItemDetails() throws Exception {
    HttpClient http = mock(HttpClient.class);
    @SuppressWarnings("unchecked")
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            "{\"errors\":true,\"items\":[{\"update\":{\"status\":429,\"error\":{\"reason\":\"secret vendor detail\"}}}]}");
    doReturn(CompletableFuture.completedFuture(response))
        .when(http)
        .sendAsync(
            any(HttpRequest.class),
            ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    ElasticsearchSearchIndexAdapter adapter = adapter(http);
    SearchDocument document = new SearchDocument();
    document.setCandidateId("C".repeat(26));
    document.setProjectionVersion((1L << 32) | 1);

    assertThatExceptionOfType(PlatformException.class)
        .isThrownBy(() -> adapter.upsertBatch(new SearchIndexTarget("approved-physical", 0), List.of(document)))
        .satisfies(
            exception -> {
              assertThat(exception.code()).isEqualTo("SEARCH_INDEX_UNAVAILABLE");
              assertThat(exception.recoverable()).isTrue();
              assertThat(exception.getMessage())
                  .contains("失败项")
                  .doesNotContain("secret vendor detail");
            });

    ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http, times(2))
        .sendAsync(
            request.capture(),
            ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    assertThat(request.getAllValues().getFirst().uri().getPath()).isEqualTo("/approved-physical");
    assertThat(request.getAllValues().getLast().uri().getPath()).isEqualTo("/_bulk");
    assertThat(request.getAllValues().getLast().headers().firstValue("Content-Type"))
        .contains("application/x-ndjson");
  }

  private static ElasticsearchSearchIndexAdapter adapter(HttpClient http) {
    ElasticsearchSearchIndexAdapter adapter = new ElasticsearchSearchIndexAdapter();
    ReflectionTestUtils.setField(adapter, "json", new ObjectMapper());
    ReflectionTestUtils.setField(adapter, "cursorCodec", mock(CursorCodec.class));
    ReflectionTestUtils.setField(adapter, "base", URI.create("http://localhost:9200/"));
    ReflectionTestUtils.setField(adapter, "alias", "resume-facts-current");
    ReflectionTestUtils.setField(adapter, "timeout", Duration.ofSeconds(1));
    ReflectionTestUtils.setField(adapter, "numberOfReplicas", 1);
    ReflectionTestUtils.setField(adapter, "http", http);
    return adapter;
  }

  private static SearchCriteria emptyCriteria() {
    return new SearchCriteria(
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
