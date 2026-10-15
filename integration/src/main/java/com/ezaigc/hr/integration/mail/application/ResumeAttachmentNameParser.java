package com.ezaigc.hr.integration.mail.application;

import com.ezaigc.hr.recruiting.demand.api.MailJdRoutingService;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import lombok.experimental.Accessors;

public final class ResumeAttachmentNameParser {

  private static final Pattern FILE = Pattern.compile("^【(.+)】.+\\.(?i:pdf|doc|docx)$");
  private static final Pattern JD_CODE = Pattern.compile("JD-(?:[0-9]{6,}|[0-9A-HJKMNP-TV-Z]{26})");

  // 忽略编号左侧的业务前缀，保留完整字母数字尾部，避免把未知编号截成已有短号。
  private static final Pattern CODE_TOKEN = Pattern.compile("JD-[A-Za-z0-9]*");

  private ResumeAttachmentNameParser() {}

  public static Parsed parse(String fileName) {
    return parse(fileName, null);
  }

  public static Parsed parse(String fileName, String subject) {
    Set<String> codes = new LinkedHashSet<>();
    extractCodes(fileName, codes);
    extractCodes(subject, codes);
    List<String> jdCodes = List.copyOf(codes);
    Matcher matcher = FILE.matcher(fileName == null ? "" : fileName);
    if (!matcher.matches()) return new Parsed(jdCodes, null, null);
    String[] fields = matcher.group(1).split("_", -1);
    String first = MailJdRoutingService.normalize(fields[0]);
    int start = fields.length >= 4 && first != null && JD_CODE.matcher(first).matches() ? 1 : 0;
    if (fields.length - start < 3) return new Parsed(jdCodes, null, null);
    String title = String.join("_", Arrays.copyOfRange(fields, start, fields.length - 2));
    return new Parsed(jdCodes, bounded(title), bounded(fields[fields.length - 2]));
  }

  private static void extractCodes(String text, Set<String> codes) {
    String normalized = MailJdRoutingService.normalize(text);
    if (normalized == null) return;
    Matcher matcher = CODE_TOKEN.matcher(normalized);
    while (matcher.find()) codes.add(matcher.group());
  }

  private static String bounded(String value) {
    String normalized = MailJdRoutingService.normalize(value);
    return normalized != null && normalized.codePointCount(0, normalized.length()) <= 180
        ? normalized : null;
  }

  @Value
  @Accessors(fluent = true)
  public static class Parsed {
    List<String> jdCodes;
    String title;
    String region;

    public String jdCode() {
      String first = jdCodes.isEmpty() ? null : jdCodes.getFirst();
      return first != null && first.length() <= 64 && JD_CODE.matcher(first).matches() ? first : null;
    }
  }
}
