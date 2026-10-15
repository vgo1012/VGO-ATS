package com.ezaigc.hr.talent.application.manager;

import com.ezaigc.hr.talent.api.CandidateMatchingStateQuery;
import com.ezaigc.hr.talent.api.CandidateRecruitmentStateQuery;
import com.ezaigc.hr.talent.api.JobContextQuery;
import com.ezaigc.hr.talent.api.ScoreRunCompletionQuery;
import com.ezaigc.hr.talent.domain.entity.CandidateResumeRow;
import com.ezaigc.hr.talent.domain.entity.ResumeJdScoreBinding;
import com.ezaigc.hr.talent.domain.response.CandidateJobContextResponse;
import com.ezaigc.hr.talent.enums.CandidateJobContextStatus;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.ResumeJdScoreBindingPersistenceService;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.time.Instant;
import java.util.stream.Collectors;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class CandidateJobContextApplicationManager {
  private static final ObjectMapper JSON = new ObjectMapper();
  @Resource private CandidatePersistenceService candidates;
  @Resource private SharedReclaimScoreApplicationManager reclaimedScores;
  @Resource private JobContextQuery jobs;
  @Resource private TalentIntakePersistenceService intakes;
  @Resource private CandidateMatchingStateQuery matching;
  @Resource private ResumeJdScoreBindingPersistenceService scoreBindings;
  @Resource private ScoreRunCompletionQuery scoreRuns;

  /** 删除完整性检查只返回关联是否存在，不以查看权限裁剪业务事实。 */
  public boolean hasCurrentPersonalPoolAssociation(List<String> candidateIds, Collection<String> jdVersionIds) {
    if (candidateIds.isEmpty() || jdVersionIds.isEmpty()) return false;
    var candidateRows = candidates.listByIds(candidateIds);
    var currentResumes = intakes.findCurrentCandidateResumes(candidateIds);
    var bindings = scoreBindings.findForCandidates(candidateIds);
    var successfulRuns = successfulRunIds(bindings.stream()
        .map(ResumeJdScoreBinding::getDefaultMatchRunId).toList());
    for (var candidate : candidateRows) {
      Set<String> currentVersions = currentResumes.stream()
          .filter(resume -> candidate.getId().equals(resume.getCandidateId()))
          .map(CandidateResumeRow::getResumeVersionId).collect(Collectors.toSet());
      var ext = JSON.readTree(candidate.getExt() == null ? "{}" : candidate.getExt());
      boolean reclaimed = currentVersions.contains(ext.path("reclaimedResumeVersionId").asString());
      String claimedBinding = reclaimed ? reclaimedScores.claimedBindingId(candidate, bindings) : null;
      if (!reclaimed && currentResumes.stream().anyMatch(resume ->
          candidate.getId().equals(resume.getCandidateId()) && jdVersionIds.contains(resume.getJdVersionId()))) {
        return true;
      }
      if (bindings.stream().anyMatch(binding -> candidate.getId().equals(binding.getCandidateId())
          && currentVersions.contains(binding.getResumeVersionId())
          && jdVersionIds.contains(binding.getJdVersionId())
          && successfulRuns.contains(binding.getDefaultMatchRunId())
          && (!reclaimed || Objects.equals(claimedBinding, binding.getId())))) return true;
    }
    return false;
  }

  public Map<String, List<CandidateJobContextResponse>> contexts(
      List<String> candidateIds,
      Map<String, List<CandidateRecruitmentStateQuery.State>> applications) {
    return contexts(candidateIds, applications, Set.of());
  }

  public Map<String, List<CandidateJobContextResponse>> contexts(
      List<String> candidateIds,
      Map<String, List<CandidateRecruitmentStateQuery.State>> applications,
      Set<String> sharedCandidateIds) {
    if (candidateIds.isEmpty()) return Map.of();
    var candidateById = candidates.listByIds(candidateIds).stream()
        .collect(Collectors.toMap(Candidate::getId, row -> row));
    var resumes = intakes.findCurrentCandidateResumes(candidateIds);
    var sharedIds = candidateIds.stream().filter(sharedCandidateIds::contains).distinct().toList();
    var allResumeContexts = new ArrayList<>(resumes);
    if (!sharedIds.isEmpty()) allResumeContexts.addAll(intakes.findCandidateResumeHistory(sharedIds));
    Map<String, List<CandidateResumeRow>> resumesByCandidate = new LinkedHashMap<>();
    Map<String, Set<String>> currentVersionsByCandidate = new LinkedHashMap<>();
    for (var resume : resumes) {
      currentVersionsByCandidate.computeIfAbsent(resume.getCandidateId(), ignored -> new HashSet<>())
          .add(resume.getResumeVersionId());
    }
    for (var resume : allResumeContexts) {
      resumesByCandidate.computeIfAbsent(resume.getCandidateId(), ignored -> new ArrayList<>()).add(resume);
    }
    var reboundScores = scoreBindings.findForCandidates(candidateIds);
    var successfulReboundRuns = successfulRunIds(reboundScores.stream()
        .map(row -> row.getDefaultMatchRunId()).toList());
    var screened = matching.completedStates(candidateIds);
    var ids = new ArrayList<String>();
    allResumeContexts.forEach(row -> ids.add(row.getJdVersionId()));
    reboundScores.forEach(row -> ids.add(row.getJdVersionId()));
    screened.forEach(row -> ids.add(row.getJdVersionId()));
    applications.values().forEach(values -> values.forEach(row -> ids.add(row.getJdVersionId())));
    var sharedJds = new HashSet<String>();
    allResumeContexts.stream().filter(row -> sharedCandidateIds.contains(row.getCandidateId()))
        .forEach(row -> sharedJds.add(row.getJdVersionId()));
    Map<String, List<ResumeJdScoreBinding>> reboundScoresByCandidate = new LinkedHashMap<>();
    for (var row : reboundScores) {
      var candidateId = row.getCandidateId();
      reboundScoresByCandidate.computeIfAbsent(candidateId, ignored -> new ArrayList<>()).add(row);
      if (sharedCandidateIds.contains(candidateId)) {
        sharedJds.add(row.getJdVersionId());
      }
    }
    Map<String, List<CandidateMatchingStateQuery.State>> screenedByCandidate = new LinkedHashMap<>();
    for (var row : screened) {
      var candidateId = row.getCandidateId();
      screenedByCandidate.computeIfAbsent(candidateId, ignored -> new ArrayList<>()).add(row);
      if (sharedCandidateIds.contains(candidateId)) {
        sharedJds.add(row.getJdVersionId());
      }
    }
    sharedIds.forEach(id -> applications.getOrDefault(id, List.of())
        .forEach(row -> sharedJds.add(row.getJdVersionId())));
    sharedJds.remove(null);
    var versionIds = ids.stream().filter(Objects::nonNull).distinct().toList();
    var privateVisible = visibleJobVersions(versionIds, false);
    var sharedVisible = sharedJds.isEmpty() ? privateVisible : visibleJobVersions(sharedJds, true);
    Map<String, List<CandidateJobContextResponse>> result = new LinkedHashMap<>();
    for (var candidateId : candidateIds) {
      boolean shared = sharedCandidateIds.contains(candidateId);
      var visible = shared ? sharedVisible : privateVisible;
      Map<String, CandidateJobContextResponse> states = new LinkedHashMap<>();
      var currentVersions = currentVersionsByCandidate.getOrDefault(candidateId, Set.of());
      var candidate = candidateById.get(candidateId);
      var ext = JSON.readTree(candidate == null || candidate.getExt() == null ? "{}" : candidate.getExt());
      String reclaimedResume = ext.path("reclaimedResumeVersionId").asString();
      boolean reclaimed = !shared && currentVersions.contains(reclaimedResume);
      String reclaimedBinding = reclaimed ? reclaimedScores.claimedBindingId(candidate, reboundScores) : null;
      String reclaimedTime = ext.path("reclaimedAt").asString();
      Instant reclaimedAt = reclaimedTime == null || reclaimedTime.isBlank() ? null : Instant.parse(reclaimedTime);
      if (!shared && currentVersions.isEmpty()) {
        result.put(candidateId, List.of());
        continue;
      }
      Set<String> reboundKeys = new HashSet<>();
      for (var resume : resumesByCandidate.getOrDefault(candidateId, List.of())) {
        if (reclaimed) continue;
        add(
            states,
            visible.get(resume.getJdVersionId()),
            resume.getResumeVersionId(),
            CandidateJobContextStatus.PENDING,
            null,
            null,
            resume.getDefaultMatchRunId());
      }
      reboundScoresByCandidate.getOrDefault(candidateId, List.of()).stream()
          .filter(row -> shared || currentVersions.contains(row.getResumeVersionId()))
          .filter(row -> !reclaimed || Objects.equals(reclaimedBinding, row.getId()))
          .filter(row -> successfulReboundRuns.contains(row.getDefaultMatchRunId()))
          .filter(row -> reboundKeys.add(row.getJdVersionId() + ":" + row.getResumeVersionId()))
          .forEach(row -> add(
              states,
              visible.get(row.getJdVersionId()),
              row.getResumeVersionId(),
              CandidateJobContextStatus.PENDING,
              null,
              null,
              row.getDefaultMatchRunId()));
      screenedByCandidate.getOrDefault(candidateId, List.of()).stream()
          .filter(row -> shared || currentVersions.contains(row.getResumeVersionId()))
          .filter(row -> !reclaimed || states.values().stream().anyMatch(context ->
              context.getJdVersionId().equals(row.getJdVersionId())
                  && Objects.equals(context.getMatchRunId(), row.getMatchRunId())))
          .forEach(
              row ->
                  add(
                      states,
                      visible.get(row.getJdVersionId()),
                      row.getResumeVersionId(),
                      CandidateJobContextStatus.AI_SCREENED,
                      null,
                      null,
                      row.getMatchRunId()));
      applications
          .getOrDefault(candidateId, List.of())
          .stream()
          .filter(row -> !reclaimed || row.isBlocksLibrary() || reclaimedAt == null
              || (row.getTerminalAt() != null && !row.getTerminalAt().isBefore(reclaimedAt)))
          .forEach(
              row ->
                  add(
                      states,
                      visible.get(row.getJdVersionId()),
                      row.getResumeVersionId(),
                      row.isBlocksLibrary()
                          ? CandidateJobContextStatus.ADVANCED
                          : CandidateJobContextStatus.FINISHED,
                      row.getStage(),
                      row.getApplicationId(),
                      null));
      result.put(candidateId, List.copyOf(states.values()));
    }
    return result;
  }

  private Map<String, JobContextQuery.Context> visibleJobVersions(Collection<String> versionIds, boolean shared) {
    if (versionIds.size() <= JobContextQuery.MAX_VERSIONS_PER_BATCH) {
      return shared ? jobs.visibleVersions(versionIds, versionIds) : jobs.visibleVersions(versionIds);
    }
    var ids = List.copyOf(versionIds);
    Map<String, JobContextQuery.Context> visible = new LinkedHashMap<>();
    for (int start = 0; start < ids.size(); start += JobContextQuery.MAX_VERSIONS_PER_BATCH) {
      var batch = ids.subList(start, Math.min(start + JobContextQuery.MAX_VERSIONS_PER_BATCH, ids.size()));
      // 共享可见性只使用本批已回查的共享关联版本，私有查询不传可信集合。
      visible.putAll(shared ? jobs.visibleVersions(batch, Set.copyOf(batch)) : jobs.visibleVersions(batch));
    }
    return visible;
  }

  private Set<String> successfulRunIds(List<String> runIds) {
    Set<String> successful = new HashSet<>();
    for (int start = 0; start < runIds.size(); start += ScoreRunCompletionQuery.MAX_RUNS_PER_BATCH) {
      int end = Math.min(start + ScoreRunCompletionQuery.MAX_RUNS_PER_BATCH, runIds.size());
      successful.addAll(scoreRuns.successfulRunIds(runIds.subList(start, end)));
    }
    return successful;
  }

  private static void add(
      Map<String, CandidateJobContextResponse> states,
      JobContextQuery.Context job,
      String resumeVersionId,
      CandidateJobContextStatus status,
      String stage,
      String applicationId,
      String matchRunId) {
    if (job == null) return;
    String key = job.getJdVersionId() + ":" + resumeVersionId;
    var previous = states.get(key);
    if (previous != null
        && CandidateJobContextStatus.ADVANCED.getCode().equals(previous.getStatus())
        && status == CandidateJobContextStatus.FINISHED) return;
    states.put(
        key,
        new CandidateJobContextResponse(
            job.getJdId(),
            job.getJdVersionId(),
            job.getTitle(),
            job.getJdCode(),
            job.getVersionNo(),
            resumeVersionId,
            status.getCode(),
            stage,
            applicationId,
            matchRunId));
  }
}
