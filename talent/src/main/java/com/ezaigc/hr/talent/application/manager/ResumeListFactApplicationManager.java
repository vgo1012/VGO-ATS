package com.ezaigc.hr.talent.application.manager;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ezaigc.hr.talent.application.assembler.ResumeParsingAssembler;
import com.ezaigc.hr.talent.domain.entity.EducationFact;
import com.ezaigc.hr.talent.domain.entity.FactBundle;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.entity.ProjectFact;
import com.ezaigc.hr.talent.domain.entity.SimpleFact;
import com.ezaigc.hr.talent.domain.entity.SkillFact;
import com.ezaigc.hr.talent.domain.entity.WorkFact;
import com.ezaigc.hr.talent.domain.response.FactBundleResponse;
import com.ezaigc.hr.talent.enums.ResumeFactType;
import com.ezaigc.hr.talent.service.EducationFactPersistenceService;
import com.ezaigc.hr.talent.service.ProjectFactPersistenceService;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import com.ezaigc.hr.talent.service.SimpleFactPersistenceService;
import com.ezaigc.hr.talent.service.SkillFactPersistenceService;
import com.ezaigc.hr.talent.service.WorkFactPersistenceService;
import com.ezaigc.hr.talent.service.support.FactOrderMetadata;
import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 只接收已授权的版本，批量加载列表需要的结构化事实。 */
@Component
public class ResumeListFactApplicationManager {
  @Resource private ResumeParsingPersistenceService snapshots;
  @Resource private WorkFactPersistenceService work;
  @Resource private ProjectFactPersistenceService projects;
  @Resource private EducationFactPersistenceService education;
  @Resource private SkillFactPersistenceService skills;
  @Resource private SimpleFactPersistenceService simple;
  @Resource private ResumeParsingAssembler assembler;

  Map<String, FactBundleResponse> summaries(Collection<String> authorizedVersionIds) {
    if (authorizedVersionIds.isEmpty()) return Map.of();
    var rows = snapshots.list(Wrappers.lambdaQuery(FactSnapshot.class)
        .select(FactSnapshot::getId, FactSnapshot::getResumeVersionId, FactSnapshot::getSnapshotVersion,
            FactSnapshot::getSourceType, FactSnapshot::getCurrentRole, FactSnapshot::getCity,
            FactSnapshot::getJobIntention, FactSnapshot::getGender, FactSnapshot::getAge, FactSnapshot::getCreateTime)
        .in(FactSnapshot::getResumeVersionId, authorizedVersionIds).eq(FactSnapshot::getCurrentMarker, "Y"));
    if (rows.isEmpty()) return Map.of();
    var ids = rows.stream().map(FactSnapshot::getId).toList();
    var workRows = group(work.list(Wrappers.lambdaQuery(WorkFact.class)
        .select(WorkFact::getId, WorkFact::getFactSnapshotId, WorkFact::getItemKey, WorkFact::getEmployer,
            WorkFact::getStartDate, WorkFact::getEndDate, WorkFact::getExt)
        .in(WorkFact::getFactSnapshotId, ids).orderByDesc(WorkFact::getStartDate).orderByAsc(WorkFact::getItemKey)), WorkFact::getFactSnapshotId);
    var projectRows = group(projects.list(Wrappers.lambdaQuery(ProjectFact.class)
        .select(ProjectFact::getId, ProjectFact::getFactSnapshotId, ProjectFact::getItemKey,
            ProjectFact::getStartDate, ProjectFact::getEndDate, ProjectFact::getExt)
        .in(ProjectFact::getFactSnapshotId, ids).orderByDesc(ProjectFact::getStartDate).orderByAsc(ProjectFact::getItemKey)), ProjectFact::getFactSnapshotId);
    var educationRows = group(education.list(Wrappers.lambdaQuery(EducationFact.class)
        .select(EducationFact::getId, EducationFact::getFactSnapshotId, EducationFact::getItemKey,
            EducationFact::getDegreeName, EducationFact::getStartDate, EducationFact::getEndDate, EducationFact::getExt)
        .in(EducationFact::getFactSnapshotId, ids).orderByDesc(EducationFact::getStartDate).orderByAsc(EducationFact::getItemKey)), EducationFact::getFactSnapshotId);
    var skillRows = group(skills.list(Wrappers.lambdaQuery(SkillFact.class)
        .select(SkillFact::getId, SkillFact::getFactSnapshotId, SkillFact::getItemKey, SkillFact::getSkillName, SkillFact::getExt)
        .in(SkillFact::getFactSnapshotId, ids).orderByAsc(SkillFact::getSkillName, SkillFact::getItemKey)), SkillFact::getFactSnapshotId);
    var simpleRows = group(simple.list(Wrappers.lambdaQuery(SimpleFact.class)
        .select(SimpleFact::getId, SimpleFact::getFactSnapshotId, SimpleFact::getItemKey,
            SimpleFact::getFactType, SimpleFact::getNameValue, SimpleFact::getExt)
        .in(SimpleFact::getFactSnapshotId, ids).eq(SimpleFact::getFactType, ResumeFactType.UNKNOWN.getCode())
        .orderByAsc(SimpleFact::getFactType, SimpleFact::getItemKey)), SimpleFact::getFactSnapshotId);
    Map<String, FactBundleResponse> result = new HashMap<>();
    for (var row : rows) {
      String id = row.getId();
      result.put(row.getResumeVersionId(), assembler.toResponse(new FactBundle(row,
          FactOrderMetadata.restore(workRows.getOrDefault(id, List.of())),
          FactOrderMetadata.restore(projectRows.getOrDefault(id, List.of())),
          FactOrderMetadata.restore(educationRows.getOrDefault(id, List.of())),
          FactOrderMetadata.restore(skillRows.getOrDefault(id, List.of())),
          FactOrderMetadata.restore(simpleRows.getOrDefault(id, List.of())))));
    }
    return result;
  }

  private static <T> Map<String, List<T>> group(List<T> rows, Function<T, String> key) {
    return rows.stream().collect(Collectors.groupingBy(key));
  }
}
