package com.ezaigc.hr.integration.search;

import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.INDEX_NAME_PATTERN;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_PROJECTION_PAGE_SIZE;

import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.integration.config.IntegrationProperties;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.domain.dto.IndexPage;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchCriteria;
import com.ezaigc.hr.talent.api.DegreeQueryAliases;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchRebuildAttempt;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Getter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@ConditionalOnProperty(name = "hr.search.mode", havingValue = "elasticsearch")
public class ElasticsearchSearchIndexAdapter implements SearchIndexPort {

    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String NDJSON_CONTENT_TYPE = "application/x-ndjson";

    private static final String MAPPING =
            """
                    {"settings":{"index":{"number_of_shards":1,"number_of_replicas":%d}},"mappings":{"dynamic":"strict","properties":{
                     "candidateId":{"type":"keyword"},"resumeVersionId":{"type":"keyword"},"factSnapshotId":{"type":"keyword"},
                     "fullName":{"type":"text","fields":{"keyword":{"type":"keyword"}}},
                     "currentRole":{"type":"text","fields":{"keyword":{"type":"keyword"}}},"city":{"type":"keyword"},
                     "employers":{"type":"text","fields":{"keyword":{"type":"keyword"}}},"roles":{"type":"text","fields":{"keyword":{"type":"keyword"}}},
                     "projects":{"type":"text","fields":{"keyword":{"type":"keyword"}}},"skills":{"type":"text","fields":{"keyword":{"type":"keyword"}}},
                     "certificates":{"type":"text","fields":{"keyword":{"type":"keyword"}}},"languages":{"type":"keyword"},"degrees":{"type":"keyword"},
                     "industries":{"type":"keyword"},"tags":{"type":"keyword"},"workYears":{"type":"scaled_float","scaling_factor":100},
                     "sourceChannel":{"type":"keyword"},"intention":{"type":"text","fields":{"keyword":{"type":"keyword"}}},"gender":{"type":"keyword"},
                     "age":{"type":"integer"},"searchText":{"type":"text"},"updateTime":{"type":"date"},
                     "contentVersion":{"type":"long","index":false},"candidateVersion":{"type":"long","index":false}}}}
                    """;
    private static final String VERSION_MAPPING =
            "{\"properties\":{\"contentVersion\":{\"type\":\"long\",\"index\":false},"
                    + "\"candidateVersion\":{\"type\":\"long\",\"index\":false}}}";
    private static final String VERSION_SCRIPT =
            "if (ctx.op == 'create') { ctx._source = params.document; } else { "
                    + "long content = ctx._source.containsKey('contentVersion') ? ctx._source.contentVersion : ctx._version; "
                    + "long candidate = ctx._source.containsKey('candidateVersion') ? ctx._source.candidateVersion : -1; "
                    + "if (content != Long.MAX_VALUE && (params.contentVersion > content || "
                    + "(params.contentVersion == content && params.candidateVersion > candidate))) { "
                    + "ctx._source = params.document; } else { ctx.op = 'noop'; } }";
    @Resource
    private IntegrationProperties properties;
    @Resource
    private ObjectMapper json;
    @Resource
    private CursorCodec cursorCodec;
    private URI base;
    /**
     * 搜索索引别名。
     */
    private String alias;
    /**
     * 授权。
     */
    private String authorization;
    /**
     * 请求超时时间。
     */
    private Duration timeout;
    /** 索引副本数；单节点部署必须为零，否则集群会长期处于 yellow。 */
    private int numberOfReplicas;
    /**
     * HTTP 客户端。
     */
    private HttpClient http;

    @PostConstruct
    void initializeClient() {
        var elasticsearch = properties.getSearch().getElasticsearch();
        String baseUrl =
                IntegrationProperties.requireConfigured(
                        elasticsearch.getBaseUrl(), "hr.search.elasticsearch.base-url");
        String configuredAlias = elasticsearch.getAlias();
        String apiKey = elasticsearch.getApiKey();
        String username = elasticsearch.getUsername();
        String password = elasticsearch.getPassword();
        if (!elasticsearch.hasValidEndpoint()) {
            throw new IllegalStateException(
                    "hr.search.elasticsearch.base-url must be an HTTP(S) endpoint without credentials");
        }
        if (!elasticsearch.hasCompleteBasicCredentials()) {
            throw new IllegalStateException(
                    "hr.search.elasticsearch.username and password must be configured together");
        }
        timeout = elasticsearch.getTimeout();
        numberOfReplicas = elasticsearch.getNumberOfReplicas();
        base = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        alias = requiredName(configuredAlias);
        authorization =
                !CommonUtils.isBlank(apiKey)
                        ? "ApiKey " + apiKey
                        : !CommonUtils.isBlank(username)
                                ? "Basic "
                                + Base64.getEncoder()
                                .encodeToString(
                                        (username + ":" + password).getBytes(StandardCharsets.UTF_8))
                                : null;
        http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    /** 容器销毁时取消残留请求并关闭连接，防止响应体停滞拖住客户端资源。 */
    @PreDestroy
    void closeClient() {
        if (http != null) {
            http.shutdownNow();
        }
    }

    @Override
    public boolean durable() {
        return true;
    }

    @Override
    public void ensureApprovedIndex(SearchIndexTarget target) {
        String name = requiredName(target.indexName());
        requireIndexExists(target);
        sendForTarget(target, "PUT", name + "/_mapping", VERSION_MAPPING, JSON_CONTENT_TYPE, Set.of(200));
        sendForTarget(target, "PUT", name + "/_settings",
                "{\"index\":{\"number_of_replicas\":" + numberOfReplicas + "}}", JSON_CONTENT_TYPE, Set.of(200));
        sendForTarget(target, "POST", "_aliases", write(Map.of("actions", List.of(
                Map.of("add", Map.of("index", name, "alias", writeAlias(name), "is_write_index", true))))),
                JSON_CONTENT_TYPE, Set.of(200));
    }

    @Override
    public String resourceScope() { return alias; }

    @Override
    public void createBootstrap(String indexName, String resourceId) {
        ObjectNode definition = ownedDefinition(indexName, resourceId);
        ((ObjectNode) definition.path("mappings").path("_meta"))
                .put("hr_ai_managed_bootstrap", true).put("hr_ai_bootstrap_alias", alias);
        ((ObjectNode) definition.path("aliases")).putObject(alias).put("is_write_index", true);
        createOwnedIndex(indexName, definition);
    }

    @Override
    public void createRebuildTarget(SearchRebuildAttempt attempt, String resourceId) {
        createOwnedIndex(attempt.indexName(), ownedDefinition(attempt.indexName(), resourceId));
    }

    private void createOwnedIndex(String indexName, ObjectNode definition) {
        String name = requiredName(indexName);
        JsonNode result = read(send("PUT", name, write(definition), Set.of(200)).body());
        // HTTP200/ack=false 仍可能稍后创建；不能把尚未确认的请求交给 GC。
        if (!result.path("acknowledged").isBoolean() || !result.path("acknowledged").asBoolean()
                || !result.path("index").isString() || !name.equals(result.path("index").asString())) {
            throw unavailable("搜索目标创建结果未确定，请保留未决意图并人工核对");
        }
    }

    private ObjectNode ownedDefinition(String indexName, String resourceId) {
        if (resourceId == null || resourceId.isBlank()) throw new IllegalArgumentException("search resource ID required");
        ObjectNode definition = (ObjectNode) read(mapping());
        ((ObjectNode) definition.path("mappings")).putObject("_meta")
                .put("hr_ai_resource_id", resourceId).put("hr_ai_owner_alias", alias);
        definition.putObject("aliases").putObject(writeAlias(indexName)).put("is_write_index", true);
        return definition;
    }

    @Override
    public void retireIndex(String indexName, String resourceId) {
        String name = requiredName(indexName);
        var response = send("GET", name + "/_mapping", null, Set.of(200, 404));
        if (response.statusCode() == 404) return;
        JsonNode metadata = read(response.body()).path(name).path("mappings").path("_meta");
        if (!read(response.body()).path(name).path("mappings").isObject()) {
            throw unavailable("搜索回收目标不是精确物理索引");
        }
        if (!resourceId.equals(metadata.path("hr_ai_resource_id").asString())
                || !alias.equals(metadata.path("hr_ai_owner_alias").asString())) {
            throw unavailable("搜索目标资源归属不匹配，禁止回收");
        }
        var deleted = send("DELETE", name, null, Set.of(200, 404));
        if (deleted.statusCode() != 404 && !read(deleted.body()).path("acknowledged").asBoolean()) {
            throw unavailable("搜索目标删除尚未确认");
        }
    }

    @Override
    public boolean adoptIndex(String indexName, String resourceId) {
        String name = requiredName(indexName);
        var response = send("GET", name + "/_mapping", null, Set.of(200, 404));
        if (response.statusCode() == 404) return false;
        if (!read(response.body()).path(name).path("mappings").isObject()) {
            throw unavailable("搜索纳管目标不是精确物理索引");
        }
        JsonNode metadata = read(response.body()).path(name).path("mappings").path("_meta");
        String existingId = metadata.path("hr_ai_resource_id").asString("");
        if (!existingId.isBlank() && (!resourceId.equals(existingId)
                || !alias.equals(metadata.path("hr_ai_owner_alias").asString()))) {
            throw unavailable("搜索目标已属于其他资源，禁止纳管");
        }
        ObjectNode updatedMetadata = metadata.isObject() ? ((ObjectNode) metadata).deepCopy() : json.createObjectNode();
        updatedMetadata.put("hr_ai_resource_id", resourceId).put("hr_ai_owner_alias", alias);
        send("PUT", name + "/_mapping", write(Map.of("_meta", updatedMetadata)), Set.of(200));
        ensureApprovedIndex(new SearchIndexTarget(name, 0));
        return true;
    }

    private String writeAlias(String indexName) {
        return "resume-write-" + Sha256Utils.hashUtf8(alias + ":" + requiredName(indexName));
    }

    private void requireIndexExists(String target) {
        if (send("HEAD", target, null, Set.of(200, 404)).statusCode() != 200) {
            throw unavailable("批准的搜索物理索引不存在，请执行受控重建");
        }
    }

    private void requireIndexExists(SearchIndexTarget target) {
        if (sendForTarget(target, "HEAD", requiredName(target.indexName()), null, JSON_CONTENT_TYPE, Set.of(200, 404)).statusCode() != 200) {
            throw unavailable("批准的搜索物理索引不存在，请执行受控重建");
        }
    }

    @Override
    public boolean isManagedBootstrap(String indexName) {
        String target = requiredName(indexName);
        JsonNode metadata = read(send("GET", target + "/_mapping", null, Set.of(200)).body())
                .path(target).path("mappings").path("_meta");
        return metadata.path("hr_ai_managed_bootstrap").isBoolean()
                && metadata.path("hr_ai_managed_bootstrap").asBoolean()
                && metadata.path("hr_ai_bootstrap_alias").isString()
                && alias.equals(metadata.path("hr_ai_bootstrap_alias").asString());
    }

    @Override
    public void prepareRebuild(SearchRebuildAttempt attempt) {
        String target = requiredName(attempt.indexName());
        requireIndexExists(target);
        JsonNode shards = read(send("POST", target + "/_refresh", null, Set.of(200)).body()).path("_shards");
        if (!shards.path("failed").isIntegralNumber() || shards.path("failed").asLong() != 0
                || !shards.path("successful").isIntegralNumber() || shards.path("successful").asLong() < 1) {
            throw unavailable("搜索重建目标尚未完整刷新");
        }
    }

    @Override
    public String activeIndex() {
        var candidate = send("GET", "_alias/" + alias, null, Set.of(200, 404));
        if (candidate.statusCode() == 404) return null;
        JsonNode response = read(candidate.body());
        if (!response.isObject() || response.size() != 1) {
            throw unavailable("搜索业务别名必须对应唯一索引");
        }
        String target = response.properties().iterator().next().getKey();
        JsonNode configuration = response.path(target).path("aliases").path(alias);
        if (!configuration.isObject() || (configuration.has("is_write_index") && !configuration.path("is_write_index").asBoolean())) {
            throw unavailable("搜索业务别名没有可写索引");
        }
        try {
            return requiredName(target);
        } catch (IllegalArgumentException invalidName) {
            throw unavailable("搜索业务别名返回无效索引名称", invalidName);
        }
    }

    @Override
    public IndexPage search(SearchIndexTarget target, SearchCriteria criteria, String cursor, int limit) {
        String indexName = requiredName(target.indexName());
        String fp = fingerprint(criteria);
        String scope = CursorScopes.scope("search.elasticsearch", indexName, target.generation(), fp);
        Cursor after = decode(cursor, scope);
        Map<String, Object> bool = new LinkedHashMap<>();
        List<Object> must = new ArrayList<>(), filter = new ArrayList<>();
        // 无事实占位与永久删除墓碑只保留版本屏障，任何检索条件都不得返回它们。
        filter.add(Map.of("exists", Map.of("field", "factSnapshotId")));
        fullText(must, criteria.getQuery());
        criteria.getConditions().forEach(condition -> fullText(must, condition));
        fullText(must, criteria.getNaturalCondition());
        terms(filter, "skills.keyword", criteria.getSkills());
        terms(filter, "employers.keyword", criteria.getEmployers());
        terms(filter, "roles.keyword", criteria.getRoles());
        terms(filter, "projects.keyword", criteria.getProjects());
        terms(filter, "certificates.keyword", criteria.getCertificates());
        terms(filter, "languages", criteria.getLanguages());
        anyTerms(filter, "degrees", DegreeQueryAliases.expand(criteria.getDegrees()));
        anyTerms(filter, "city", criteria.getCities());
        anyTerms(filter, "candidateId", criteria.getCandidateIds());
        terms(filter, "industries", criteria.getIndustries());
        terms(filter, "tags", criteria.getTags());
        term(filter, "sourceChannel", criteria.getSourceChannel());
        term(filter, "gender", criteria.getGender());
        if (criteria.getIntention() != null && !criteria.getIntention().isBlank()) {
            filter.add(Map.of("match_phrase", Map.of("intention", criteria.getIntention())));
        }
        Map<String, Object> years = range(criteria.getMinWorkYears(), null);
        if (criteria.getMaxWorkYears() != null) {
            if (years == null) years = new LinkedHashMap<>();
            years.put("lt", criteria.getMaxWorkYears());
        }
        if (years != null) {
            filter.add(Map.of("range", Map.of("workYears", years)));
        }
        Map<String, Object> age = range(criteria.getMinAge(), criteria.getMaxAge());
        if (age != null) {
            filter.add(Map.of("range", Map.of("age", age)));
        }
        Map<String, Object> updated = range(criteria.getUpdatedFrom(), criteria.getUpdatedTo());
        if (updated != null) {
            filter.add(Map.of("range", Map.of("updateTime", updated)));
        }
        if (!must.isEmpty()) {
            bool.put("must", must);
        }
        if (!filter.isEmpty()) {
            bool.put("filter", filter);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", limit + 1);
        body.put("track_total_hits", false);
        body.put("query", bool.isEmpty() ? Map.of("match_all", Map.of()) : Map.of("bool", bool));
        body.put(
                "sort",
                List.of(
                        Map.of("updateTime", Map.of("order", "desc")),
                        Map.of("candidateId", Map.of("order", "asc"))));
        if (after != null) {
            body.put("search_after", List.of(after.getUpdateTime().toString(), after.getCandidateId()));
        }
        List<SearchDocument> loaded =
                readSearchDocuments(
                        sendForTarget(target, "POST", indexName + "/_search?allow_partial_search_results=false", write(body), JSON_CONTENT_TYPE, Set.of(200)).body());
        boolean more = loaded.size() > limit;
        List<SearchDocument> page = loaded.stream().limit(limit).toList();
        String next =
                more && !page.isEmpty()
                        ? encode(scope, page.getLast())
                        : null;
        return new IndexPage(page, next, more);
    }

    @Override
    public void beginRebuild(SearchRebuildAttempt attempt) {
        String target = requiredName(attempt.indexName());
        requireIndexExists(target);
        String fence = rebuildFence(attempt);
        HttpResponse<String> response = send("GET", "_alias/" + fence, null, Set.of(200, 404));
        if (response.statusCode() == 404) {
            aliases(List.of(Map.of("add", Map.of("index", target, "alias", fence, "is_write_index", true))));
            return;
        }
        JsonNode current = read(response.body());
        if (current.size() != 1) throw new IllegalStateException("search rebuild fence is not unique");
        String previous = current.properties().iterator().next().getKey();
        if (!current.path(previous).path("aliases").path(fence).path("is_write_index").asBoolean()) {
            throw new IllegalStateException("search rebuild fence is not exclusive");
        }
        int previousAttempt = attempt.attemptOf(previous);
        if (previousAttempt > attempt.attempt()) {
            throw new IllegalStateException("search rebuild attempt superseded");
        }
        if (previousAttempt == attempt.attempt()) return;
        // 条件删除和声明一起提交；迟到的旧声明无法移走较新的围栏。
        aliases(List.of(
                Map.of("remove", Map.of("index", previous, "alias", fence, "must_exist", true)),
                Map.of("add", Map.of("index", target, "alias", fence, "is_write_index", true))));
    }

    @Override
    public void upsertBatch(SearchIndexTarget target, List<SearchDocument> documents) {
        requireIndexExists(target);
        bulkIndex(target, documents);
    }

    /**
     * 激活重建。
     */
    @Override
    public void activateRebuild(SearchRebuildAttempt attempt) {
        String target = requiredName(attempt.indexName());
        String fence = rebuildFence(attempt);
        aliases(
                List.of(
                        // must_exist 使整个动作列表在批次已被替代时失败，包括业务别名切换。
                        Map.of("remove", Map.of("index", target, "alias", fence, "must_exist", true)),
                        Map.of("add", Map.of("index", target, "alias", fence, "is_write_index", true)),
                        Map.of("remove", Map.of("index", "*", "alias", alias, "must_exist", false)),
                        Map.of(
                                "add",
                                Map.of(
                                        "index", target, "alias", alias, "is_write_index", true))));
    }

    private String rebuildFence(SearchRebuildAttempt attempt) {
        return "resume-rebuild-" + Sha256Utils.hashUtf8(alias + ":" + attempt.runId());
    }

    private String mapping() {
        return MAPPING.formatted(numberOfReplicas);
    }

    private void aliases(List<Object> actions) {
        send("POST", "_aliases", write(Map.of("actions", actions)), Set.of(200));
    }

    /**
     * 使用 Elasticsearch NDJSON bulk 协议写入一页投影。部分成功也按整页失败处理，调用方不会推进游标，
     * 下次通过固定文档 ID 幂等覆盖已成功的子项。
     */
    private void bulkIndex(SearchIndexTarget indexTarget, List<SearchDocument> documents) {
        if (documents == null) {
            throw new IllegalArgumentException("search index documents must not be null");
        }
        if (documents.isEmpty()) {
            return;
        }
        if (documents.size() > MAX_PROJECTION_PAGE_SIZE) {
            throw new IllegalArgumentException("search index batch exceeds projection page limit");
        }
        String target = writeAlias(indexTarget.indexName());
        StringBuilder body = new StringBuilder(documents.size() * 768);
        for (SearchDocument document : documents) {
            if (document == null
                    || document.getCandidateId() == null
                    || document.getCandidateId().isBlank()
                    || document.getProjectionVersion() < 1) {
                throw new IllegalArgumentException("search index document candidate ID and positive version are required");
            }
            ObjectNode source = (ObjectNode) read(write(document));
            source.put("contentVersion", document.getProjectionVersion());
            source.put("candidateVersion", document.getCandidateVersion());
            Map<String, Object> params = Map.of(
                    "document", source,
                    "contentVersion", document.getProjectionVersion(),
                    "candidateVersion", document.getCandidateVersion());
            body.append(write(Map.of("update", Map.of("_index", target, "_id", document.getCandidateId(),
                            "retry_on_conflict", 3))))
                    .append('\n')
                    .append(write(Map.of(
                            "scripted_upsert", true,
                            "script", Map.of("lang", "painless", "source", VERSION_SCRIPT, "params", params),
                            "upsert", source)))
                    .append('\n');
        }
        HttpResponse<String> response =
                sendForTarget(indexTarget, "POST", "_bulk?require_alias=true", body.toString(), NDJSON_CONTENT_TYPE, Set.of(200));
        requireSuccessfulBulkResponse(response.body(), documents.size());
    }

    private void requireSuccessfulBulkResponse(String responseBody, int expectedItems) {
        JsonNode root = read(responseBody);
        if (root == null || !root.isObject()) {
            throw unavailable("Elasticsearch 返回了无效批量响应");
        }
        JsonNode errors = root.get("errors");
        JsonNode items = root.get("items");
        if (errors == null
                || !errors.isBoolean()
                || items == null
                || !items.isArray()
                || items.size() != expectedItems) {
            throw unavailable("Elasticsearch 返回了无效批量响应");
        }
        for (JsonNode item : items) {
            JsonNode result = item.path("update");
            JsonNode status = result.path("status");
            if (!status.isIntegralNumber()) {
                throw unavailable("Elasticsearch 批量写入包含无效失败项");
            }
            if ((status.asInt() == 200 || status.asInt() == 201) && !result.has("error")) {
                continue;
            }
            throw unavailable("Elasticsearch 批量写入包含失败项");
        }
        if (errors.asBoolean()) {
            throw unavailable("Elasticsearch 批量写入包含不一致的失败项");
        }
    }

    private HttpResponse<String> send(
            String method, String path, String body, Set<Integer> accepted) {
        return send(method, path, body, JSON_CONTENT_TYPE, accepted);
    }

    private HttpResponse<String> send(
            String method, String path, String body, String contentType, Set<Integer> accepted) {
        return send(method, path, body, contentType, accepted, timeout);
    }

    private HttpResponse<String> sendForTarget(SearchIndexTarget target,
            String method, String path, String body, String contentType, Set<Integer> accepted) {
        Duration remaining = target.remainingTimeout(timeout);
        if (remaining.isZero()) throw unavailable("搜索请求期限已届满，请重试");
        return send(method, path, body, contentType, accepted, remaining, target.deadlineNanos());
    }

    private HttpResponse<String> send(
            String method, String path, String body, String contentType, Set<Integer> accepted,
            Duration operationTimeout) {
        return send(method, path, body, contentType, accepted, operationTimeout, Long.MAX_VALUE);
    }

    private HttpResponse<String> send(
            String method, String path, String body, String contentType, Set<Integer> accepted,
            Duration operationTimeout, long requestDeadline) {
        CompletableFuture<HttpResponse<String>> pending = null;
        try {
            long operationDeadline = System.nanoTime() + operationTimeout.toNanos();
            var b =
                    HttpRequest.newBuilder(base.resolve(path))
                            .timeout(operationTimeout)
                            .header("Accept", "application/json");
            if (authorization != null) {
                b.header("Authorization", authorization);
            }
            if (body == null) {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                b.header("Content-Type", contentType)
                        .method(method, HttpRequest.BodyPublishers.ofString(body));
            }
            pending = http.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString());
            // 请求级 timeout 不覆盖响应体停滞；等待完整响应并在截止后取消底层请求。
            long waitNanos = operationDeadline - System.nanoTime();
            if (requestDeadline != Long.MAX_VALUE) waitNanos = Math.min(waitNanos, requestDeadline - System.nanoTime());
            if (waitNanos <= 0) throw new TimeoutException("search request deadline exhausted before response");
            var response = pending.get(waitNanos, TimeUnit.NANOSECONDS);
            if (!accepted.contains(response.statusCode())) {
                throw unavailable("Elasticsearch 请求失败（HTTP " + response.statusCode() + "）");
            }
            return response;
        } catch (InterruptedException ex) {
            if (pending != null) {
                pending.cancel(true);
            }
            Thread.currentThread().interrupt();
            throw unavailable("Elasticsearch 请求被中断", ex);
        } catch (TimeoutException ex) {
            if (pending != null) {
                pending.cancel(true);
            }
            throw unavailable("Elasticsearch 请求超时", ex);
        } catch (ExecutionException ex) {
            throw unavailable("Elasticsearch 当前不可用", ex.getCause());
        }
    }

    private static void fullText(List<Object> must, String q) {
        if (q != null && !q.isBlank()) {
            must.add(
                    Map.of(
                            "multi_match",
                            Map.of(
                                    "query",
                                    q,
                                    "fields",
                                    List.of(
                                            "searchText",
                                            "fullName",
                                            "currentRole",
                                            "employers",
                                            "roles",
                                            "projects",
                                            "skills",
                                            "certificates",
                                            "intention"),
                                    "operator",
                                    "and")));
        }
    }

    /** 学历与城市为同组任选；不同筛选组仍由外层 bool.filter 同时约束。 */
    private static void anyTerms(List<Object> filter, String field, List<String> values) {
        if (!values.isEmpty()) filter.add(Map.of("terms", Map.of(field, values)));
    }

    private static void terms(List<Object> filter, String field, List<String> values) {
        for (String v : values) {
            filter.add(Map.of("term", Map.of(field, v)));
        }
    }

    private static void term(List<Object> filter, String field, String value) {
        if (value != null && !value.isBlank()) {
            filter.add(Map.of("term", Map.of(field, value)));
        }
    }

    private static Map<String, Object> range(Object min, Object max) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (min != null) {
            out.put("gte", min);
        }
        if (max != null) {
            out.put("lte", max);
        }
        return out.isEmpty() ? null : out;
    }

    private String fingerprint(SearchCriteria c) {
        try {
            return Sha256Utils.hashUtf8(write(c));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String encode(String scope, SearchDocument document) {
        return cursorCodec.encode(
                scope,
                List.of(document.getUpdateTime().toString(), document.getCandidateId()));
    }

    private Cursor decode(String value, String scope) {
        if (CommonUtils.isBlank(value)) {
            return null;
        }
        try {
            List<String> values = cursorCodec.decode(value, scope, 2);
            return new Cursor(Instant.parse(values.get(0)), values.get(1));
        } catch (RuntimeException ex) {
            throw new PlatformException(
                    HttpStatus.CONFLICT, "SEARCH_CURSOR_SCOPE_MISMATCH", "搜索游标不属于当前筛选条件或已失效", true);
        }
    }

    private JsonNode read(String value) {
        try {
            return json.readTree(value);
        } catch (Exception ex) {
            throw unavailable("Elasticsearch 返回了无效响应", ex);
        }
    }

    private List<SearchDocument> readSearchDocuments(String value) {
        JsonNode root = read(value);
        try {
            JsonNode timedOut = root.get("timed_out");
            JsonNode failedShards = root.path("_shards").get("failed");
            JsonNode terminatedEarly = root.get("terminated_early");
            if (timedOut == null || !timedOut.isBoolean() || timedOut.asBoolean()
                    || failedShards == null || !failedShards.isIntegralNumber() || failedShards.asLong() != 0
                    || terminatedEarly != null && (!terminatedEarly.isBoolean() || terminatedEarly.asBoolean())) {
                throw unavailable("Elasticsearch 未返回完整搜索结果，请稍后重试");
            }
            JsonNode hitsContainer = root.get("hits");
            JsonNode hits = hitsContainer == null ? null : hitsContainer.get("hits");
            if (hits == null || !hits.isArray()) {
                throw new IllegalStateException("search hits array is missing");
            }
            List<SearchDocument> loaded = new ArrayList<>();
            for (JsonNode hit : hits) {
                JsonNode source = hit.get("_source");
                if (source == null || !source.isObject()) {
                    throw new IllegalStateException("search hit source is missing");
                }
                loaded.add(readDocument(source));
            }
            return loaded;
        } catch (PlatformException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw unavailable("Elasticsearch 返回了无效响应", ex);
        }
    }

    private SearchDocument readDocument(JsonNode node) {
        try {
            ObjectNode document = (ObjectNode) node.deepCopy();
            document.remove("contentVersion");
            return json.readValue(document.toString(), SearchDocument.class);
        } catch (Exception ex) {
            throw unavailable("Elasticsearch 投影格式与当前版本不兼容", ex);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String requiredName(String value) {
        if (value == null || !value.matches(INDEX_NAME_PATTERN)) {
            throw new IllegalArgumentException("invalid Elasticsearch index or alias name");
        }
        return value;
    }

    private static PlatformException unavailable(String detail) {
        return new PlatformException(
                HttpStatus.SERVICE_UNAVAILABLE, "SEARCH_INDEX_UNAVAILABLE", detail, true);
    }

    private static PlatformException unavailable(String detail, Throwable cause) {
        return new PlatformException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SEARCH_INDEX_UNAVAILABLE",
                detail,
                true,
                cause);
    }

    @Getter(AccessLevel.PACKAGE)
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Cursor {

        private final Instant updateTime;
        private final String candidateId;
    }
}
