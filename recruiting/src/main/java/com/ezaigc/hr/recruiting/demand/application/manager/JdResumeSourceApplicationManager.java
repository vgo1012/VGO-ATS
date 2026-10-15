package com.ezaigc.hr.recruiting.demand.application.manager;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorCount;
import com.ezaigc.hr.common.web.pagination.CursorPages;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.common.web.pagination.StableCursor;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.domain.dto.CreateJdResumeSourceDTO;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdResumeSourceEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdSourceEntity;
import com.ezaigc.hr.recruiting.demand.service.JdSourcePersistenceService;
import com.ezaigc.hr.recruiting.demand.domain.entity.MailSourceMessageEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandAuditEventPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdResumeSourcePersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdVersionPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.MailSourceMessagePersistenceService;
import com.ezaigc.hr.talent.api.CandidateDirectoryService;
import com.ezaigc.hr.talent.api.ResumeSubmissionQueryService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 本机模拟邮件的来源登记，使用正式权限、附件版本和审计，不执行招聘决策。 */
@Service
public class JdResumeSourceApplicationManager {
  @Resource private JdSourcePersistenceService jdSources;
  @Resource private AuthorizationService authorization;
  @Resource private DemandQueryService queries;
  @Resource private JdVersionPersistenceService versions;
  @Resource private CandidateDirectoryService candidates;
  @Resource private ResumeSubmissionQueryService resumes;
  @Resource private MailSourceMessagePersistenceService messages;
  @Resource private JdResumeSourcePersistenceService sources;
  @Resource private DemandAuditEventPersistenceService audit;
  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;
  @Resource private CursorCodec cursors;

  @Transactional
  public JdResumeSourceEntity register(String jdVersionId, CreateJdResumeSourceDTO input) {
    requireAccess(jdVersionId, "demand.manage");
    authorization.requirePermission("talent.read");
    var actor = authorization.currentActor();
    var version = versions.findJdVersion(jdVersionId);
    String body = normalize(input.getPlainBody());
    String jdText = normalize(version.getRawText());
    // 模拟器只接受一个明确的 JD 块，禁止按标题猜测或把多个 JD 合并。
    String begin = "【JD开始】\n";
    String end = "\n【JD结束】";
    int start = body.indexOf(begin);
    int finish = body.indexOf(end);
    if (start < 0 || finish < start + begin.length()
        || body.indexOf(begin, start + begin.length()) >= 0
        || body.indexOf(end, finish + end.length()) >= 0
        || !body.substring(start + begin.length(), finish).equals(jdText)) {
      throw new PlatformException(HttpStatus.CONFLICT, "MAIL_JD_AMBIGUOUS", "邮件必须包含唯一且与所选版本一致的 JD 原文块", true);
    }
    String messageId = input.getSourceMessageId().strip();
    if (!messageId.startsWith("<") || !messageId.endsWith(">")
        || messageId.indexOf('\r') >= 0 || messageId.indexOf('\n') >= 0) {
      throw new PlatformException(HttpStatus.BAD_REQUEST, "MAIL_MESSAGE_ID_INVALID", "邮件 Message-ID 格式无效", true);
    }
    var candidate = candidates.lockActive(input.getCandidateId());
    var resume = resumes.requireSubmissionSnapshot(input.getCandidateId(), input.getResumeVersionId());
    String messageHash = hash(messageId);
    String payloadHash = hash(jdVersionId, input.getSenderEmail(), actor.getWorkEmail(), input.getSubject(), body);
    var now = clock.instant();
    var message = new MailSourceMessageEntity();
    message.setId(ids.newId());
    message.setJdVersionId(jdVersionId);
    message.setSourceMessageId(messageId);
    message.setMessageHash(messageHash);
    message.setPayloadHash(payloadHash);
    message.setSenderEmail(input.getSenderEmail());
    message.setRecipientEmail(actor.getWorkEmail());
    message.setSubject(input.getSubject());
    message.setPlainBody(body);
    message.setDeliveryType("MAIL_SIMULATION");
    message.setCreateId(actor.getUserId());
    message.setCreateName(actor.getDisplayName());
    message.setCreateTime(now);
    message.setUpdateId(actor.getUserId());
    message.setUpdateTime(now);
    // 唯一索引使并发重放先串行化，再核对同 Message-ID 的正文和 JD。
    messages.insertIfAbsent(message);
    var storedMessage = messages.findExisting(actor.getUserId(), messageHash);
    if (storedMessage == null || !Objects.equals(storedMessage.getPayloadHash(), payloadHash)) {
      throw new PlatformException(HttpStatus.CONFLICT, "MAIL_MESSAGE_CONFLICT", "同一 Message-ID 已用于其他正文或 JD，请核对原始邮件", true);
    }
    var source = new JdResumeSourceEntity();
    source.setId(ids.newId());
    source.setMailSourceId(storedMessage.getId());
    source.setCandidateId(candidate.candidateId());
    source.setCandidateName(candidate.fullName());
    source.setResumeVersionId(resume.resumeVersionId());
    source.setOriginalFileName(resume.originalFileName());
    source.setContentSha256(resume.contentSha256());
    source.setCreateId(actor.getUserId());
    source.setCreateTime(now);
    source.setUpdateId(actor.getUserId());
    source.setUpdateTime(now);
    sources.insertIfAbsent(source);
    var stored = sources.findExisting(storedMessage.getId(), resume.contentSha256());
    if (stored == null || !Objects.equals(stored.getCandidateId(), candidate.candidateId())) {
      throw new PlatformException(HttpStatus.CONFLICT, "MAIL_ATTACHMENT_IDENTITY_CONFLICT", "同一邮件附件已登记到其他候选人，请核对身份", true);
    }
    if (stored.getId().equals(source.getId())) {
      if (audit.insertAudit(ids.newId(), "JD_RESUME_SOURCE_REGISTERED", actor.getUserId(), "JD_VERSION", jdVersionId, "模拟邮件附件登记", now) != 1) {
        throw new IllegalStateException("邮件来源审计未写入唯一记录");
      }
    }
    if (jdSources.findSource(actor.getUserId(), "MAIL", messageHash) == null) {
      var origin = new JdSourceEntity();
      origin.setId(ids.newId()); origin.setJdVersionId(jdVersionId); origin.setSourceType("MAIL");
      origin.setSourceKey(messageHash); origin.setMailSourceId(storedMessage.getId()); origin.setOwnerUserId(actor.getUserId());
      origin.setCreateId(actor.getUserId()); origin.setCreateTime(now); origin.setUpdateId(actor.getUserId()); origin.setUpdateTime(now);
      if (!jdSources.save(origin)) throw new IllegalStateException("邮件 JD 来源写入失败");
    }
    return stored;
  }

  @Transactional(readOnly = true)
  public CursorPage<JdResumeSourceEntity> list(String jdVersionId, String cursor, Integer requestedLimit) {
    requireAccess(jdVersionId, "demand.read");
    authorization.requirePermission("talent.read");
    int limit = PageLimits.resolve(requestedLimit);
    String scope = "jd-resume-sources:" + jdVersionId + ":" + authorization.currentActor().getUserId();
    var position = StableCursor.decode(cursors, scope, cursor);
    var context = queries.requireJdVersion(jdVersionId);
    var rows = sources.findPage(jdVersionId, position, limit + 1);
    return CursorPages.page(rows, limit, cursors, scope,
        JdResumeSourceEntity::getCreateTime, JdResumeSourceEntity::getId);
  }

  @Transactional(readOnly = true)
  public CursorCount count(String jdVersionId) {
    requireAccess(jdVersionId, "demand.read");
    authorization.requirePermission("talent.read");
    return new CursorCount(sources.countByJdVersion(jdVersionId), null, false);
  }

  @Transactional(readOnly = true)
  public MailSourceMessageEntity mail(String id) {
    var message = messages.getById(id);
    if (message == null) throw new PlatformException(HttpStatus.NOT_FOUND, "MAIL_SOURCE_NOT_FOUND", "邮件来源不存在", false);
    requireAccess(message.getJdVersionId(), "demand.read");
    var context = queries.requireJdVersion(message.getJdVersionId());
    authorization.requirePermission("talent.read");
    return message;
  }

  private void requireAccess(String id, String permission) {
    var v = "demand.read".equals(permission)
        ? queries.requireJdVersion(id) : queries.requireJdVersionForNewUse(id);
    if (!"demand.read".equals(permission) && v.isDeleted())
      throw new PlatformException(HttpStatus.CONFLICT, "JD_DELETED", "JD 已删除，不能新增来源", false);
    authorization.requireAccess(permission, new AuthorizationTarget(v.getDemandId() == null ? "JD" : "DEMAND", v.getDemandId() == null ? v.getJdId() : v.getDemandId(),
        v.getOwnerUserId(), v.getOwnerDepartmentId(), v.getCollaboratorUserIds()));
  }
  private static String normalize(String value) { return value.replace("\r\n", "\n").replace('\r', '\n'); }
  private static String hash(String... values) { return Sha256Utils.fingerprintStrings("", 0, values); }
}
