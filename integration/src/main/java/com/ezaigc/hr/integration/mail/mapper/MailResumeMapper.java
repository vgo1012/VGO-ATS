package com.ezaigc.hr.integration.mail.mapper;

import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.integration.mail.domain.MailResumeIngestRun;
import com.ezaigc.hr.integration.mail.domain.MailResumeRecord;
import com.ezaigc.hr.integration.mail.domain.MailboxCursor;
import com.ezaigc.hr.integration.mail.service.MailResumeStore;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MailResumeMapper extends MailResumeStore {

  @Select("""
      SELECT mailbox_key,folder,uid_validity,last_uid,initialized_at,last_success_at,
             ext,remark,create_id,create_name,create_time,update_id,update_name,update_time,version
      FROM talent_mailbox_cursor WHERE mailbox_key=#{mailboxKey}
      """)
  MailboxCursor findCursor(String mailboxKey);

  @Insert("""
      INSERT IGNORE INTO talent_mailbox_cursor(
        mailbox_key,folder,uid_validity,last_uid,initialized_at,last_success_at,version,
        create_id,create_time,update_id,update_time)
      VALUES(#{mailboxKey},#{folder},#{uidValidity},#{lastUid},#{now},#{now},0,
        #{actorId},#{now},#{actorId},#{now})
      """)
  int insertCursor(String mailboxKey, String folder, long uidValidity, long lastUid,
      String actorId, Instant now);

  @Update("""
      UPDATE talent_mailbox_cursor
      SET last_uid=#{newUid},last_success_at=#{now},version=version+1,update_id=#{actorId},update_time=#{now}
      WHERE mailbox_key=#{mailboxKey} AND uid_validity=#{uidValidity} AND last_uid=#{expectedUid}
      """)
  int advanceCursor(String mailboxKey, long uidValidity, long expectedUid, long newUid,
      String actorId, Instant now);

  @Select("""
      SELECT id,mailbox_key,folder,source_channel_id,uid_validity,mail_uid,message_id_hash,
             sender_email,subject,received_at,attachment_name,media_type,size_bytes,content_sha256,
             object_key,parsed_jd_code,parsed_jd_title,parsed_region,match_type,status,failure_code,
             failure_detail,jd_id,jd_version_id,owner_user_id,owner_department_id,intake_id,
             linked_by_user_id,linked_at,link_reason,ext,remark,create_id,create_name,create_time,
             update_id,update_name,update_time,version
      FROM talent_mail_resume
      WHERE mailbox_key=#{mailboxKey} AND folder=#{folder}
        AND uid_validity=#{uidValidity} AND mail_uid=#{mailUid}
      """)
  MailResumeRecord findByUid(String mailboxKey, String folder, long uidValidity, long mailUid);

  @Select("""
      SELECT COUNT(*) FROM talent_mail_resume
      WHERE message_id_hash=#{messageIdHash} OR content_sha256=#{contentHash}
      """)
  int countDuplicateContent(String messageIdHash, String contentHash);

  @Insert("""
      INSERT INTO talent_mail_resume(
        id,mailbox_key,folder,source_channel_id,uid_validity,mail_uid,message_id_hash,sender_email,subject,received_at,
        attachment_name,media_type,size_bytes,content_sha256,object_key,parsed_jd_code,parsed_jd_title,
        parsed_region,match_type,status,failure_code,failure_detail,jd_id,jd_version_id,owner_user_id,
        owner_department_id,version,create_id,create_time,update_id,update_time)
      VALUES(#{id},#{mailboxKey},#{folder},#{sourceChannelId},#{uidValidity},#{mailUid},#{messageIdHash},#{senderEmail},
        #{subject},#{receivedAt},#{attachmentName},#{mediaType},#{sizeBytes},#{contentSha256},#{objectKey},
        #{parsedJdCode},#{parsedJdTitle},#{parsedRegion},#{matchType},#{status},#{failureCode},#{failureDetail},
        #{jdId},#{jdVersionId},#{ownerUserId},#{ownerDepartmentId},0,#{createId},#{createTime},#{updateId},#{updateTime})
      """)
  int insertMessage(MailResumeRecord record);

  @SpecialSql("按稳定收件时间和 ID 游标读取待人工校验邮件")
  @Select("""
      <script>
      SELECT id,mailbox_key,folder,source_channel_id,uid_validity,mail_uid,message_id_hash,
             sender_email,subject,received_at,attachment_name,media_type,size_bytes,content_sha256,
             object_key,parsed_jd_code,parsed_jd_title,parsed_region,match_type,status,failure_code,
             failure_detail,jd_id,jd_version_id,owner_user_id,owner_department_id,intake_id,
             linked_by_user_id,linked_at,link_reason,ext,remark,create_id,create_name,create_time,
             update_id,update_name,update_time,version
      FROM talent_mail_resume
      WHERE status IN (#{reviewStatus},#{dispatchFailedStatus})
      <if test='from != null'>AND received_at &gt;= #{from}</if>
      <if test='to != null'>AND received_at &lt; #{to}</if>
      <if test='title != null'>AND BINARY parsed_jd_title = BINARY #{title}</if>
      <if test='region != null'>AND BINARY parsed_region = BINARY #{region}</if>
      <if test='attachment != null'>AND attachment_name LIKE CONCAT('%',#{attachment},'%')</if>
      <if test='cursorTime != null'>
        AND (received_at &lt; #{cursorTime} OR (received_at=#{cursorTime} AND id &lt; #{cursorId}))
      </if>
      ORDER BY received_at DESC,id DESC LIMIT #{limit}
      </script>
      """)
  List<MailResumeRecord> findReviews(
      @Param("reviewStatus") String reviewStatus,
      @Param("dispatchFailedStatus") String dispatchFailedStatus,
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("title") String title,
      @Param("region") String region,
      @Param("attachment") String attachment,
      @Param("cursorTime") Instant cursorTime,
      @Param("cursorId") String cursorId,
      @Param("limit") int limit);

  @SpecialSql("待人工校验邮件总数使用与列表相同的状态和筛选条件")
  @Select("""
      <script>
      SELECT COUNT(*) FROM talent_mail_resume
      WHERE status IN (#{reviewStatus},#{dispatchFailedStatus})
      <if test='from != null'>AND received_at &gt;= #{from}</if>
      <if test='to != null'>AND received_at &lt; #{to}</if>
      <if test='title != null'>AND BINARY parsed_jd_title = BINARY #{title}</if>
      <if test='region != null'>AND BINARY parsed_region = BINARY #{region}</if>
      <if test='attachment != null'>AND attachment_name LIKE CONCAT('%',#{attachment},'%')</if>
      </script>
      """)
  long countReviews(
      @Param("reviewStatus") String reviewStatus,
      @Param("dispatchFailedStatus") String dispatchFailedStatus,
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("title") String title,
      @Param("region") String region,
      @Param("attachment") String attachment);

  @Select("""
      SELECT id,mailbox_key,folder,source_channel_id,uid_validity,mail_uid,message_id_hash,
             sender_email,subject,received_at,attachment_name,media_type,size_bytes,content_sha256,
             object_key,parsed_jd_code,parsed_jd_title,parsed_region,match_type,status,failure_code,
             failure_detail,jd_id,jd_version_id,owner_user_id,owner_department_id,intake_id,
             linked_by_user_id,linked_at,link_reason,ext,remark,create_id,create_name,create_time,
             update_id,update_name,update_time,version
      FROM talent_mail_resume WHERE id=#{id}
      """)
  MailResumeRecord findMessage(String id);

  @SpecialSql("人工关联时锁定邮件记录，保证 JD 只绑定一次")
  @Select("""
      SELECT id,mailbox_key,folder,source_channel_id,uid_validity,mail_uid,message_id_hash,
             sender_email,subject,received_at,attachment_name,media_type,size_bytes,content_sha256,
             object_key,parsed_jd_code,parsed_jd_title,parsed_region,match_type,status,failure_code,
             failure_detail,jd_id,jd_version_id,owner_user_id,owner_department_id,intake_id,
             linked_by_user_id,linked_at,link_reason,ext,remark,create_id,create_name,create_time,
             update_id,update_name,update_time,version
      FROM talent_mail_resume WHERE id=#{id} FOR UPDATE
      """)
  MailResumeRecord findMessageForUpdate(String id);

  @Update("""
      UPDATE talent_mail_resume
      SET status=#{matchedStatus},failure_code=NULL,failure_detail=NULL,
        jd_id=#{jdId},jd_version_id=#{jdVersionId},owner_user_id=#{ownerUserId},
        owner_department_id=#{ownerDepartmentId},linked_by_user_id=#{actorId},linked_at=#{now},
        link_reason=#{reason},version=version+1,update_id=#{actorId},update_time=#{now}
      WHERE id=#{id} AND status=#{reviewStatus} AND version=#{version}
      """)
  int linkMessage(String id, long version, String matchedStatus, String reviewStatus, String jdId,
      String jdVersionId, String ownerUserId, String ownerDepartmentId, String actorId,
      String reason, Instant now);

  @Update("""
      UPDATE talent_mail_resume
      SET status=#{dispatchedStatus},intake_id=#{intakeId},failure_code=NULL,failure_detail=NULL,
        version=version+1,update_id=#{actorId},update_time=#{now}
      WHERE id=#{id} AND status IN (#{matchedStatus},#{dispatchFailedStatus})
      """)
  int markDispatched(String id, String intakeId, String dispatchedStatus, String matchedStatus,
      String dispatchFailedStatus, String actorId, Instant now);

  @Update("""
      UPDATE talent_mail_resume
      SET status=#{dispatchFailedStatus},failure_code=#{code},failure_detail=#{detail},
        version=version+1,update_id=#{actorId},update_time=#{now}
      WHERE id=#{id} AND status IN (#{matchedStatus},#{dispatchFailedStatus})
      """)
  int markDispatchFailed(String id, String dispatchFailedStatus, String matchedStatus,
      String code, String detail, String actorId, Instant now);

  @Select("""
      SELECT id,mailbox_key,folder,source_channel_id,uid_validity,mail_uid,message_id_hash,
             sender_email,subject,received_at,attachment_name,media_type,size_bytes,content_sha256,
             object_key,parsed_jd_code,parsed_jd_title,parsed_region,match_type,status,failure_code,
             failure_detail,jd_id,jd_version_id,owner_user_id,owner_department_id,intake_id,
             linked_by_user_id,linked_at,link_reason,ext,remark,create_id,create_name,create_time,
             update_id,update_name,update_time,version
      FROM talent_mail_resume
      WHERE status IN (#{matchedStatus},#{dispatchFailedStatus})
      ORDER BY update_time,id LIMIT #{limit}
      """)
  List<MailResumeRecord> findDispatchable(String matchedStatus, String dispatchFailedStatus, int limit);

  @Select("""
      SELECT jd_id FROM talent_mail_route_rule
      WHERE mailbox_key=#{mailboxKey} AND BINARY normalized_title=BINARY #{title}
        AND BINARY normalized_region=BINARY #{region} AND active_marker='Y'
      """)
  String findRule(String mailboxKey, String title, String region);

  @Insert("""
      INSERT IGNORE INTO talent_mail_route_rule(
        id,mailbox_key,normalized_title,normalized_region,jd_id,status,active_marker,version,
        create_id,create_time,update_id,update_time)
      VALUES(#{id},#{mailboxKey},#{title},#{region},#{jdId},#{status},'Y',0,
        #{actorId},#{now},#{actorId},#{now})
      """)
  int insertRule(String id, String mailboxKey, String title, String region, String jdId,
      String status, String actorId, Instant now);

  @Insert("""
      INSERT IGNORE INTO talent_mail_ingest_run(
        id,slot_key,mailbox_key,status,scanned_count,attachment_count,auto_matched_count,
        review_required_count,duplicate_count,failed_count,version,create_id,create_time,update_id,update_time)
      VALUES(#{id},#{slotKey},#{mailboxKey},#{status},0,0,0,0,0,0,0,#{actorId},#{now},#{actorId},#{now})
      """)
  int insertRun(String id, String slotKey, String mailboxKey, String status, String actorId, Instant now);

  @Select("""
      SELECT id,slot_key,mailbox_key,status,scanned_count,attachment_count,auto_matched_count,
             review_required_count,duplicate_count,failed_count,recent_error_code,recent_error_detail,
             started_at,finished_at,ext,remark,create_id,create_name,create_time,update_id,update_name,
             update_time,version
      FROM talent_mail_ingest_run WHERE slot_key=#{slotKey}
      """)
  MailResumeIngestRun findRunBySlot(String slotKey);

  @Select("""
      SELECT id,slot_key,mailbox_key,status,scanned_count,attachment_count,auto_matched_count,
             review_required_count,duplicate_count,failed_count,recent_error_code,recent_error_detail,
             started_at,finished_at,ext,remark,create_id,create_name,create_time,update_id,update_name,
             update_time,version
      FROM talent_mail_ingest_run WHERE id=#{id}
      """)
  MailResumeIngestRun findRun(String id);

  @Update("""
      UPDATE talent_mail_ingest_run
      SET status=#{status},started_at=COALESCE(started_at,#{now}),recent_error_code=NULL,
        recent_error_detail=NULL,version=version+1,update_id=#{actorId},update_time=#{now}
      WHERE id=#{id}
      """)
  int markRunRunning(String id, String status, String actorId, Instant now);

  @Update("""
      UPDATE talent_mail_ingest_run
      SET status=#{status},scanned_count=#{scanned},attachment_count=#{attachments},
        auto_matched_count=#{matched},review_required_count=#{reviews},duplicate_count=#{duplicates},
        failed_count=#{failed},recent_error_code=#{errorCode},recent_error_detail=#{errorDetail},
        finished_at=#{now},version=version+1,update_id=#{actorId},update_time=#{now}
      WHERE id=#{id}
      """)
  int completeRun(String id, int scanned, int attachments, int matched, int reviews,
      int duplicates, int failed, String errorCode, String errorDetail,
      String actorId, Instant now, String status);

  @Update("""
      UPDATE talent_mail_ingest_run
      SET status=#{status},recent_error_code=#{code},recent_error_detail=#{detail},finished_at=#{now},
        version=version+1,update_id=#{actorId},update_time=#{now}
      WHERE id=#{id}
      """)
  int failRun(String id, String status, String code, String detail, String actorId, Instant now);
}
