package com.ezaigc.hr.talent.application.manager;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.request.IdempotencyKey;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.application.support.WorkCalendarLifecycleImpactSupport;
import com.ezaigc.hr.talent.domain.dto.WorkCalendarImpactDTO;
import com.ezaigc.hr.talent.domain.dto.WorkCalendarDayDTO;
import com.ezaigc.hr.talent.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.talent.domain.entity.TalentProcessEvent;
import tools.jackson.databind.ObjectMapper;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarDayEntity;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarRevisionEntity;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarYearEntity;
import com.ezaigc.hr.talent.domain.response.WorkCalendarDayResponse;
import com.ezaigc.hr.talent.domain.response.PublishedWorkCalendarResponse;
import com.ezaigc.hr.talent.domain.response.WorkCalendarPreviewResponse;
import com.ezaigc.hr.talent.domain.response.WorkCalendarRevisionResponse;
import com.ezaigc.hr.talent.domain.response.WorkCalendarYearResponse;
import com.ezaigc.hr.talent.service.TalentIdempotencyPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import com.ezaigc.hr.talent.service.WorkCalendarPersistenceService;
import com.ezaigc.hr.talent.enums.WorkCalendarRevisionStatus;
import com.ezaigc.hr.talent.enums.WorkCalendarDayType;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** 管理员日历维护；所有年度写操作先锁同一年度指针，已发布日期永不原地修改。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class WorkCalendarApplicationManager {
  @Resource private AuthorizationService authorization;
  @Resource private WorkCalendarPersistenceService calendars;
  @Resource private WorkCalendarLifecycleImpactSupport lifecycle;
  @Resource private TalentProcessEventPersistenceService events;
  @Resource private TalentIdempotencyPersistenceService idempotencies;
  @Resource private TalentIntakePersistenceService intake;
  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;
  @Resource private ObjectMapper json;

  @Transactional(readOnly = true)
  public PublishedWorkCalendarResponse published(int year) {
    authorization.currentActor();
    validateYear(year);
    WorkCalendarYearEntity pointer = calendars.findYear(year);
    if (pointer == null || pointer.getPublishedRevisionId() == null) {
      return new PublishedWorkCalendarResponse(year, null, List.of());
    }
    WorkCalendarRevisionEntity revision = requireRevision(year, pointer.getPublishedRevisionId());
    // 发布切换期间旧指针可能读到已替代版本；两者均是不可变的已发布事实。
    if (!WorkCalendarRevisionStatus.PUBLISHED.matches(revision.getStatus())
        && !WorkCalendarRevisionStatus.SUPERSEDED.matches(revision.getStatus())) {
      throw PlatformExceptions.conflict("WORK_CALENDAR_NOT_PUBLISHED", "工作日历尚未发布");
    }
    return new PublishedWorkCalendarResponse(year, revision.getId(),
        calendars.days(revision.getId()).stream().map(this::dayResponse).toList());
  }

  @Transactional(readOnly = true)
  public WorkCalendarYearResponse year(int year) {
    admin();
    validateYear(year);
    WorkCalendarYearEntity pointer = calendars.findYear(year);
    WorkCalendarRevisionResponse published = pointer == null || pointer.getPublishedRevisionId() == null
        ? null : response(requireRevision(year, pointer.getPublishedRevisionId()));
    WorkCalendarRevisionResponse draft = calendars.revisions(year).stream()
        .filter(revision -> WorkCalendarRevisionStatus.DRAFT.matches(revision.getStatus())).findFirst()
        .map(this::response).orElse(null);
    WorkCalendarYearEntity next = calendars.findYear(year + 1);
    return new WorkCalendarYearResponse(year, published, draft,
        next == null || next.getPublishedRevisionId() == null);
  }

  public WorkCalendarRevisionResponse createDraft(int year, String key) {
    CurrentActorSnapshot actor = admin();
    WorkCalendarYearEntity pointer = lockYear(year);
    String id = ids.newId();
    String replay = reserve(actor.getUserId(), key, "WORK_CALENDAR_DRAFT",
        Sha256Utils.hashUtf8(Integer.toString(year)), id);
    if (replay != null) return response(requireRevision(year, replay));
    List<WorkCalendarRevisionEntity> history = calendars.revisions(year);
    if (history.stream().anyMatch(revision -> WorkCalendarRevisionStatus.DRAFT.matches(revision.getStatus()))) {
      throw PlatformExceptions.conflict("WORK_CALENDAR_DRAFT_EXISTS", "该年度已有草稿，请继续编辑");
    }
    WorkCalendarRevisionEntity draft = new WorkCalendarRevisionEntity();
    draft.setId(id);
    draft.setCalendarYear(year);
    draft.setRevision(history.isEmpty() ? 1 : history.getFirst().getRevision() + 1);
    draft.setStatus(WorkCalendarRevisionStatus.DRAFT.getCode());
    draft.setCreateId(actor.getUserId());
    draft.setCreateName(actor.getDisplayName());
    draft.setCreateTime(clock.instant());
    calendars.save(draft);
    List<WorkCalendarDayEntity> days = new ArrayList<>();
    if (pointer.getPublishedRevisionId() != null) {
      for (WorkCalendarDayEntity source : calendars.days(pointer.getPublishedRevisionId())) {
        days.add(day(id, source.getCalendarDate(), source.getDayType(), source.getDescription()));
      }
    } else {
      LocalDate date = LocalDate.of(year, 1, 1);
      while (date.getYear() == year) {
        boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY
            || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        days.add(day(id, date, weekend ? WorkCalendarDayType.RESTDAY.getCode() : WorkCalendarDayType.WORKDAY.getCode(), null));
        date = date.plusDays(1);
      }
    }
    WorkCalendarCalculationManager.validateCoverage(year, days);
    calendars.insertDays(days);
    audit(actor, "WORK_CALENDAR_DRAFT_CREATED", id, "生成年度草稿，默认模板尚未确认节假日");
    return response(draft);
  }

  /** 一次保存创建不可变已发布修订，年度锁与客户端基线防止覆盖他人修改。 */
  public WorkCalendarRevisionResponse save(int year, String publishedRevisionId,
      String expectedVersion, List<WorkCalendarDayDTO> submittedDays,
      String sourceNote, String reason, String key) {
    CurrentActorSnapshot actor = admin();
    WorkCalendarYearEntity pointer = lockYear(year);
    if (sourceNote == null || sourceNote.isBlank() || sourceNote.length() > 1000
        || reason == null || reason.isBlank() || reason.length() > 500) {
      throw PlatformExceptions.badRequest("WORK_CALENDAR_CONFIRMATION_REQUIRED", "请填写日历来源和修改原因");
    }
    if (submittedDays == null || submittedDays.size() != LocalDate.of(year, 1, 1).lengthOfYear()
        || submittedDays.stream().anyMatch(value -> value == null || value.getDate() == null
            || value.getDate().getYear() != year
            || !(WorkCalendarDayType.WORKDAY.matches(value.getDayType())
                || WorkCalendarDayType.RESTDAY.matches(value.getDayType())
                || WorkCalendarDayType.HOLIDAY.matches(value.getDayType()))
            || (value.getDescription() != null && value.getDescription().length() > 200))) {
      throw PlatformExceptions.badRequest("WORK_CALENDAR_DAYS_INVALID", "请提交完整年度日期、有效类型及说明");
    }
    List<WorkCalendarDayDTO> ordered = submittedDays.stream()
        .sorted(Comparator.comparing(WorkCalendarDayDTO::getDate)).toList();
    String id = ids.newId();
    List<WorkCalendarDayEntity> days = ordered.stream()
        .map(value -> day(id, value.getDate(), value.getDayType(), value.getDescription())).toList();
    WorkCalendarCalculationManager.validateCoverage(year, days);
    // JSON 编码保留字段边界，避免来源/说明内分隔符造成幂等指纹歧义。
    String requestFingerprint = Sha256Utils.hashUtf8(json.writeValueAsString(
        Arrays.asList(year, publishedRevisionId, expectedVersion, ordered, sourceNote, reason)));
    String replay = reserve(actor.getUserId(), key, "WORK_CALENDAR_SAVE", requestFingerprint, id);
    if (replay != null) return response(requireRevision(year, replay));
    if (!Objects.equals(publishedRevisionId, pointer.getPublishedRevisionId())
        || (publishedRevisionId == null && expectedVersion != null)) {
      throw PlatformExceptions.versionConflict("WORK_CALENDAR_VERSION_CONFLICT");
    }
    WorkCalendarRevisionEntity previous = publishedRevisionId == null
        ? null : requireRevision(year, publishedRevisionId);
    if (previous != null && (!WorkCalendarRevisionStatus.PUBLISHED.matches(previous.getStatus())
        || !Objects.equals(expectedVersion, previous.getVersion().toString()))) {
      throw PlatformExceptions.versionConflict("WORK_CALENDAR_VERSION_CONFLICT");
    }
    WorkCalendarRevisionEntity revision = new WorkCalendarRevisionEntity();
    revision.setId(id);
    revision.setCalendarYear(year);
    revision.setRevision(calendars.revisions(year).stream()
        .mapToInt(WorkCalendarRevisionEntity::getRevision).max().orElse(0) + 1);
    revision.setStatus(WorkCalendarRevisionStatus.PUBLISHED.getCode());
    revision.setSourceNote(sourceNote.trim());
    revision.setPublishReason(reason.trim());
    revision.setPublishedBy(actor.getUserId());
    revision.setPublishedName(actor.getDisplayName());
    revision.setPublishedAt(clock.instant());
    revision.setCreateId(actor.getUserId());
    revision.setCreateName(actor.getDisplayName());
    revision.setCreateTime(clock.instant());
    // 回调只追加可恢复重算请求，同一事务提交后才可由后台处理。
    revision.setRecalculationId(lifecycle.published(year, id, requestFingerprint));
    if (!calendars.save(revision)) throw PlatformExceptions.versionConflict();
    if (calendars.insertDays(days) != days.size()) throw PlatformExceptions.versionConflict();
    if (previous != null) {
      previous.setStatus(WorkCalendarRevisionStatus.SUPERSEDED.getCode());
      if (!calendars.updateById(previous)) throw PlatformExceptions.versionConflict();
    }
    if (calendars.publishPointer(year, id) != 1) throw PlatformExceptions.versionConflict();
    audit(actor, "WORK_CALENDAR_PUBLISHED", id, reason.trim());
    return response(revision);
  }

  public WorkCalendarRevisionResponse updateDays(int year, String id, String expectedVersion,
      List<LocalDate> dates, String dayType, String description) {
    CurrentActorSnapshot actor = admin();
    lockYear(year);
    WorkCalendarRevisionEntity draft = draft(year, id, expectedVersion);
    if (dates == null || dates.isEmpty() || dates.size() > 366
        || new HashSet<>(dates).size() != dates.size()
        || dates.stream().anyMatch(date -> date == null || date.getYear() != year)
        || !(WorkCalendarDayType.WORKDAY.matches(dayType) || WorkCalendarDayType.RESTDAY.matches(dayType) || WorkCalendarDayType.HOLIDAY.matches(dayType))
        || (description != null && description.length() > 200)) {
      throw PlatformExceptions.badRequest("WORK_CALENDAR_DAYS_INVALID", "日期必须唯一且属于所选年份，类型与说明须有效");
    }
    if (calendars.updateDays(id, dates, dayType, description) != dates.size()) {
      throw PlatformExceptions.conflict("WORK_CALENDAR_INCOMPLETE", "草稿日期不完整，请重新生成");
    }
    calendars.clearPreview(id);
    audit(actor, "WORK_CALENDAR_DAYS_UPDATED", id,
        "批量修改 " + dates.size() + " 个日期为 " + dayType,
        json.writeValueAsString(Map.of("dates", dates, "dayType", dayType,
            "description", Objects.toString(description, ""))));
    draft.setVersion(draft.getVersion() + 1);
    return response(draft);
  }

  public WorkCalendarPreviewResponse preview(int year, String id, String expectedVersion) {
    admin();
    WorkCalendarYearEntity pointer = lockYear(year);
    WorkCalendarRevisionEntity draft = draft(year, id, expectedVersion);
    List<WorkCalendarDayEntity> days = calendars.days(id);
    WorkCalendarCalculationManager.validateCoverage(year, days);
    WorkCalendarImpactDTO impact = lifecycle.preview(year, id);
    String previewId = ids.newId();
    Instant expires = clock.instant().plus(30, ChronoUnit.MINUTES);
    calendars.setPreview(id, previewId, fingerprint(draft, pointer, impact),
        pointer.getPublishedRevisionId(), expires);
    Map<LocalDate, WorkCalendarDayEntity> old = new HashMap<>();
    if (pointer.getPublishedRevisionId() != null) {
      calendars.days(pointer.getPublishedRevisionId()).forEach(day -> old.put(day.getCalendarDate(), day));
    }
    List<WorkCalendarDayResponse> changed = days.stream()
        .filter(day -> !sameDay(day, old.get(day.getCalendarDate())))
        .map(this::dayResponse).toList();
    return new WorkCalendarPreviewResponse(previewId, expectedVersion,
        pointer.getPublishedRevisionId(), changed, impact.getUpgradeCount(), impact.getDowngradeCount(),
        impact.getShareCount(), impact.getMissingYears(), expires, true);
  }

  public WorkCalendarRevisionResponse publish(int year, String id, String expectedVersion,
      String previewId, String publishedRevisionId, String sourceNote, String reason,
      boolean holidaysConfirmed, String key) {
    CurrentActorSnapshot actor = admin();
    WorkCalendarYearEntity pointer = lockYear(year);
    String requestFingerprint = Sha256Utils.fingerprintObjects(31, year, id, expectedVersion,
        previewId, publishedRevisionId, sourceNote, reason, holidaysConfirmed);
    String replay = reserve(actor.getUserId(), key, "WORK_CALENDAR_PUBLISH", requestFingerprint, id);
    if (replay != null) return response(requireRevision(year, replay));
    WorkCalendarRevisionEntity draft = draft(year, id, expectedVersion);
    if (!holidaysConfirmed || sourceNote == null || sourceNote.isBlank() || sourceNote.length() > 1000
        || reason == null || reason.isBlank() || reason.length() > 500) {
      throw PlatformExceptions.badRequest("WORK_CALENDAR_CONFIRMATION_REQUIRED", "请确认节假日与补班，并填写来源和发布原因");
    }
    WorkCalendarCalculationManager.validateCoverage(year, calendars.days(id));
    if (previewId == null || !previewId.equals(draft.getPreviewId())
        || draft.getPreviewExpiresAt() == null || !clock.instant().isBefore(draft.getPreviewExpiresAt())
        || !Objects.equals(publishedRevisionId, pointer.getPublishedRevisionId())
        || !Objects.equals(draft.getPreviewPublishedId(), pointer.getPublishedRevisionId())
        || !Objects.equals(draft.getPreviewFingerprint(), fingerprint(draft, pointer, lifecycle.preview(year, id)))) {
      throw PlatformExceptions.conflict("WORK_CALENDAR_PREVIEW_EXPIRED", "日历或业务数据已变化，请重新预览后发布");
    }
    if (pointer.getPublishedRevisionId() != null) {
      WorkCalendarRevisionEntity previous = requireRevision(year, pointer.getPublishedRevisionId());
      previous.setStatus(WorkCalendarRevisionStatus.SUPERSEDED.getCode());
      if (!calendars.updateById(previous)) throw PlatformExceptions.versionConflict();
    }
    draft.setStatus(WorkCalendarRevisionStatus.PUBLISHED.getCode());
    draft.setSourceNote(sourceNote.trim());
    draft.setPublishedBy(actor.getUserId());
    draft.setPublishedAt(clock.instant());
    draft.setPublishReason(reason.trim());
    draft.setPublishedName(actor.getDisplayName());
    // 回调只追加可恢复重算请求，禁止同步获取简历锁或自动共享。
    draft.setRecalculationId(lifecycle.published(year, id, draft.getPreviewFingerprint()));
    if (!calendars.updateById(draft)) throw PlatformExceptions.versionConflict();
    calendars.publishPointer(year, id);
    audit(actor, "WORK_CALENDAR_PUBLISHED", id, reason.trim());
    return response(draft);
  }

  @Transactional(readOnly = true)
  public List<WorkCalendarRevisionResponse> history(int year) {
    admin();
    validateYear(year);
    return calendars.revisions(year).stream()
        .filter(revision -> !WorkCalendarRevisionStatus.DRAFT.matches(revision.getStatus()))
        .map(this::response).toList();
  }

  private CurrentActorSnapshot admin() {
    CurrentActorSnapshot actor = authorization.currentActor();
    if (!actor.isSuperAdmin()) {
      throw PlatformExceptions.forbidden("SUPER_ADMIN_REQUIRED", "仅超级管理员可维护工作日历");
    }
    return actor;
  }

  private WorkCalendarYearEntity lockYear(int year) {
    validateYear(year);
    calendars.ensureYear(year);
    return calendars.lockYear(year);
  }

  private static void validateYear(int year) {
    if (year < 2000 || year > 2100) {
      throw PlatformExceptions.badRequest("WORK_CALENDAR_YEAR_INVALID", "年份须在 2000 至 2100 之间");
    }
  }

  private WorkCalendarRevisionEntity requireRevision(int year, String id) {
    WorkCalendarRevisionEntity revision = calendars.getById(id);
    if (revision == null || revision.getCalendarYear() != year) {
      throw PlatformExceptions.notFound("WORK_CALENDAR_NOT_FOUND", "日历版本不存在");
    }
    return revision;
  }

  private WorkCalendarRevisionEntity draft(int year, String id, String expectedVersion) {
    WorkCalendarRevisionEntity revision = requireRevision(year, id);
    if (!WorkCalendarRevisionStatus.DRAFT.matches(revision.getStatus()) || !Objects.equals(expectedVersion, revision.getVersion().toString())) {
      throw PlatformExceptions.versionConflict("WORK_CALENDAR_VERSION_CONFLICT");
    }
    return revision;
  }

  private WorkCalendarDayEntity day(String revisionId, LocalDate date, String type, String description) {
    WorkCalendarDayEntity day = new WorkCalendarDayEntity();
    day.setId(ids.newId());
    day.setCalendarRevisionId(revisionId);
    day.setCalendarDate(date);
    day.setDayType(type);
    day.setDescription(description);
    return day;
  }

  private boolean sameDay(WorkCalendarDayEntity a, WorkCalendarDayEntity b) {
    return b != null && Objects.equals(a.getDayType(), b.getDayType())
        && Objects.equals(a.getDescription(), b.getDescription());
  }

  private WorkCalendarDayResponse dayResponse(WorkCalendarDayEntity day) {
    return new WorkCalendarDayResponse(day.getCalendarDate(), day.getDayType(), day.getDescription());
  }

  private WorkCalendarRevisionResponse response(WorkCalendarRevisionEntity revision) {
    return new WorkCalendarRevisionResponse(revision.getId(), revision.getCalendarYear(),
        revision.getRevision(), revision.getStatus(), revision.getVersion().toString(),
        revision.getSourceNote(), revision.getPublishedBy(), revision.getPublishedAt(),
        revision.getRecalculationId(), revision.getPublishReason(), revision.getPublishedName(), calendars.days(revision.getId()).stream().map(this::dayResponse).toList());
  }

  private String fingerprint(WorkCalendarRevisionEntity draft, WorkCalendarYearEntity pointer,
      WorkCalendarImpactDTO impact) {
    // 预览跨越下一次 01:00 后失效，不能发布上一结算边界的影响结果。
    Instant now = clock.instant();
    LocalDate boundary = now.atZone(ZoneId.of("Asia/Shanghai")).minusHours(1).toLocalDate().minusDays(1);
    return Sha256Utils.fingerprintObjects(31, draft.getId(), draft.getVersion(),
        pointer.getPublishedRevisionId(), impact.getFingerprint(), boundary);
  }

  private String reserve(String actor, String key, String action, String fingerprint, String target) {
    key = IdempotencyKey.fromHttpHeader(key);
    IdempotencyRecord existing = idempotencies.findIdempotency(actor, key);
    if (existing == null && intake.insertIdempotency(actor, key, action, fingerprint, target, clock.instant()) == 1) return null;
    if (existing == null) existing = idempotencies.findIdempotency(actor, key);
    if (existing == null) throw PlatformExceptions.conflict("IDEMPOTENCY_RESERVATION_FAILED", "请求处理中，请重试");
    if (!action.equals(existing.getActionCode()) || !fingerprint.equals(existing.getRequestFingerprint())) {
      throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "同一幂等键不能用于不同请求");
    }
    return existing.getTargetId();
  }

  private void audit(CurrentActorSnapshot actor, String event, String id, String reason) {
    audit(actor, event, id, reason, null);
  }

  private void audit(CurrentActorSnapshot actor, String type, String id, String reason, String ext) {
    TalentProcessEvent event = new TalentProcessEvent();
    event.setId(ids.newId());
    event.setEventType(type);
    event.setActorUserId(actor.getUserId());
    event.setTargetType("WORK_CALENDAR");
    event.setTargetId(id);
    event.setReason(reason);
    event.setOccurredAt(clock.instant());
    event.setExt(ext);
    events.save(event);
  }
}
