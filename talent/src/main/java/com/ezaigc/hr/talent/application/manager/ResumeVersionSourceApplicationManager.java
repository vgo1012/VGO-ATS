package com.ezaigc.hr.talent.application.manager;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.request.RequiredIdentifier;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.domain.entity.SourceChannel;
import com.ezaigc.hr.talent.domain.response.ResumeSourceResponse;
import com.ezaigc.hr.talent.domain.response.ResumeVersionSourceResponse;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import com.ezaigc.hr.talent.service.SourceChannelPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 按真实历史版本归属授权后批量读取收件来源。 */
@Component
public class ResumeVersionSourceApplicationManager {
  @Resource private AuthorizationService authorization;
  @Resource private ResumeVersionPersistenceService versions;
  @Resource private ResumeVersionReadAccess readAccess;
  @Resource private TalentIntakePersistenceService intakes;
  @Resource private SourceChannelPersistenceService channels;
  @Resource private TalentIntakeAssembler assembler;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<ResumeVersionSourceResponse> sources(List<String> resumeVersionIds) {
    authorization.currentActor();
    if (resumeVersionIds == null || resumeVersionIds.size() > 100) {
      throw PlatformExceptions.unprocessable(
          "TOO_MANY_RESUME_VERSIONS", "简历版本来源每次最多100项");
    }
    var ids = resumeVersionIds.stream()
        .map(id -> RequiredIdentifier.require(id, "resumeVersionId"))
        .distinct()
        .toList();
    if (ids.isEmpty()) return List.of();

    var authorizedVersions = readAccess.readableVersions(ids, versions.listByIds(ids));
    if (authorizedVersions.isEmpty()) return List.of();

    var intakeIds = authorizedVersions.stream().map(ResumeVersion::getIntakeId)
        .filter(id -> id != null && !id.isBlank()).distinct().toList();
    Map<String, ResumeIntake> intakeRows = intakeIds.isEmpty()
        ? Map.of() : index(intakes.listByIds(intakeIds), ResumeIntake::getId);
    var channelIds = intakeRows.values().stream()
        .map(ResumeIntake::getSourceChannelId).filter(id -> id != null && !id.isBlank())
        .distinct().toList();
    Map<String, SourceChannel> channelRows = channelIds.isEmpty()
        ? Map.of() : index(channels.listByIds(channelIds), SourceChannel::getId);
    return authorizedVersions.stream().map(version -> new ResumeVersionSourceResponse(
        version.getId(), source(version, intakeRows, channelRows))).toList();
  }

  private ResumeSourceResponse source(ResumeVersion version,
      Map<String, ResumeIntake> intakes, Map<String, SourceChannel> channels) {
    var intake = version.getIntakeId() == null ? null : intakes.get(version.getIntakeId());
    if (intake == null) return null;
    var channel = intake.getSourceChannelId() == null ? null : channels.get(intake.getSourceChannelId());
    return assembler.toSourceResponse(intake, channel);
  }

  private static <T> Map<String, T> index(List<T> rows, Function<T, String> key) {
    return rows.stream().collect(Collectors.toMap(key, Function.identity()));
  }
}
