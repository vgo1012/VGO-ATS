package com.ezaigc.hr.talent.application.manager;

import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.Resume;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.enums.CandidateStatus;
import com.ezaigc.hr.talent.enums.ResumeVersionStatus;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.ResumePersistenceService;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 在调用方的只读事务内，按真实归属裁剪历史版本，不替换为当前版本。 */
@Component
public class ResumeVersionReadAccess {
  @Resource private ResumePersistenceService resumes;
  @Resource private CandidatePersistenceService candidates;
  @Resource private CandidateReadAccess access;

  /** ids 由入口完成校验和去重，返回顺序沿用请求顺序。 */
  List<ResumeVersion> readableVersions(List<String> ids, List<ResumeVersion> rows) {
    var versionRows = index(rows.stream()
        .filter(version -> ResumeVersionStatus.AVAILABLE.matches(version.getStatus())).toList(),
        ResumeVersion::getId);
    if (versionRows.isEmpty()) return List.of();
    var resumeRows = index(resumes.listByIds(versionRows.values().stream()
        .map(ResumeVersion::getResumeId).distinct().toList()), Resume::getId);
    if (resumeRows.isEmpty()) return List.of();
    var people = candidates.listByIds(resumeRows.values().stream()
        .map(Resume::getCandidateId).distinct().toList()).stream()
        .filter(candidate -> CandidateStatus.ACTIVE.matches(candidate.getStatus())).toList();
    var activePeople = index(people, Candidate::getId);
    var decision = access.evaluate(people);
    return ids.stream().filter(id -> {
      var version = versionRows.get(id);
      var resume = version == null ? null : resumeRows.get(version.getResumeId());
      return resume != null && activePeople.containsKey(resume.getCandidateId())
          && decision.canReadResume(resume.getCandidateId(), id);
    }).map(versionRows::get).toList();
  }

  private static <T> Map<String, T> index(List<T> rows, Function<T, String> key) {
    return rows.stream().collect(Collectors.toMap(key, Function.identity()));
  }
}
