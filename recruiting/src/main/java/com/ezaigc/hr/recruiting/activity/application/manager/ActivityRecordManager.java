package com.ezaigc.hr.recruiting.activity.application.manager;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.common.web.pagination.StableCursor;
import com.ezaigc.hr.common.web.pagination.VisibleCursorPageCollector;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.recruiting.activity.domain.dto.ActivityRecordQuery;
import com.ezaigc.hr.recruiting.activity.domain.dto.ActivityRecordRowDTO;
import com.ezaigc.hr.recruiting.activity.domain.response.ActivityFactResponse;
import com.ezaigc.hr.recruiting.activity.domain.response.ActivityRecordResponse;
import com.ezaigc.hr.recruiting.activity.enums.ActivityAction;
import com.ezaigc.hr.recruiting.activity.enums.ActivitySource;
import com.ezaigc.hr.recruiting.activity.service.ActivityOriginPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ParticipantPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.application.manager.RecruitmentApplicationManager;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ActivityRecordManager {
  @Resource private ActivityOriginPersistenceService origins;
  @Resource private RecruitmentApplicationManager applications;
  @Resource private InterviewPersistenceService interviews;
  @Resource private ParticipantPersistenceService participants;
  @Resource private AuthorizationService authorization;
  @Resource private CandidateLibraryQueryService candidateLibrary;
  @Resource private CursorCodec cursorCodec;
  @Resource private ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public CursorPage<ActivityRecordResponse> records(String applicationId, String type, String cursor, Integer requestedLimit) {
    var app = applications.requireFollowUpApplication(applicationId);
    if (type != null) {
      try { ActivityAction.valueOf(type); }
      catch (IllegalArgumentException exception) {
        throw PlatformExceptions.unprocessable("ACTIVITY_TYPE_INVALID", "活动记录类型无效");
      }
    }
    var actor = authorization.currentActor();
    String scope = CursorScopes.scope("activity-records", applicationId, type, actor.getUserId(), actor.getPermissions(),
        actor.hasCompanyWideScope(), actor.getOperationalManagedUserIds(), actor.visibleDepartmentIds());
    Map<String, List<ActivityFactResponse>> parsed = new HashMap<>();
    var page = VisibleCursorPageCollector.collect(decode(cursor, scope), PageLimits.resolve(requestedLimit),
        100, cursorCodec, scope,
        scan -> origins.findRecords(new ActivityRecordQuery(applicationId, app.getCandidateId(), type,
            scan == null ? null : scan.getSortTime(), scan == null ? null : scan.getId(), 100)),
        rows -> visible(rows, parsed, candidateLibrary.eligibleCandidateIds(List.of(app.getCandidateId())).contains(app.getCandidateId())), ActivityRecordRowDTO::getOccurredAt, ActivityRecordRowDTO::getId);
    return page.map(row -> {
      var facts = parsed.get(row.getId());
      String comment = comment(facts);
      return new ActivityRecordResponse(row.getId(), row.getType(), row.getTitle(), comment, row.getOccurredAt(), facts);
    });
  }

  /** 阶段推进的系统说明不能遮住用户评论；明确无评论也不回填系统原因。 */
  static String comment(List<ActivityFactResponse> facts) {
    var authored = facts.stream().filter(fact -> !ActivitySource.isSystemExplanation(fact.getSourceType())).toList();
    return (authored.isEmpty() ? facts : authored).stream().map(ActivityFactResponse::getComment)
        .filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
  }

  private Set<String> visible(List<ActivityRecordRowDTO> rows, Map<String, List<ActivityFactResponse>> parsed, boolean shared) {
    rows.forEach(row -> parsed.put(row.getId(), Arrays.stream(objectMapper.readValue(row.getFactsJson(), ActivityFactResponse[].class))
        .sorted(Comparator.comparing(ActivityFactResponse::getOccurredAt).reversed()
            .thenComparing(ActivityFactResponse::getSourceId, Comparator.reverseOrder())).toList()));
    if (shared) return rows.stream().map(ActivityRecordRowDTO::getId).collect(Collectors.toSet());
    Set<String> interviewIds = parsed.values().stream().flatMap(List::stream).map(ActivityFactResponse::getInterviewId)
        .filter(value -> value != null).collect(Collectors.toSet());
    var actor = authorization.currentActor();
    Set<String> allowed = new HashSet<>();
    if (!interviewIds.isEmpty()) {
      participants.findParticipantsByInterviewIds(interviewIds).stream()
          .filter(value -> actor.getUserId().equals(value.getUserId())).forEach(value -> allowed.add(value.getInterviewId()));
      if (!(actor.hasPermission("interview.feedback.submit-own") && !actor.hasPermission("interview.schedule"))) {
        var targets = interviews.listByIds(interviewIds).stream().map(value -> new AuthorizationTarget(
            "INTERVIEW", value.getId(), value.getOwnerUserId(), value.getOwnerDepartmentId(), Set.of())).toList();
        allowed.addAll(authorization.accessibleObjectIds(actor, "interview.feedback.read-authorized", targets));
      }
    }
    Set<String> visible = new HashSet<>();
    for (var row : rows) {
      var facts = parsed.get(row.getId()).stream().filter(fact ->
          (fact.getInterviewId() == null || allowed.contains(fact.getInterviewId()))
              && (!ActivitySource.RESTRICTION.matches(fact.getSourceType()) || actor.hasPermission("submission.read"))).toList();
      if (facts.isEmpty()) parsed.remove(row.getId());
      else { parsed.put(row.getId(), facts); visible.add(row.getId()); }
    }
    return visible;
  }

  private StableCursor decode(String cursor, String scope) {
    if (cursor == null || cursor.isBlank()) return null;
    try {
      var parts = cursorCodec.decode(cursor, scope, 2);
      if (parts.get(1).isBlank() || parts.get(1).length() > 100) throw new IllegalArgumentException();
      return new StableCursor(Instant.parse(parts.get(0)), parts.get(1));
    } catch (IllegalArgumentException | DateTimeParseException exception) {
      throw PlatformExceptions.unprocessable("ACTIVITY_CURSOR_INVALID", "活动记录分页游标无效");
    }
  }
}
