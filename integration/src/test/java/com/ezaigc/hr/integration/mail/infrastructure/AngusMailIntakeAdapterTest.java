package com.ezaigc.hr.integration.mail.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.integration.mail.application.provider.MailIntakePort;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.ParseException;
import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AngusMailIntakeAdapterTest {

  @Test
  void backlogFetchUsesABoundedUidIntervalInsteadOfTheEntireMailbox() throws Exception {
    Message highest = mock(Message.class);
    Folder folder = mock(Folder.class);
    UIDFolder uids = mock(UIDFolder.class);
    when(folder.getMessageCount()).thenReturn(100_000);
    when(folder.getMessage(100_000)).thenReturn(highest);
    when(uids.getUID(highest)).thenReturn(100_000L);
    when(uids.getMessagesByUID(anyLong(), anyLong())).thenReturn(new Message[0]);
    when(uids.getUIDValidity()).thenReturn(7L);

    var scan = new AngusMailIntakeAdapter().scanFolder(folder, uids, 100L, 20);

    verify(uids).getMessagesByUID(101L, 120L);
    assertThat(scan.highestUid()).isEqualTo(100_000L);
    assertThat(scan.messages()).isEmpty();
    assertThat(scan.scannedThroughUid()).isEqualTo(120L);
    verify(uids, never()).getMessagesByUID(anyLong(), eq(UIDFolder.LASTUID));
  }

  @Test
  void boundedIntervalNeverWrapsAtTheUidUpperEdge() throws Exception {
    Message highest = mock(Message.class);
    Folder folder = mock(Folder.class);
    UIDFolder uids = mock(UIDFolder.class);
    when(folder.getMessageCount()).thenReturn(1);
    when(folder.getMessage(1)).thenReturn(highest);
    when(uids.getUID(highest)).thenReturn(Long.MAX_VALUE);
    when(uids.getMessagesByUID(anyLong(), anyLong())).thenReturn(new Message[0]);
    new AngusMailIntakeAdapter().scanFolder(folder, uids, Long.MAX_VALUE - 5, 200);
    verify(uids).getMessagesByUID(Long.MAX_VALUE - 4, Long.MAX_VALUE);
  }

  @Test
  void malformedAttachmentCharsetDoesNotBlockItsUid() throws Exception {
    MimeMessage message = message("=?x-invalid-charset?B?cmVzdW1lLnBkZg==?=");
    var scan = scanSingle(message);
    assertThat(scan.messages()).singleElement().satisfies(value -> {
      assertThat(value.uid()).isEqualTo(1L);
      assertThat(value.resumeAttachments()).isEmpty();
      assertThat(value.readFailureCode()).isEqualTo("MAIL_MESSAGE_UNREADABLE");
    });
  }

  @Test
  void malformedMultipartDoesNotBlockItsUid() throws Exception {
    MimeMessage message = mock(MimeMessage.class);
    when(message.isMimeType("multipart/*")).thenReturn(true);
    when(message.getContent()).thenThrow(new ParseException("synthetic malformed MIME"));
    assertThat(scanSingle(message).messages()).singleElement()
        .satisfies(value -> {
          assertThat(value.uid()).isEqualTo(1L);
          assertThat(value.readFailureCode()).isEqualTo("MAIL_MESSAGE_UNREADABLE");
        });
  }

  @Test
  void transientMailboxFailureIsNotRecordedAsBadMail() throws Exception {
    MimeMessage message = mock(MimeMessage.class);
    when(message.isMimeType("multipart/*")).thenThrow(new MessagingException("temporary server failure"));
    assertThatThrownBy(() -> scanSingle(message)).isInstanceOf(MessagingException.class);
  }

  private static MailIntakePort.Scan scanSingle(Message message) throws Exception {
    Folder folder = mock(Folder.class);
    UIDFolder uids = mock(UIDFolder.class);
    when(folder.getMessageCount()).thenReturn(1);
    when(folder.getMessage(1)).thenReturn(message);
    when(uids.getUID(message)).thenReturn(1L);
    when(uids.getUIDValidity()).thenReturn(7L);
    when(uids.getMessagesByUID(anyLong(), anyLong())).thenReturn(new Message[] {message});
    return new AngusMailIntakeAdapter().scanFolder(folder, uids, 0L, 20);
  }

  @Test
  void eachScanRetainsAtMostOneMessagePayloadEvenWhenTheCallerRequestsALargeBatch() throws Exception {
    var first = message("first.pdf");
    var second = message("second.pdf");
    Folder folder = mock(Folder.class);
    UIDFolder uids = mock(UIDFolder.class);
    when(folder.getMessageCount()).thenReturn(2);
    when(folder.getMessage(2)).thenReturn(second);
    when(uids.getUID(first)).thenReturn(1L);
    when(uids.getUID(second)).thenReturn(2L);
    when(uids.getUIDValidity()).thenReturn(7L);
    when(uids.getMessagesByUID(anyLong(), anyLong())).thenReturn(new Message[] {first, second});

    var scan = new AngusMailIntakeAdapter().scanFolder(folder, uids, 0L, 20);

    assertThat(scan.messages()).hasSize(1);
    assertThat(scan.messages().getFirst().uid()).isEqualTo(1L);
    assertThat(scan.messages().getFirst().resumeAttachments()).singleElement()
        .satisfies(attachment -> assertThat(attachment.content()).isNotEmpty());
    assertThat(scan.highestUid()).isEqualTo(2L);
  }

  @Test
  void multipleAttachmentRejectionDoesNotDecodeEitherPayload() throws Exception {
    MimeBodyPart first = spy(attachment("first.pdf"));
    MimeBodyPart second = spy(attachment("second.pdf"));
    doThrow(new IOException("payload must not be decoded for a multiple-attachment rejection"))
        .when(first).getInputStream();
    doThrow(new IOException("payload must not be decoded for a multiple-attachment rejection"))
        .when(second).getInputStream();
    MimeMultipart multipart = new MimeMultipart();
    multipart.addBodyPart(first);
    multipart.addBodyPart(second);
    MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
    message.setContent(multipart);
    message.saveChanges();

    MailIntakePort.MailMessage result = ReflectionTestUtils.invokeMethod(new AngusMailIntakeAdapter(), "read", 1L, message);

    assertThat(result.resumeAttachments()).hasSize(2)
        .allSatisfy(attachment -> assertThat(attachment.content()).isEmpty());
    verify(first, never()).getInputStream();
    verify(second, never()).getInputStream();
  }

  private static MimeMessage message(String name) throws Exception {
    MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
    MimeMultipart multipart = new MimeMultipart();
    multipart.addBodyPart(attachment(name));
    message.setContent(multipart);
    message.setSubject("synthetic resume");
    message.saveChanges();
    return message;
  }

  private static MimeBodyPart attachment(String name) throws Exception {
    MimeBodyPart part = new MimeBodyPart();
    part.setText("%PDF-1.7\nsynthetic payload");
    part.setFileName(name);
    return part;
  }
}
