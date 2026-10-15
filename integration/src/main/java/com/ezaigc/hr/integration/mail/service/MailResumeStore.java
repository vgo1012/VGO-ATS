package com.ezaigc.hr.integration.mail.service;

import com.ezaigc.hr.integration.mail.domain.MailResumeIngestRun;
import com.ezaigc.hr.integration.mail.domain.MailResumeRecord;
import com.ezaigc.hr.integration.mail.domain.MailboxCursor;
import java.time.Instant;
import java.util.List;

public interface MailResumeStore {
  MailboxCursor findCursor(String mailboxKey);
  int insertCursor(String mailboxKey, String folder, long uidValidity, long lastUid,
      String actorId, Instant now);
  int advanceCursor(String mailboxKey, long uidValidity, long expectedUid, long newUid,
      String actorId, Instant now);
  MailResumeRecord findByUid(String mailboxKey, String folder, long uidValidity, long mailUid);
  int countDuplicateContent(String messageIdHash, String contentHash);
  int insertMessage(MailResumeRecord record);
  List<MailResumeRecord> findReviews(String reviewStatus, String dispatchFailedStatus,
      Instant from, Instant to, String title, String region, String attachment,
      Instant cursorTime, String cursorId, int limit);
  long countReviews(String reviewStatus, String dispatchFailedStatus,
      Instant from, Instant to, String title, String region, String attachment);
  MailResumeRecord findMessage(String id);
  MailResumeRecord findMessageForUpdate(String id);
  int linkMessage(String id, long version, String matchedStatus, String reviewStatus, String jdId,
      String jdVersionId, String ownerUserId, String ownerDepartmentId, String actorId,
      String reason, Instant now);
  int markDispatched(String id, String intakeId, String dispatchedStatus, String matchedStatus,
      String dispatchFailedStatus, String actorId, Instant now);
  int markDispatchFailed(String id, String dispatchFailedStatus, String matchedStatus,
      String code, String detail, String actorId, Instant now);
  List<MailResumeRecord> findDispatchable(String matchedStatus, String dispatchFailedStatus, int limit);
  String findRule(String mailboxKey, String title, String region);
  int insertRule(String id, String mailboxKey, String title, String region, String jdId,
      String status, String actorId, Instant now);
  int insertRun(String id, String slotKey, String mailboxKey, String status, String actorId, Instant now);
  MailResumeIngestRun findRunBySlot(String slotKey);
  MailResumeIngestRun findRun(String id);
  int markRunRunning(String id, String status, String actorId, Instant now);
  int completeRun(String id, int scanned, int attachments, int matched, int reviews,
      int duplicates, int failed, String errorCode, String errorDetail,
      String actorId, Instant now, String status);
  int failRun(String id, String status, String code, String detail, String actorId, Instant now);
}
