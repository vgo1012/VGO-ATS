package com.ezaigc.hr.recruiting.demand.api;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Value;
import lombok.experimental.Accessors;

/** 邮件简历只读路由边界；只提供当前有效 JD 的精确匹配事实。 */
public interface MailJdRoutingService {

  List<Target> activeTargets();

  Target requireCurrent(String jdId);

  static String normalize(String value) {
    if (value == null) return null;
    String normalized = WHITESPACE.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC).strip())
        .replaceAll(" ");
    return normalized.isEmpty() ? null : normalized;
  }

  Pattern WHITESPACE = Pattern.compile("\\s+");

  @Value
  @Accessors(fluent = true)
  class Target {
    String jdId;
    String jdVersionId;
    String code;
    String title;
    int versionNo;
    String ownerUserId;
    String ownerDepartmentId;
    List<String> regions;
    boolean ownerValid;
  }
}
