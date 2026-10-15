package com.ezaigc.hr.talent.application.adapter;

import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_RESUME_FACT_BATCH_SIZE;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.talent.api.CandidateAccessPolicy;
import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.api.ResumeFactQueryService.ResumeFactBatch;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import com.ezaigc.hr.talent.api.enums.ResumeFactErrorCode;
import com.ezaigc.hr.talent.domain.service.FactDateMetadata;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData.CandidateFacts;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData.Education;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData.Project;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData.Skill;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData.Work;
import com.ezaigc.hr.talent.domain.entity.EducationFact;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.entity.ProjectFact;
import com.ezaigc.hr.talent.domain.entity.ResumeResourceEntity;
import com.ezaigc.hr.talent.domain.entity.SimpleFact;
import com.ezaigc.hr.talent.domain.entity.SkillFact;
import com.ezaigc.hr.talent.domain.entity.WorkFact;
import com.ezaigc.hr.talent.domain.dto.SearchFactContactDTO;
import com.ezaigc.hr.talent.service.EducationFactPersistenceService;
import com.ezaigc.hr.talent.service.ProjectFactPersistenceService;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import com.ezaigc.hr.talent.service.SimpleFactPersistenceService;
import com.ezaigc.hr.talent.service.SkillFactPersistenceService;
import com.ezaigc.hr.talent.service.WorkFactPersistenceService;
import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ResumeFactQueryAdapter implements ResumeFactQueryService {

  @Resource private ObjectMapper json;
  @Resource private CandidateAccessPolicy candidateAccess;

  @Resource private ResumeParsingPersistenceService mapper;
  @Resource private EducationFactPersistenceService educationFactPersistenceService;
  @Resource private ProjectFactPersistenceService projectFactPersistenceService;
  @Resource private SimpleFactPersistenceService simpleFactPersistenceService;
  @Resource private SkillFactPersistenceService skillFactPersistenceService;
  @Resource private WorkFactPersistenceService workFactPersistenceService;

  @Override
  public ResumeFactSnapshotData currentFacts(String resumeVersionId) {
    FactSnapshot snapshot = mapper.findCurrentFactSnapshot(resumeVersionId);
    if (snapshot == null)
      throw new PlatformException(
          HttpStatus.CONFLICT, ResumeFactErrorCode.RESUME_FACTS_REQUIRED.getCode(), "简历当前版本尚无有效事实快照", true);
    return map(snapshot, true);
  }

  @Override
  public ResumeFactSnapshotData factsBySnapshotId(String snapshotId) {
    FactSnapshot snapshot = mapper.findFactSnapshot(snapshotId);
    if (snapshot == null)
      throw new PlatformException(
          HttpStatus.NOT_FOUND, "RESUME_FACT_SNAPSHOT_NOT_FOUND", "简历事实快照不存在", false);
    return map(snapshot, false);
  }

  @Override
  public ResumeFactBatch currentFactBatch(Collection<String> resumeVersionIds) {
    List<String> normalizedIds = normalizeIds(resumeVersionIds);
    List<FactSnapshot> snapshots = mapper.findCurrentFactSnapshotDetails(normalizedIds);
    Map<String, String> candidatesByVersion = resumeVersionCandidates(
        mapper.findResumeResources(snapshots.stream().map(FactSnapshot::getResumeVersionId).toList()));
    Set<String> readableIds = candidateAccess.readableResumeVersionIds(candidatesByVersion);
    ResumeFactBatch loaded = batch(
        snapshots,
        FactSnapshot::getResumeVersionId,
        this::currentFacts);
    return id -> {
      if (candidatesByVersion.containsKey(id) && !readableIds.contains(id)) {
        throw PlatformExceptions.forbidden(
            "CANDIDATE_RESUME_ACCESS_FORBIDDEN", "无权查看该简历版本");
      }
      return loaded.get(id);
    };
  }

  @Override
  public ResumeFactBatch snapshotFactBatch(Collection<String> snapshotIds) {
    List<String> normalizedIds = normalizeIds(snapshotIds);
    return batch(
        normalizedIds.isEmpty() ? List.of() : mapper.listByIds(normalizedIds),
        FactSnapshot::getId,
        this::factsBySnapshotId);
  }

  @Override
  public Map<String, CurrentFactContact> currentFactContacts(
      Collection<String> resumeVersionIds) {
    if (resumeVersionIds == null || resumeVersionIds.isEmpty()) {
      return Map.of();
    }
    Set<String> readableIds = readableResumeVersionIds(
        mapper.findResumeResources(normalizeIds(resumeVersionIds)));
    if (readableIds.isEmpty()) return Map.of();
    return mapper.findLatestAvailableFactContacts(readableIds).stream()
        .collect(
            Collectors.toUnmodifiableMap(
                SearchFactContactDTO::getResumeVersionId,
                snapshot ->
                    new CurrentFactContact(
                        snapshot.getId(), snapshot.getEmail(), snapshot.getPhone())));
  }

  private ResumeFactSnapshotData map(FactSnapshot snapshot, boolean authorize) {
    String resumeVersionId = snapshot.getResumeVersionId();
    ResumeResourceEntity resource = mapper.findResumeResource(resumeVersionId);
    if (resource == null)
      throw new PlatformException(
          HttpStatus.NOT_FOUND, "RESUME_VERSION_NOT_FOUND", "简历版本不存在", false);
    if (authorize) candidateAccess.requireResumeReadable(resource.getCandidateId(), resumeVersionId);
    return assemble(
        snapshot,
        resource,
        workFactPersistenceService.findWork(snapshot.getId()),
        projectFactPersistenceService.findProjects(snapshot.getId()),
        educationFactPersistenceService.findEducation(snapshot.getId()),
        skillFactPersistenceService.findSkills(snapshot.getId()),
        simpleFactPersistenceService.findSimple(snapshot.getId()));
  }

  private Set<String> readableResumeVersionIds(Collection<ResumeResourceEntity> resources) {
    return candidateAccess.readableResumeVersionIds(resumeVersionCandidates(resources));
  }

  private Map<String, String> resumeVersionCandidates(Collection<ResumeResourceEntity> resources) {
    return resources.stream().collect(
        Collectors.toUnmodifiableMap(
            ResumeResourceEntity::getResumeVersionId,
            ResumeResourceEntity::getCandidateId));
  }

  private ResumeFactBatch batch(
      List<FactSnapshot> loadedSnapshots,
      Function<FactSnapshot, String> resultKey,
      Function<String, ResumeFactSnapshotData> fallback) {
    if (loadedSnapshots.isEmpty()) {
      return fallback::apply;
    }
    List<FactSnapshot> snapshotList = List.copyOf(loadedSnapshots);
    BatchIndex index = preloadChildren(snapshotList);
    Map<String, FactSnapshot> snapshotsByResultKey =
        snapshotList.stream()
            .collect(Collectors.toUnmodifiableMap(resultKey, Function.identity()));
    return id -> {
      FactSnapshot snapshot = snapshotsByResultKey.get(id);
      if (snapshot == null) {
        return fallback.apply(id);
      }
      try {
        return map(snapshot, index);
      } catch (PlatformException batchFailure) {
        try {
          return fallback.apply(id);
        } catch (RuntimeException fallbackFailure) {
          fallbackFailure.addSuppressed(batchFailure);
          throw fallbackFailure;
        }
      }
    };
  }

  private BatchIndex preloadChildren(List<FactSnapshot> snapshots) {
    List<String> snapshotIds = snapshots.stream().map(FactSnapshot::getId).toList();
    List<String> resumeVersionIds =
        snapshots.stream().map(FactSnapshot::getResumeVersionId).distinct().toList();
    return BatchIndex.from(
        mapper.findResumeResources(resumeVersionIds),
        workFactPersistenceService.list(
            Wrappers.lambdaQuery(WorkFact.class)
                .in(WorkFact::getFactSnapshotId, snapshotIds)
                .orderByAsc(WorkFact::getFactSnapshotId)
                .orderByDesc(WorkFact::getStartDate)
                .orderByAsc(WorkFact::getItemKey)),
        projectFactPersistenceService.list(
            Wrappers.lambdaQuery(ProjectFact.class)
                .in(ProjectFact::getFactSnapshotId, snapshotIds)
                .orderByAsc(ProjectFact::getFactSnapshotId)
                .orderByDesc(ProjectFact::getStartDate)
                .orderByAsc(ProjectFact::getItemKey)),
        educationFactPersistenceService.list(
            Wrappers.lambdaQuery(EducationFact.class)
                .in(EducationFact::getFactSnapshotId, snapshotIds)
                .orderByAsc(EducationFact::getFactSnapshotId)
                .orderByDesc(EducationFact::getStartDate)
                .orderByAsc(EducationFact::getItemKey)),
        skillFactPersistenceService.list(
            Wrappers.lambdaQuery(SkillFact.class)
                .in(SkillFact::getFactSnapshotId, snapshotIds)
                .orderByAsc(SkillFact::getFactSnapshotId)
                .orderByAsc(SkillFact::getSkillName, SkillFact::getItemKey)),
        simpleFactPersistenceService.list(
            Wrappers.lambdaQuery(SimpleFact.class)
                .in(SimpleFact::getFactSnapshotId, snapshotIds)
                .orderByAsc(
                    SimpleFact::getFactSnapshotId,
                    SimpleFact::getFactType,
                    SimpleFact::getItemKey)));
  }

  private static List<String> normalizeIds(Collection<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    if (ids.size() > MAX_RESUME_FACT_BATCH_SIZE) {
      throw new IllegalArgumentException(
          "简历事实批量查询不能超过 " + MAX_RESUME_FACT_BATCH_SIZE + " 项");
    }
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String id : ids) {
      if (id != null && !id.isBlank()) {
        normalized.add(id);
      }
    }
    return List.copyOf(normalized);
  }

  private ResumeFactSnapshotData map(FactSnapshot snapshot, BatchIndex index) {
    ResumeResourceEntity resource = index.resources().get(snapshot.getResumeVersionId());
    if (resource == null) {
      throw new PlatformException(
          HttpStatus.NOT_FOUND, "RESUME_VERSION_NOT_FOUND", "简历版本不存在", false);
    }
    String snapshotId = snapshot.getId();
    return assemble(
        snapshot,
        resource,
        index.workFacts().getOrDefault(snapshotId, List.of()),
        index.projectFacts().getOrDefault(snapshotId, List.of()),
        index.educationFacts().getOrDefault(snapshotId, List.of()),
        index.skillFacts().getOrDefault(snapshotId, List.of()),
        index.simpleFacts().getOrDefault(snapshotId, List.of()));
  }

  private ResumeFactSnapshotData assemble(
      FactSnapshot snapshot,
      ResumeResourceEntity resource,
      List<WorkFact> workFacts,
      List<ProjectFact> projectFacts,
      List<EducationFact> educationFacts,
      List<SkillFact> skillFacts,
      List<SimpleFact> simpleFacts) {
    String resumeVersionId = snapshot.getResumeVersionId();
    return new ResumeFactSnapshotData(
        snapshot.getId(),
        resumeVersionId,
        resource.getCandidateId(),
        snapshot.getSnapshotVersion(),
        new CandidateFacts(
            snapshot.getFullName(),
            snapshot.getEmail(),
            snapshot.getPhone(),
            snapshot.getCurrentRole(),
            snapshot.getCity(),
            snapshot.getAge(),
            snapshot.getGender(),
            snapshot.getJobIntention()),
        workFacts.stream()
            .map(
                v ->
                    new Work(
                        v.getItemKey(),
                        v.getEmployer(),
                        v.getRoleName(),
                        v.getStartDate(),
                        v.getEndDate(),
                        v.getDescriptionText(),
                        strings(v.getResponsibilitiesJson()),
                        strings(v.getAchievementsJson()),
                        FactDateMetadata.read(v.getExt(), "startDateText", v.getStartDate()),
                        FactDateMetadata.read(v.getExt(), "endDateText", v.getEndDate())))
            .toList(),
        projectFacts.stream()
            .map(
                v ->
                    new Project(
                        v.getItemKey(),
                        v.getProjectName(),
                        v.getRoleName(),
                        v.getStartDate(),
                        v.getEndDate(),
                        v.getDescriptionText(),
                        strings(v.getTechnologiesJson()),
                        strings(v.getAchievementsJson())))
            .toList(),
        educationFacts.stream()
            .map(
                v ->
                    new Education(
                        v.getItemKey(),
                        v.getSchool(),
                        v.getDegreeName(),
                        v.getMajor(),
                        v.getStartDate(),
                        v.getEndDate(),
                        v.getLearningMode()))
            .toList(),
        skillFacts.stream()
            .map(v -> new Skill(v.getItemKey(), v.getSkillName(), v.getLevelName(), v.getYearsValue()))
            .toList(),
        simpleFacts.stream()
            .map(v -> new ResumeFactSnapshotData.SimpleFact(
                        v.getFactType(),
                        v.getItemKey(),
                        v.getNameValue(),
                        v.getLevelValue(),
                        v.getIssuerValue(),
                        v.getStartDate(),
                        v.getEndDate()))
            .toList());
  }

  @Getter(AccessLevel.PRIVATE)
  @Accessors(fluent = true)
  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  private static final class BatchIndex {
    private final Map<String, ResumeResourceEntity> resources;
    private final Map<String, List<WorkFact>> workFacts;
    private final Map<String, List<ProjectFact>> projectFacts;
    private final Map<String, List<EducationFact>> educationFacts;
    private final Map<String, List<SkillFact>> skillFacts;
    private final Map<String, List<SimpleFact>> simpleFacts;

    private static BatchIndex from(
        List<ResumeResourceEntity> resources,
        List<WorkFact> workFacts,
        List<ProjectFact> projectFacts,
        List<EducationFact> educationFacts,
        List<SkillFact> skillFacts,
        List<SimpleFact> simpleFacts) {
      return new BatchIndex(
          resources.stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      ResumeResourceEntity::getResumeVersionId, Function.identity())),
          group(workFacts, WorkFact::getFactSnapshotId),
          group(projectFacts, ProjectFact::getFactSnapshotId),
          group(educationFacts, EducationFact::getFactSnapshotId),
          group(skillFacts, SkillFact::getFactSnapshotId),
          group(simpleFacts, SimpleFact::getFactSnapshotId));
    }

    private static <T> Map<String, List<T>> group(
        List<T> values, Function<T, String> keyExtractor) {
      return values.stream()
          .collect(
              Collectors.groupingBy(
                  keyExtractor,
                  LinkedHashMap::new,
                  Collectors.collectingAndThen(Collectors.toList(), List::copyOf)));
    }
  }

  private List<String> strings(String value) {
    try {
      return json.readValue(value, new TypeReference<List<String>>() {});
    } catch (Exception ex) {
      throw new PlatformException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "RESUME_FACT_SNAPSHOT_INVALID",
          "已持久化的简历事实快照无法读取",
          false,
          ex);
    }
  }
}
