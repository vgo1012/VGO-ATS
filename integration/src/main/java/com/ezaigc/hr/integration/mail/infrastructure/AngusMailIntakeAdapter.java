package com.ezaigc.hr.integration.mail.infrastructure;

import static com.ezaigc.hr.talent.api.ResumeUploadPolicy.MAX_FILE_SIZE_BYTES;

import com.ezaigc.hr.integration.config.MailResumeProperties;
import com.ezaigc.hr.integration.mail.application.provider.MailIntakePort;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.internet.ParseException;
import jakarta.annotation.Resource;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.eclipse.angus.mail.util.DecodingException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hr.mail-resume.enabled", havingValue = "true")
public class AngusMailIntakeAdapter implements MailIntakePort {

  @Resource private MailResumeProperties configuration;

  @Override
  public Scan scan(Long afterUid, int limit) {
    Properties properties = new Properties();
    properties.setProperty("mail.store.protocol", "imaps");
    properties.setProperty("mail.imaps.peek", "true");
    properties.setProperty("mail.imaps.connectiontimeout", millis(configuration.getConnectTimeout()));
    properties.setProperty("mail.imaps.timeout", millis(configuration.getReadTimeout()));
    properties.setProperty("mail.imaps.writetimeout", millis(configuration.getWriteTimeout()));
    try (Store store = Session.getInstance(properties).getStore("imaps")) {
      store.connect(configuration.getHost(), configuration.getPort(), configuration.getUsername(), configuration.getPassword());
      try (Folder folder = store.getFolder(configuration.getFolder())) {
        folder.open(Folder.READ_ONLY);
        if (!(folder instanceof UIDFolder uidFolder)) {
          throw new IllegalStateException("邮箱服务器不支持 IMAP UID");
        }
        return scanFolder(folder, uidFolder, afterUid, limit);
      }
    } catch (Exception exception) {
      throw new IllegalStateException("公共邮箱读取失败", exception);
    }
  }

  Scan scanFolder(Folder folder, UIDFolder uidFolder, Long afterUid, int limit) throws Exception {
    if (limit < 1 || afterUid != null && afterUid < 0) {
      throw new IllegalArgumentException("邮件扫描需要正数批量与非负UID游标");
    }
    int count = folder.getMessageCount();
    long highestUid = count == 0 ? 0 : uidFolder.getUID(folder.getMessage(count));
    if (afterUid == null || afterUid >= highestUid) {
      return new Scan(uidFolder.getUIDValidity(), highestUid, List.of());
    }
    // UID区间本身有界，避免先把整份积压元数据取回再在本机limit。
    long rangeEnd = afterUid + Math.min(highestUid - afterUid, Math.min(limit, 200));
    Message[] fetched = uidFolder.getMessagesByUID(afterUid + 1, rangeEnd);
    List<Message> ordered = Arrays.stream(fetched)
        .filter(message -> message != null)
        .sorted(Comparator.comparingLong(message -> uid(uidFolder, message)))
        // 一封邮件提交后再读取下一封，避免扫描批次同时保留多份最大附件。
        .limit(1)
        .toList();
    List<MailIntakePort.MailMessage> messages = new ArrayList<>(ordered.size());
    for (Message message : ordered) {
      long messageUid = uidFolder.getUID(message);
      try {
        messages.add(read(messageUid, message));
      } catch (ParseException | UnsupportedEncodingException | DecodingException exception) {
        // 仅隔离已确定的格式错误；连接/服务读失败仍中断扫描并保留游标以供重试。
        messages.add(new MailMessage(messageUid, null, null, null, Instant.now(),
            List.of(), "MAIL_MESSAGE_UNREADABLE"));
      }
    }
    long covered = messages.isEmpty() ? rangeEnd : messages.getLast().uid();
    return new Scan(uidFolder.getUIDValidity(), highestUid, List.copyOf(messages), covered);
  }

  private MailIntakePort.MailMessage read(long uid, Message message) throws Exception {
    List<Part> parts = new ArrayList<>();
    collectAttachments(message, parts, 0);
    List<Attachment> attachments = new ArrayList<>(parts.size());
    for (Part part : parts) {
      String fileName = MimeUtility.decodeText(part.getFileName()).strip();
      byte[] content = new byte[0];
      // 多附件只需要元数据即可拒绝，不读取两份无效载荷。
      if (parts.size() == 1) {
        try (InputStream input = part.getInputStream()) {
          content = input.readNBytes(Math.toIntExact(MAX_FILE_SIZE_BYTES + 1));
        }
      }
      attachments.add(new Attachment(fileName, content));
    }
    String[] ids = message.getHeader("Message-ID");
    return new MailIntakePort.MailMessage(
        uid,
        ids == null || ids.length == 0 ? null : ids[0],
        sender(message.getFrom()),
        message.getSubject(),
        message.getReceivedDate() == null ? Instant.now() : message.getReceivedDate().toInstant(),
        List.copyOf(attachments));
  }

  private void collectAttachments(Part part, List<Part> result, int depth) throws Exception {
    if (result.size() >= 2 || depth > 20) return;
    if (part.isMimeType("multipart/*")) {
      Multipart multipart = (Multipart) part.getContent();
      for (int index = 0; index < multipart.getCount() && result.size() < 2; index++) {
        collectAttachments(multipart.getBodyPart(index), result, depth + 1);
      }
      return;
    }
    String rawName = part.getFileName();
    if (rawName == null) return;
    String fileName = MimeUtility.decodeText(rawName).strip();
    if (!supported(fileName)) return;
    result.add(part);
  }

  private static boolean supported(String fileName) {
    String lower = fileName.toLowerCase(Locale.ROOT);
    return lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx");
  }

  private static String sender(Address[] addresses) {
    if (addresses == null || addresses.length == 0) return null;
    Address address = addresses[0];
    return address instanceof InternetAddress internet ? internet.getAddress() : address.toString();
  }

  private static String millis(Duration value) {
    return Long.toString(value.toMillis());
  }

  private static long uid(UIDFolder folder, Message message) {
    try {
      return folder.getUID(message);
    } catch (Exception exception) {
      throw new IllegalStateException("邮件 UID 读取失败", exception);
    }
  }
}
