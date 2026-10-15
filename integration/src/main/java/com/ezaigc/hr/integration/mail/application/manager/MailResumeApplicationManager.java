package com.ezaigc.hr.integration.mail.application.manager;

import static com.ezaigc.hr.common.core.constant.PlatformActorConstants.SYSTEM_ACTOR_ID;
import static com.ezaigc.hr.common.web.pagination.CursorPages.page;
import static com.ezaigc.hr.integration.mail.constant.MailResumeConstants.RESOURCE_TYPE;
import static com.ezaigc.hr.integration.mail.constant.MailResumeConstants.TASK_TYPE;
import static com.ezaigc.hr.integration.mail.enums.MailIngestRunStatus.FAILED;
import static com.ezaigc.hr.integration.mail.enums.MailIngestRunStatus.PARTIAL;
import static com.ezaigc.hr.integration.mail.enums.MailIngestRunStatus.QUEUED;
import static com.ezaigc.hr.integration.mail.enums.MailIngestRunStatus.RUNNING;
import static com.ezaigc.hr.integration.mail.enums.MailIngestRunStatus.SUCCEEDED;
import static com.ezaigc.hr.integration.mail.enums.MailRouteRuleStatus.ACTIVE;
import static com.ezaigc.hr.integration.mail.enums.MailResumeStatus.DISPATCHED;
import static com.ezaigc.hr.integration.mail.enums.MailResumeStatus.DISPATCH_FAILED;
import static com.ezaigc.hr.integration.mail.enums.MailResumeStatus.MATCHED;
import static com.ezaigc.hr.integration.mail.enums.MailResumeStatus.REJECTED;
import static com.ezaigc.hr.integration.mail.enums.MailResumeStatus.REVIEW_REQUIRED;
import static com.ezaigc.hr.talent.api.ResumeUploadPolicy.MAX_FILE_SIZE_BYTES;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.EnqueueTask;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorCount;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.common.web.pagination.StableCursor;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.integration.config.MailResumeProperties;
import com.ezaigc.hr.integration.mail.application.MailResumeMatcher;
import com.ezaigc.hr.integration.mail.application.ResumeAttachmentNameParser;
import com.ezaigc.hr.integration.mail.application.ResumeAttachmentNameParser.Parsed;
import com.ezaigc.hr.integration.mail.application.MailResumeMatcher.Route;
import com.ezaigc.hr.integration.mail.application.provider.MailIntakePort;
import com.ezaigc.hr.integration.mail.domain.MailResumeIngestRun;
import com.ezaigc.hr.integration.mail.domain.MailJdOptionResponse;
import com.ezaigc.hr.integration.mail.domain.MailResumeLinkRequest;
import com.ezaigc.hr.integration.mail.domain.MailResumeRecord;
import com.ezaigc.hr.integration.mail.domain.MailResumeReviewResponse;
import com.ezaigc.hr.integration.mail.domain.MailboxCursor;
import com.ezaigc.hr.integration.mail.service.MailResumeStore;
import com.ezaigc.hr.recruiting.demand.api.MailJdRoutingService;
import com.ezaigc.hr.recruiting.demand.api.MailJdRoutingService.Target;
import com.ezaigc.hr.talent.api.MailResumeIntakeService;
import com.ezaigc.hr.talent.api.MalwareScanPort;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import com.ezaigc.hr.talent.enums.MalwareScanVerdict;
import jakarta.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MailResumeApplicationManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(MailResumeApplicationManager.class);

  @Resource private MailResumeProperties properties;
  @Resource private ObjectProvider<MailIntakePort> mailPorts;
  @Resource private MailResumeStore mapper;
  @Resource private MailJdRoutingService routing;
  @Resource private MalwareScanPort scanner;
  @Resource private ObjectStoragePort objects;
  @Resource private MailResumeIntakeService intakes;
  @Resource private AuthorizationService authorization;
  @Resource private AsyncTaskPort tasks;
  @Resource private PlatformIdGenerator ids;
  @Resource private CursorCodec cursors;
  @Resource private TransactionTemplate transactions;
  @Resource private Clock clock;

  @Transactional
  public TaskSnapshot schedule() {
    long slot = clock.instant().getEpochSecond() / 300;
    String slotKey = "mail-resume:" + properties.getMailboxKey() + ":" + slot;
    TaskSnapshot existing = tasks.getByIdempotency(SYSTEM_ACTOR_ID, slotKey);
    if (existing != null) return existing;
    Instant now = clock.instant();
    String runId = ids.newId();
    mapper.insertRun(runId, slotKey, properties.getMailboxKey(), QUEUED.getCode(), SYSTEM_ACTOR_ID, now);
    MailResumeIngestRun run = mapper.findRunBySlot(slotKey);
    return tasks.enqueue(new EnqueueTask(
        ids.newId(), TASK_TYPE, RESOURCE_TYPE, run.getId(), SYSTEM_ACTOR_ID, SYSTEM_ACTOR_ID,
        SYSTEM_ACTOR_ID, "talent.manage", "talent.manage", slotKey,
        Sha256Utils.hashUtf8(properties.getMailboxKey() + ":" + slot), 3, now));
  }

  public void process(TaskSnapshot task) {
    String runId = task.resourceId();
    Instant started = clock.instant();
    mapper.markRunRunning(runId, RUNNING.getCode(), SYSTEM_ACTOR_ID, started);
    try {
      Counters counters = collect();
      mapper.completeRun(runId, counters.scanned, counters.attachments, counters.matched,
          counters.reviews, counters.duplicates, counters.failed, counters.errorCode,
          counters.errorDetail, SYSTEM_ACTOR_ID, clock.instant(),
          counters.failed == 0 && !counters.deferred ? SUCCEEDED.getCode() : PARTIAL.getCode());
      tasks.succeed(task, runId);
    } catch (UidValidityChangedException exception) {
      fail(task, runId, "MAIL_UIDVALIDITY_CHANGED",
          "邮箱 UIDVALIDITY 已变化，已停止推进游标，请管理员确认是否重新建立零基线");
    } catch (MailboxConfigurationChangedException exception) {
      fail(task, runId, "MAILBOX_CONFIGURATION_CHANGED",
          "邮箱文件夹与已有游标不一致，请更换 mailbox-key 后重新建立零基线");
    } catch (MalwareScannerUnavailableException exception) {
      fail(task, runId, "MALWARE_SCANNER_UNAVAILABLE",
          "附件安全扫描暂不可用，游标未推进，恢复后会自动重试");
    } catch (RuntimeException exception) {
      LOGGER.error("mail-resume-ingest-failed runId={} errorType={}", runId,
          exception.getClass().getName(), SafeLogException.from(exception));
      fail(task, runId, "MAIL_RESUME_INGEST_FAILED", "邮件简历采集失败，请检查邮箱连接和任务日志");
    }
  }

  private void fail(TaskSnapshot task, String runId, String code, String detail) {
    mapper.failRun(runId, FAILED.getCode(), code, detail, SYSTEM_ACTOR_ID, clock.instant());
    tasks.fail(task, code, detail, true);
  }

  private Counters collect() {
    MailIntakePort port = mailPorts.getIfAvailable();
    if (port == null) throw new IllegalStateException("邮件简历采集未启用");
    Counters counters = new Counters();
    retryDispatchable(counters);
    MailboxCursor cursor = mapper.findCursor(properties.getMailboxKey());
    MailIntakePort.Scan scan = port.scan(cursor == null ? null : cursor.getLastUid(), properties.getBatchSize());
    if (cursor == null) {
      mapper.insertCursor(properties.getMailboxKey(), properties.getFolder(), scan.uidValidity(),
          scan.highestUid(), SYSTEM_ACTOR_ID, clock.instant());
      return counters;
    }
    if (!properties.getFolder().equals(cursor.getFolder())) throw new MailboxConfigurationChangedException();
    if (cursor.getUidValidity() != scan.uidValidity()) throw new UidValidityChangedException();
    long expectedUid = cursor.getLastUid();
    long targetHighestUid = scan.highestUid();
    List<Target> targets = routing.activeTargets();
    int budget = properties.getBatchSize();
    int scannedPages = 1;
    while (true) {
      for (MailIntakePort.MailMessage message : scan.messages()) {
        if (counters.scanned >= budget) break;
        counters.scanned++;
        MailResumeRecord existing = mapper.findByUid(properties.getMailboxKey(), properties.getFolder(),
            scan.uidValidity(), message.uid());
        if (existing != null) {
          counters.duplicates++;
          advance(expectedUid, message.uid(), scan.uidValidity());
          expectedUid = message.uid();
          continue;
        }
        Prepared prepared = prepare(message, scan.uidValidity(), targets);
        counters.attachments += message.resumeAttachments().size();
        if (prepared.duplicate) counters.duplicates++;
        if (REVIEW_REQUIRED.matches(prepared.record.getStatus())) counters.reviews++;
        if (MATCHED.matches(prepared.record.getStatus())) counters.matched++;
        if (REJECTED.matches(prepared.record.getStatus()) && !prepared.duplicate) {
          counters.failed++;
          counters.error(prepared.record.getFailureCode(), prepared.record.getFailureDetail());
        }
        persistAndAdvance(prepared.record, expectedUid, message.uid(), scan.uidValidity());
        expectedUid = message.uid();
        if (MATCHED.matches(prepared.record.getStatus())) dispatch(prepared.record, counters);
      }
      if (expectedUid >= targetHighestUid) break;
      if (scan.messages().isEmpty()) {
        long checkedThrough = Math.min(scan.scannedThroughUid(), targetHighestUid);
        if (checkedThrough <= expectedUid) throw new IllegalStateException("邮件扫描没有推进已检查UID区间");
        advance(expectedUid, checkedThrough, scan.uidValidity());
        expectedUid = checkedThrough;
        if (expectedUid >= targetHighestUid) break;
      }
      if (counters.scanned >= budget || scannedPages >= budget) {
        counters.deferred = true;
        counters.error("MAIL_RESUME_BATCH_DEFERRED", "本轮采集预算已用尽，剩余邮件将在后续批次继续处理");
        break;
      }
      scan = port.scan(expectedUid, properties.getBatchSize());
      scannedPages++;
      if (cursor.getUidValidity() != scan.uidValidity()) throw new UidValidityChangedException();
    }
    return counters;
  }

  private Prepared prepare(MailIntakePort.MailMessage message, long uidValidity, List<Target> targets) {
    MailResumeRecord record = baseRecord(message, uidValidity);
    if (message.readFailureCode() != null) {
      return rejected(record, message.readFailureCode(), "邮件格式或字符集无效，已隔离该邮件");
    }
    List<MailIntakePort.Attachment> attachments = message.resumeAttachments();
    if (attachments.isEmpty()) return rejected(record, "MAIL_RESUME_ATTACHMENT_MISSING", "邮件没有支持的简历附件");
    if (attachments.size() != 1) return rejected(record, "MAIL_RESUME_ATTACHMENT_MULTIPLE", "邮件包含多个简历附件");
    MailIntakePort.Attachment attachment = attachments.getFirst();
    byte[] content = attachment.content();
    record.setAttachmentName(limit(attachment.fileName(), 255));
    record.setMediaType(mediaType(attachment.fileName()));
    record.setSizeBytes(content.length);
    if (content.length == 0 || content.length > MAX_FILE_SIZE_BYTES) {
      return rejected(record, "MAIL_RESUME_ATTACHMENT_SIZE_INVALID", "简历附件必须大于 0 且不超过 10 MB");
    }
    String hash = Sha256Utils.hashBytes(content);
    record.setContentSha256(hash);
    boolean duplicate = mapper.countDuplicateContent(record.getMessageIdHash(), hash) > 0;
    MalwareScanVerdict verdict;
    try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
      verdict = scanner.scan(record.getAttachmentName(), input);
    } catch (IOException exception) {
      verdict = MalwareScanVerdict.UNAVAILABLE;
    }
    String objectKey = (verdict == MalwareScanVerdict.CLEAN ? "resume/" : "quarantine/")
        + hash.substring(0, 2) + "/" + hash;
    record.setObjectKey(objectKey);
    storeIfAbsent(objectKey, content);
    if (verdict == MalwareScanVerdict.MALICIOUS) {
      Prepared rejected = rejected(record, "MALWARE_OR_SIGNATURE_REJECTED", "附件安全检查未通过");
      return new Prepared(rejected.record, duplicate);
    }
    if (verdict != MalwareScanVerdict.CLEAN) throw new MalwareScannerUnavailableException();
    if (duplicate) {
      Prepared rejected = rejected(record, "MAIL_RESUME_DUPLICATE", "Message-ID 或附件内容已采集，不重复入库");
      return new Prepared(rejected.record, true);
    }
    Parsed parsed = ResumeAttachmentNameParser.parse(attachment.fileName(), message.subject());
    record.setParsedJdCode(parsed.jdCode());
    record.setParsedJdTitle(parsed.title());
    record.setParsedRegion(parsed.region());
    String ruleJdId = parsed.title() == null || parsed.region() == null ? null
        : mapper.findRule(properties.getMailboxKey(), parsed.title(), parsed.region());
    Route route = MailResumeMatcher.match(parsed, targets, ruleJdId);
    record.setMatchType(route.matchType());
    if (route.target() == null) {
      record.setStatus(REVIEW_REQUIRED.getCode());
      record.setFailureCode(route.failureCode());
      record.setFailureDetail(route.failureDetail());
    } else {
      record.setStatus(MATCHED.getCode());
      record.setJdId(route.target().jdId());
      record.setJdVersionId(route.target().jdVersionId());
      record.setOwnerUserId(route.target().ownerUserId());
      record.setOwnerDepartmentId(route.target().ownerDepartmentId());
    }
    return new Prepared(record, duplicate);
  }

  private MailResumeRecord baseRecord(MailIntakePort.MailMessage message, long uidValidity) {
    MailResumeRecord record = new MailResumeRecord();
    record.setId(ids.newId());
    record.setMailboxKey(properties.getMailboxKey());
    record.setFolder(properties.getFolder());
    record.setSourceChannelId(properties.getSourceChannelId());
    record.setUidValidity(uidValidity);
    record.setMailUid(message.uid());
    String identity = message.messageId() == null
        ? properties.getMailboxKey() + ":" + uidValidity + ":" + message.uid()
        : message.messageId();
    record.setMessageIdHash(Sha256Utils.hashUtf8(identity));
    record.setSenderEmail(limit(message.sender(), 320));
    record.setSubject(limit(message.subject(), 500));
    record.setReceivedAt(Objects.requireNonNullElseGet(message.receivedAt(), clock::instant));
    Instant now = clock.instant();
    record.setCreateId(SYSTEM_ACTOR_ID);
    record.setUpdateId(SYSTEM_ACTOR_ID);
    record.setCreateTime(now);
    record.setUpdateTime(now);
    return record;
  }

  private Prepared rejected(MailResumeRecord record, String code, String detail) {
    record.setStatus(REJECTED.getCode());
    record.setFailureCode(code);
    record.setFailureDetail(detail);
    return new Prepared(record, false);
  }

  private void persistAndAdvance(MailResumeRecord record, long expectedUid, long newUid, long uidValidity) {
    transactions.executeWithoutResult(ignored -> {
      if (mapper.insertMessage(record) != 1) throw new IllegalStateException("邮件简历记录写入失败");
      if (mapper.advanceCursor(properties.getMailboxKey(), uidValidity, expectedUid, newUid,
          SYSTEM_ACTOR_ID, clock.instant()) != 1) {
        throw new IllegalStateException("邮箱 UID 游标并发变化");
      }
    });
  }

  private void advance(long expectedUid, long newUid, long uidValidity) {
    if (mapper.advanceCursor(properties.getMailboxKey(), uidValidity, expectedUid, newUid,
        SYSTEM_ACTOR_ID, clock.instant()) != 1) {
      throw new IllegalStateException("邮箱 UID 游标并发变化");
    }
  }

  private void retryDispatchable(Counters counters) {
    for (MailResumeRecord record : mapper.findDispatchable(
        MATCHED.getCode(), DISPATCH_FAILED.getCode(), properties.getBatchSize())) {
      dispatch(record, counters);
    }
  }

  private void dispatch(MailResumeRecord record, Counters counters) {
    try {
      var result = intakes.importResume(new MailResumeIntakeService.Command(
          record.getId(), record.getSourceChannelId(), record.getJdVersionId(), record.getOwnerUserId(),
          record.getOwnerDepartmentId(), record.getAttachmentName(), record.getSizeBytes(), record.getObjectKey()));
      mapper.markDispatched(record.getId(), result.intakeId(), DISPATCHED.getCode(),
          MATCHED.getCode(), DISPATCH_FAILED.getCode(), SYSTEM_ACTOR_ID, clock.instant());
      record.setStatus(DISPATCHED.getCode());
      record.setIntakeId(result.intakeId());
    } catch (PlatformException exception) {
      dispatchFailed(record, counters, exception.code(), exception.getMessage(), exception);
    } catch (RuntimeException exception) {
      dispatchFailed(record, counters, "MAIL_RESUME_DISPATCH_FAILED",
          "简历入库任务启动失败，请检查 JD、负责人和来源渠道", exception);
    }
  }

  private void dispatchFailed(MailResumeRecord record, Counters counters, String code,
      String detail, RuntimeException exception) {
    LOGGER.warn("mail-resume-dispatch-failed mailRecordId={} errorType={}", record.getId(),
        exception.getClass().getName(), SafeLogException.from(exception));
    String safeDetail = limit(detail, 500);
    mapper.markDispatchFailed(record.getId(), DISPATCH_FAILED.getCode(), MATCHED.getCode(),
        code, safeDetail, SYSTEM_ACTOR_ID, clock.instant());
    record.setStatus(DISPATCH_FAILED.getCode());
    record.setFailureCode(code);
    record.setFailureDetail(safeDetail);
    counters.failed++;
    counters.error(code, safeDetail);
  }

  private void storeIfAbsent(String objectKey, byte[] content) {
    try {
      if (!objects.exists(objectKey)) objects.put(objectKey, new ByteArrayInputStream(content));
    } catch (IOException exception) {
      throw new IllegalStateException("邮件简历附件存储失败", exception);
    }
  }

  public CursorPage<MailResumeReviewResponse> reviews(Instant from, Instant to, String title,
      String region, String attachment, String cursor, Integer requestedLimit) {
    requireSuperAdmin();
    if (from != null && to != null && !from.isBefore(to)) {
      throw PlatformExceptions.unprocessable("MAIL_REVIEW_TIME_RANGE_INVALID", "from 必须早于 to");
    }
    String normalizedTitle = MailJdRoutingService.normalize(title);
    String normalizedRegion = MailJdRoutingService.normalize(region);
    String normalizedAttachment = bounded(attachment, 255);
    int limit = PageLimits.resolve(requestedLimit);
    String scope = CursorScopes.scope("mail-resume.reviews", authorization.currentActor().getUserId(),
        from, to, normalizedTitle, normalizedRegion, normalizedAttachment);
    StableCursor decoded = StableCursor.decode(cursors, scope, cursor);
    List<MailResumeRecord> rows = mapper.findReviews(
        REVIEW_REQUIRED.getCode(), DISPATCH_FAILED.getCode(), from, to, normalizedTitle, normalizedRegion,
        normalizedAttachment, decoded == null ? null : decoded.getSortTime(),
        decoded == null ? null : decoded.getId(), limit + 1);
    return page(rows, limit, cursors, scope, this::response,
        MailResumeRecord::getReceivedAt, MailResumeRecord::getId);
  }

  public CursorCount countReviews(Instant from, Instant to, String title,
      String region, String attachment) {
    requireSuperAdmin();
    if (from != null && to != null && !from.isBefore(to)) {
      throw PlatformExceptions.unprocessable("MAIL_REVIEW_TIME_RANGE_INVALID", "from 必须早于 to");
    }
    String normalizedTitle = MailJdRoutingService.normalize(title);
    String normalizedRegion = MailJdRoutingService.normalize(region);
    String normalizedAttachment = bounded(attachment, 255);
    long count = mapper.countReviews(
        REVIEW_REQUIRED.getCode(), DISPATCH_FAILED.getCode(), from, to,
        normalizedTitle, normalizedRegion, normalizedAttachment);
    return new CursorCount(count, null, false);
  }

  public List<MailJdOptionResponse> jdOptions(String query) {
    requireSuperAdmin();
    String normalized = MailJdRoutingService.normalize(bounded(query, 180));
    return routing.activeTargets().stream()
        .filter(Target::ownerValid)
        .filter(target -> normalized == null
            || target.title().contains(normalized)
            || target.code() != null && target.code().contains(normalized))
        .limit(50)
        .map(target -> new MailJdOptionResponse(target.jdId(), target.code(), target.title(),
            target.versionNo(), target.regions()))
        .toList();
  }

  public MailResumeReviewResponse link(String id, MailResumeLinkRequest request) {
    requireSuperAdmin();
    String reason = requiredReason(request.getReason(), 500);
    MailResumeRecord linked = transactions.execute(ignored -> linkInTransaction(
        id, request, routing.requireCurrent(request.getJdId()), reason));
    if (linked == null) throw new IllegalStateException("邮件简历关联事务没有返回结果");
    if (!DISPATCHED.matches(linked.getStatus())) dispatch(linked, new Counters());
    return response(mapper.findMessage(id));
  }

  private MailResumeRecord linkInTransaction(String id, MailResumeLinkRequest request,
      Target target, String reason) {
    MailResumeRecord record = mapper.findMessageForUpdate(id);
    if (record == null) throw PlatformExceptions.notFound("MAIL_RESUME_NOT_FOUND", "邮件简历记录不存在");
    if (!REVIEW_REQUIRED.matches(record.getStatus())) {
      if (target.jdId().equals(record.getJdId())) return record;
      throw PlatformExceptions.conflict("MAIL_RESUME_ALREADY_LINKED", "邮件简历已关联 JD，不允许改绑");
    }
    var actor = authorization.currentActor();
    Instant now = clock.instant();
    if (request.isSaveExactRule()) {
      if (record.getParsedJdTitle() == null || record.getParsedRegion() == null) {
        throw PlatformExceptions.unprocessable("MAIL_ROUTE_RULE_FIELDS_REQUIRED", "缺少岗位文本或区域时不能保存精确规则");
      }
      mapper.insertRule(ids.newId(), record.getMailboxKey(), record.getParsedJdTitle(),
          record.getParsedRegion(), target.jdId(), ACTIVE.getCode(), actor.getUserId(), now);
      String existing = mapper.findRule(record.getMailboxKey(), record.getParsedJdTitle(), record.getParsedRegion());
      if (!target.jdId().equals(existing)) {
        throw PlatformExceptions.conflict("MAIL_ROUTE_RULE_CONFLICT", "相同岗位文本和区域已关联其他 JD");
      }
    }
    if (mapper.linkMessage(record.getId(), record.getVersion(), MATCHED.getCode(),
        REVIEW_REQUIRED.getCode(), target.jdId(), target.jdVersionId(), target.ownerUserId(),
        target.ownerDepartmentId(), actor.getUserId(), reason, now) != 1) {
      throw PlatformExceptions.conflict("MAIL_RESUME_LINK_CONFLICT", "邮件简历已被其他管理员处理，请刷新");
    }
    return mapper.findMessage(record.getId());
  }

  private void requireSuperAdmin() {
    if (!authorization.currentActor().isSuperAdmin()) {
      throw PlatformExceptions.forbidden("SUPER_ADMIN_REQUIRED", "需要超级管理员权限");
    }
  }

  public MailResumeIngestRun run(String id) {
    requireSuperAdmin();
    MailResumeIngestRun run = mapper.findRun(id);
    if (run == null) throw PlatformExceptions.notFound("MAIL_RESUME_RUN_NOT_FOUND", "邮件采集运行记录不存在");
    return run;
  }

  private MailResumeReviewResponse response(MailResumeRecord record) {
    return new MailResumeReviewResponse(record.getId(), record.getReceivedAt(), record.getSenderEmail(),
        record.getSubject(), record.getAttachmentName(), record.getParsedJdCode(),
        record.getParsedJdTitle(), record.getParsedRegion(), record.getFailureCode(),
        record.getFailureDetail(), record.getStatus(), record.getMatchType(), record.getJdId(),
        record.getJdVersionId(), record.getIntakeId(), record.getLinkedByUserId(), record.getLinkedAt(),
        Long.toString(record.getVersion()));
  }

  private static String requiredReason(String value, int maximum) {
    String result = bounded(value, maximum);
    if (result == null) throw PlatformExceptions.unprocessable("MAIL_REVIEW_REASON_REQUIRED", "请填写处理原因");
    return result;
  }

  private static String bounded(String value, int maximum) {
    String result = CommonUtils.stripToNull(value);
    if (result != null && result.length() > maximum) {
      throw PlatformExceptions.unprocessable("MAIL_REVIEW_FILTER_TOO_LONG", "输入内容过长");
    }
    return result;
  }

  private static String limit(String value, int maximum) {
    if (value == null) return null;
    String stripped = value.strip();
    int length = stripped.codePointCount(0, stripped.length());
    return length <= maximum ? stripped : stripped.substring(0, stripped.offsetByCodePoints(0, maximum));
  }

  private static String mediaType(String fileName) {
    String lower = fileName.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".pdf")) return "application/pdf";
    if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    return "application/msword";
  }

  private static final class Counters {
    private boolean deferred;
    private int scanned;
    private int attachments;
    private int matched;
    private int reviews;
    private int duplicates;
    private int failed;
    private String errorCode;
    private String errorDetail;

    private void error(String code, String detail) {
      errorCode = code;
      errorDetail = detail;
    }
  }

  private static final class Prepared {
    private final MailResumeRecord record;
    private final boolean duplicate;

    private Prepared(MailResumeRecord record, boolean duplicate) {
      this.record = record;
      this.duplicate = duplicate;
    }
  }

  private static final class UidValidityChangedException extends RuntimeException {}

  private static final class MailboxConfigurationChangedException extends RuntimeException {}

  private static final class MalwareScannerUnavailableException extends RuntimeException {}
}
