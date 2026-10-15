package com.ezaigc.hr.talent.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class TalentIntakeMapperContractTest {

  @Test
  void resumeVersionCursorUsesTheSameKeysAsTheVersionDescendingOrder() {
    var configuration = new MybatisConfiguration();
    configuration.addMapper(TalentIntakeMapper.class);
    var bound = configuration.getMappedStatement(TalentIntakeMapper.class.getName() + ".findResumeVersions")
        .getBoundSql(Map.of("candidateId", "candidate", "cursorVersionNo", 3,
            "cursorId", "anchor", "limit", 2));
    String sql = bound.getSql().replaceAll("\\s+", " ");
    assertThat(sql).contains("r.candidate_id=?", "v.version_no < ?",
        "v.version_no=? AND v.id < ?", "ORDER BY v.version_no DESC,v.id DESC LIMIT ?")
        .doesNotContain("v.create_time <", "v.create_time=?");
    assertThat(bound.getParameterMappings()).extracting(mapping -> mapping.getProperty())
        .containsExactly("candidateId", "cursorVersionNo", "cursorVersionNo",
            "cursorVersionNo", "cursorId", "limit");
  }

  @Test
  void sharedIntakeHistoryIncludesOldAndUnscoredVersionsWithCandidateScopedBindings() {
    var configuration = new MybatisConfiguration();
    configuration.addMapper(TalentIntakeMapper.class);
    var parameters = Map.of("candidateIds", List.of("shared-a", "shared-b"));
    var history = configuration.getMappedStatement(
        TalentIntakeMapper.class.getName() + ".findCandidateResumeHistory").getBoundSql(parameters);
    assertThat(history.getSql()).contains("v.resume_id=r.id", "i.resume_version_id=v.id",
        "r.candidate_id IN", "b.jd_version_id IS NOT NULL", "position_no=1")
        .doesNotContain("current_version_no", "score_status", "i.status=", "owner_user_id");
    assertThat(history.getParameterMappings()).hasSize(2);
    var current = configuration.getMappedStatement(
        TalentIntakeMapper.class.getName() + ".findCurrentCandidateResumes").getBoundSql(parameters);
    assertThat(current.getSql()).contains("v.version_no=r.current_version_no", "i.score_status='SUCCEEDED'");
  }

  @Test
  void conflictCandidateFilterMatchesEitherSideBeforePaginationAndKeepsIntakeVisibility() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    configuration.addMapper(TalentIntakeMapper.class);
    var statement = configuration.getMappedStatement(TalentIntakeMapper.class.getName() + ".findConflicts");
    var bound = statement.getBoundSql(Map.of(
        "candidateId", "candidate", "intakeIds", List.of("intake"),
        "actorId", "actor", "scopeAll", false, "managedUserIds", Set.of("report"),
        "visibleDepartmentIds", Set.of(), "limit", 21));
    String query = bound.getSql().replaceAll("\\s+", " ").toLowerCase();
    assertThat(query).contains(
        "and (? is null or c.candidate_id_a=? or c.candidate_id_b=?)",
        "and c.intake_id in", "and (?=true or i.owner_user_id=? or i.owner_user_id in");
    assertThat(query.indexOf("c.candidate_id_a=?")).isLessThan(query.indexOf("order by"));
    assertThat(bound.getParameterMappings().stream()
        .filter(parameter -> parameter.getProperty().equals("candidateId")).count()).isEqualTo(3);
    assertThat(statement.getBoundSql(Map.of("actorId", "actor", "scopeAll", false,
        "managedUserIds", Set.of(), "visibleDepartmentIds", Set.of(), "limit", 21)).getSql())
        .doesNotContain("c.intake_id IN");
  }

  @Test
  void intakeCountUsesListFiltersAndVisibilityWithoutCursorOrLimit() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    configuration.addMapper(TalentIntakeMapper.class);
    for (var managedUserIds : List.of(Set.<String>of(), Set.of("report"))) {
      Map<String, Object> parameters = Map.of(
          "actorId", "actor", "scopeAll", false, "managedUserIds", managedUserIds,
          "visibleDepartmentIds", Set.of(), "limit", 21);
      String listSql = configuration.getMappedStatement(TalentIntakeMapper.class.getName() + ".findIntakes")
          .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").toLowerCase();
      String countSql = configuration.getMappedStatement(TalentIntakeMapper.class.getName() + ".countIntakes")
          .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").toLowerCase();
      String sharedFilters = listSql.substring(listSql.indexOf(" from talent_resume_intake i join"),
          listSql.lastIndexOf(" and (? is null or i.create_time < ?"));
      assertThat(countSql).isEqualTo("select count(*)" + sharedFilters);
      assertThat(countSql).contains("i.owner_user_id=?", "i.status != 'ignored'",
          "candidate.full_name like", "i.create_time >= ?", "i.create_time < ?",
          "duplicate_intake.content_sha256=i.content_sha256",
          "?='problem'", "?='identity_review'", "?='failed'", "?='missing_jd'",
          "i.score_status in ('failed_retryable','failed_final')")
          .doesNotContain("limit", "order by", "i.id <");
      if (!managedUserIds.isEmpty()) {
        assertThat(countSql).contains("or i.owner_user_id in");
      }
    }
  }

  @Test
  void intakeBatchFilterIsAppliedBeforePaginationAndCount() {
    var configuration = new MybatisConfiguration();
    configuration.addMapper(TalentIntakeMapper.class);
    var parameters = Map.of(
        "batchId", "batch-a", "actorId", "actor", "scopeAll", false,
        "managedUserIds", Set.<String>of(), "visibleDepartmentIds", Set.<String>of(),
        "limit", 21);
    String listSql = configuration.getMappedStatement(TalentIntakeMapper.class.getName() + ".findIntakes")
        .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").toLowerCase();
    String countSql = configuration.getMappedStatement(TalentIntakeMapper.class.getName() + ".countIntakes")
        .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").toLowerCase();

    assertThat(listSql).contains("i.batch_id=?", "i.owner_user_id=?")
        .containsSubsequence("i.batch_id=?", "order by", "limit ?");
    assertThat(countSql).contains("i.batch_id=?", "i.owner_user_id=?")
        .doesNotContain("order by", "limit");
  }

  @Test
  void batchTaskListAndDetailReportParsingProgressWithinTheBatchScope() {
    var configuration = new MybatisConfiguration();
    configuration.addMapper(IntakeBatchMapper.class);
    var parameters = Map.of(
        "actorId", "actor", "scopeAll", false, "managedUserIds", Set.of("report"),
        "limit", 21);
    String listSql = configuration.getMappedStatement(IntakeBatchMapper.class.getName() + ".findBatches")
        .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").toLowerCase();
    String detailSql = configuration.getMappedStatement(
        IntakeBatchMapper.class.getName() + ".findBatchWithProgress")
        .getBoundSql(Map.of("id", "batch-a")).getSql().replaceAll("\\s+", " ").toLowerCase();

    assertThat(listSql).contains("b.default_owner_user_id=?", "b.default_owner_user_id in",
        "parsing_count", "processing_count", "parsed_count", "completed_count", "attention_count", "ignored_count")
        .containsSubsequence("order by b.create_time desc,b.id desc", "limit ?");
    assertThat(detailSql).contains("b.id=?", "parsing_count", "processing_count", "parsed_count",
        "completed_count", "attention_count", "ignored_count");
    assertThat(listSql).contains("i.batch_id=b.id").doesNotContain("select *");
  }

  @Test
  void batchSummaryPartitionsCompletedProcessingAttentionAndIgnoredWithoutNullScoreGaps() {
    var configuration = new MybatisConfiguration();
    configuration.addMapper(IntakeBatchMapper.class);
    String sql = configuration.getMappedStatement(IntakeBatchMapper.class.getName() + ".findBatches")
        .getBoundSql(Map.of("actorId", "actor", "scopeAll", false,
            "managedUserIds", Set.<String>of(), "limit", 21))
        .getSql().replaceAll("\\s+", " ").toLowerCase();

    assertThat(sql).contains(
        "i.status in ('parsing','confirmed')",
        "i.status='completed' and b.jd_version_id is not null and (i.score_status is null or i.score_status not in ('succeeded','failed_retryable','failed_final'))",
        "i.status='completed' and b.jd_version_id is not null and i.score_status='succeeded') as completed_count",
        "i.status='ignored') as ignored_count");
    String parsingQuery = sql.substring(sql.indexOf("(select count(*) from talent_resume_intake i where i.batch_id=b.id"),
        sql.indexOf("as parsing_count"));
    assertThat(parsingQuery).contains("i.status in ('parsing','confirmed')")
        .doesNotContain("score_status", "completed");
    String processingQuery = sql.substring(sql.indexOf("as parsing_count") + "as parsing_count".length(),
        sql.indexOf("as processing_count"));
    assertThat(processingQuery).contains(
        "i.score_status is null or i.score_status not in ('failed_retryable','failed_final')",
        "i.status='completed' and b.jd_version_id is not null");
    assertThat(sql).contains("i.status!='ignored' and (i.status in (")
        .contains("or (i.status='completed' and b.jd_version_id is null))) as attention_count");
  }

  @Test
  void batchAttentionExcludesIgnoredIntakesEvenWhenScoringFailed() {
    var configuration = new MybatisConfiguration();
    configuration.addMapper(IntakeBatchMapper.class);
    String sql = configuration.getMappedStatement(IntakeBatchMapper.class.getName() + ".findBatches")
        .getBoundSql(Map.of("actorId", "actor", "scopeAll", false,
            "managedUserIds", Set.<String>of(), "limit", 21))
        .getSql().replaceAll("\\s+", " ").toLowerCase();
    String attentionQuery = sql.substring(sql.lastIndexOf(
        "(select count(*) from talent_resume_intake i where i.batch_id=b.id"));

    assertThat(attentionQuery).contains(
        "where i.batch_id=b.id and i.status!='ignored' and (i.status in",
        "or i.score_status in ('failed_retryable','failed_final')",
        "or (i.status='completed' and b.jd_version_id is null)))");
  }

  @Test
  void historicalBatchJdLinkIsAtomicAndCannotOverwriteAnExistingAssociation()
      throws NoSuchMethodException {
    Method method = IntakeBatchMapper.class.getDeclaredMethod(
        "linkJdVersion", String.class, String.class, String.class, Instant.class);

    assertThat(sql(method))
        .contains("set jd_version_id=#{jdversionid}", "version=version+1")
        .contains("where id=#{id} and jd_version_id is null");
  }

  @Test
  void fileResultKeepsBatchOpenForRemainingUploads() throws NoSuchMethodException {
    Method method =
        TalentIntakeMapper.class.getDeclaredMethod(
            "incrementBatch",
            String.class,
            int.class,
            int.class,
            String.class,
            Instant.class);
    String sql = sql(method);

    assertThat(sql)
        .contains("status='processing'", "status in ('open','processing')")
        .doesNotContain("completed_with_errors");
  }

  @Test
  void explicitCompletionDerivesTerminalStateFromAccumulatedFailures()
      throws NoSuchMethodException {
    Method method =
        TalentIntakeMapper.class.getDeclaredMethod(
            "completeBatch", String.class, String.class, Instant.class);
    String sql = sql(method);

    assertThat(sql)
        .contains(
            "when failed_count>0 then 'completed_with_errors'",
            "else 'completed'",
            "status in ('open','processing')");
  }

  @Test
  void intakeListKeepsParsingProjectionAndExcludesIgnoredRowsBeforePagination()
      throws NoSuchMethodException {
    Method method =
        TalentIntakeMapper.class.getDeclaredMethod(
            "findIntakes",
            String.class,
            String.class,
            String.class,
            Boolean.class,
            String.class,
            String.class,
            Instant.class,
            Instant.class,
            String.class,
            boolean.class,
            Set.class,
            Set.class,
            Instant.class,
            String.class,
            int.class);
    String sql = selectSql(method);

    assertThat(sql)
        .contains("p.status as processing_status")
        .contains("left join talent_resume_intake_processing p on p.id=i.processing_id")
        .containsSubsequence("i.status != 'ignored'", "#{importstatus}", "limit #{limit}")
        .contains("i.status='completed' and i.score_status='succeeded'", "then 'imported'",
            "i.status in ('parsing','confirmed','completed') then 'processing'",
            "else 'pending'");
  }

  @Test
  void scoreProjectionIncludesCompletedLinkedIntakesWithoutExistingRuns()
      throws NoSuchMethodException {
    assertThat(selectSql(TalentIntakeMapper.class.getDeclaredMethod("findScorableIntakes", int.class)))
        .contains("b.jd_version_id is not null", "i.resume_version_id is not null")
        .doesNotContain("i.score_run_id is not null", "source_channel_id=");
  }

  @Test
  void currentResumeContextsKeepEachReadyJdTripleAndRequireDefaultScoreCompletion()
      throws NoSuchMethodException {
    String sql = selectSql(TalentIntakeMapper.class.getDeclaredMethod(
        "findCurrentCandidateResumes", List.class));

    assertThat(sql).contains(
        "join talent_resume_intake i on i.resume_version_id=v.id",
        "partition by r.candidate_id,v.id,b.jd_version_id",
        "i.status='completed'",
        "i.score_status='succeeded'",
        "i.score_run_id is not null",
        "where position_no=1");
  }

  @Test
  void summaryCurrentVersionIsIndependentOfJobContextsAndScoreReadiness()
      throws NoSuchMethodException {
    String sql = selectSql(TalentIntakeMapper.class.getDeclaredMethod(
        "findCurrentCandidateResumeVersions", List.class));
    assertThat(sql).contains("r.candidate_id", "v.id as resume_version_id",
        "v.resume_id=r.id", "v.version_no=r.current_version_no")
        .doesNotContain("talent_resume_intake", "talent_intake_batch", "jd_version_id", "score_status");
  }

  @Test
  void candidateReadinessGateOnlyAppliesToMyResumePoolQueries() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    configuration.addMapper(TalentIntakeMapper.class);
    var statement = configuration.getMappedStatement(
        TalentIntakeMapper.class.getName() + ".findCandidates");
    Map<String, Object> ordinary = Map.of(
        "actorId", "actor",
        "scopeAll", false,
        "managedUserIds", Set.of(),
        "visibleDepartmentIds", Set.of(),
        "readyOnly", false,
        "limit", 21);
    Map<String, Object> pool = Map.of(
        "actorId", "actor",
        "scopeAll", false,
        "managedUserIds", Set.of(),
        "visibleDepartmentIds", Set.of(),
        "readyOnly", true,
        "limit", 21);

    String ordinarySql = statement.getBoundSql(ordinary).getSql().replaceAll("\\s+", " ")
        .toLowerCase();
    String poolSql = statement.getBoundSql(pool).getSql().replaceAll("\\s+", " ")
        .toLowerCase();

    assertThat(ordinarySql).doesNotContain("join talent_resume_intake");
    assertThat(poolSql).contains(
        "join talent_resume_intake",
        "i.status='completed'",
        "i.score_status='succeeded'",
        "i.score_run_id is not null",
        "b.jd_version_id is not null");
  }

  @Test
  void sqlOperationsContractKeepsCandidateReadinessParameterAlignedWithMapper()
      throws NoSuchMethodException {
    Class<?>[] parameterTypes = {
      String.class,
      String.class,
      boolean.class,
      Set.class,
      Set.class,
      boolean.class,
      Instant.class,
      String.class,
      int.class
    };

    Method sqlOperation =
        TalentIntakeSqlOperations.class.getDeclaredMethod("findCandidates", parameterTypes);
    Method mapperOperation =
        TalentIntakeMapper.class.getDeclaredMethod("findCandidates", parameterTypes);

    assertThat(mapperOperation.getParameterTypes())
        .containsExactly(sqlOperation.getParameterTypes());
  }

  private static String sql(Method method) {
    return String.join(" ", method.getAnnotation(Update.class).value())
        .replaceAll("\\s+", " ")
        .toLowerCase();
  }

  private static String selectSql(Method method) {
    return String.join(" ", method.getAnnotation(Select.class).value())
        .replaceAll("\\s+", " ")
        .toLowerCase();
  }
}
