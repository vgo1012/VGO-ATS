package com.ezaigc.hr.integration.mail.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AngusMailIntakeAdapterContractTest {

  @Test
  void readsBoundedUidWindowsWithoutChangingMailboxFlags() throws IOException {
    String source = Files.readString(Path.of(
        "src/main/java/com/ezaigc/hr/integration/mail/infrastructure/AngusMailIntakeAdapter.java"));

    assertThat(source)
        .contains("mail.imaps.peek\", \"true", "folder.open(Folder.READ_ONLY)",
            "getMessagesByUID(afterUid + 1, rangeEnd)")
        .doesNotContain("UIDFolder.LASTUID")
        .doesNotContain("Folder.READ_WRITE", "Flag.SEEN", "setFlag(", "setFlags(", ".search(");
  }
}
