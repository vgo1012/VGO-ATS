package com.ezaigc.hr.talent.application.manager;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.request.RequiredIdentifier;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.CandidateResumeRow;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.domain.entity.SourceChannel;
import com.ezaigc.hr.talent.domain.response.CandidateResumeSummaryResponse;
import com.ezaigc.hr.talent.enums.ResumeSummaryFactsStatus;
import com.ezaigc.hr.talent.domain.response.ResumeSourceResponse;
import com.ezaigc.hr.talent.enums.CandidateStatus;
import com.ezaigc.hr.talent.enums.ResumeVersionStatus;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import com.ezaigc.hr.talent.service.SourceChannelPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 本页候选人的批量摘要；基础档案共享，结构化事实继承详情的对象授权。 */
@Component
public class CandidateResumeSummaryApplicationManager {
  @Resource private AuthorizationService authorization;
  @Resource private CandidatePersistenceService candidates;
  @Resource private TalentIntakePersistenceService intakes;
  @Resource private ResumeVersionPersistenceService versions;
  @Resource private SourceChannelPersistenceService channels;
  @Resource private ResumeListFactApplicationManager facts;
  @Resource private TalentIntakeAssembler assembler;
  @Resource private CandidateReadAccess candidateReadAccess;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<CandidateResumeSummaryResponse> summaries(Collection<String> candidateIds) {
    authorization.currentActor();
    if (candidateIds == null || candidateIds.size() > 100) {
      throw PlatformExceptions.unprocessable("TOO_MANY_CANDIDATES", "候选人摘要每次最多100项");
    }
    var ids = candidateIds.stream().map(id -> RequiredIdentifier.require(id, "candidateId")).distinct().toList();
    if (ids.isEmpty()) return List.of();
    var loadedPeople = index(candidates.listByIds(ids).stream()
        .filter(candidate -> CandidateStatus.ACTIVE.matches(candidate.getStatus())).toList(), Candidate::getId);
    var decision = candidateReadAccess.evaluate(loadedPeople.values());
    var people = loadedPeople.entrySet().stream()
        .filter(entry -> decision.canReadCandidate(entry.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    if (people.isEmpty()) return List.of();
    var current = index(intakes.findCurrentCandidateResumeVersions(List.copyOf(people.keySet())), CandidateResumeRow::getCandidateId);
    var versionIds = current.values().stream()
        .filter(row -> decision.canReadResume(row.getCandidateId(), row.getResumeVersionId()))
        .map(CandidateResumeRow::getResumeVersionId).distinct().toList();
    var resumeVersions = versionIds.isEmpty() ? Map.<String, ResumeVersion>of()
        : index(versions.listByIds(versionIds), ResumeVersion::getId);
    var intakeIds = resumeVersions.values().stream().map(ResumeVersion::getIntakeId).distinct().toList();
    var sources = intakeIds.isEmpty() ? Map.<String, ResumeIntake>of()
        : index(intakes.listByIds(intakeIds), ResumeIntake::getId);
    var channelIds = sources.values().stream().map(ResumeIntake::getSourceChannelId).distinct().toList();
    var sourceChannels = channelIds.isEmpty() ? Map.<String, SourceChannel>of()
        : index(channels.listByIds(channelIds), SourceChannel::getId);
    var readableVersions = resumeVersions.values().stream()
        .filter(version -> version != null && ResumeVersionStatus.AVAILABLE.matches(version.getStatus()))
        .map(ResumeVersion::getId).toList();
    var bundles = facts.summaries(readableVersions);
    return ids.stream().filter(people::containsKey).map(id -> {
      var link = current.get(id);
      var version = link == null ? null : resumeVersions.get(link.getResumeVersionId());
      var bundle = version == null ? null : bundles.get(version.getId());
      ResumeSummaryFactsStatus status = link == null ? ResumeSummaryFactsStatus.NO_RESUME
          : version == null ? ResumeSummaryFactsStatus.FORBIDDEN
          : bundle == null ? ResumeSummaryFactsStatus.NOT_READY : ResumeSummaryFactsStatus.READY;
      return new CandidateResumeSummaryResponse(id, version == null ? null : assembler.toResponse(version),
          version == null ? null : source(sources.get(version.getIntakeId()), sourceChannels), bundle, status);
    }).toList();
  }

  private ResumeSourceResponse source(ResumeIntake intake, Map<String, SourceChannel> channels) {
    if (intake == null) return null;
    var channel = channels.get(intake.getSourceChannelId());
    return assembler.toSourceResponse(intake, channel);
  }

  private static <T> Map<String, T> index(List<T> rows, Function<T, String> key) {
    return rows.stream().collect(Collectors.toMap(key, Function.identity()));
  }
}
