package com.ezaigc.hr.integration.mail.application;

import static com.ezaigc.hr.integration.mail.enums.MailResumeStatus.REVIEW_REQUIRED;

import com.ezaigc.hr.integration.mail.application.ResumeAttachmentNameParser.Parsed;
import com.ezaigc.hr.recruiting.demand.api.MailJdRoutingService;
import com.ezaigc.hr.recruiting.demand.api.MailJdRoutingService.Target;
import java.util.List;
import lombok.Value;
import lombok.experimental.Accessors;

public final class MailResumeMatcher {

  private MailResumeMatcher() {}

  public static Route match(Parsed parsed, List<Target> targets, String ruleJdId) {
    if (!parsed.jdCodes().isEmpty()) {
      Target target = null;
      for (String code : parsed.jdCodes()) {
        Target resolved = targets.stream()
            .filter(item -> code.equals(MailJdRoutingService.normalize(item.code())))
            .findFirst().orElse(null);
        if (resolved == null) return review("JD_CODE_NOT_FOUND", "JD 编号不存在或已关闭");
        if (target != null && !target.jdId().equals(resolved.jdId())) {
          return review("JD_CODE_CONFLICT", "邮件主题或附件包含指向不同岗位的 JD 编号");
        }
        target = resolved;
      }
      if (!target.ownerValid()) return review("JD_OWNER_INVALID", "JD 当前负责人无效");
      if (parsed.region() != null && !target.regions().contains(parsed.region())) {
        return review("JD_CODE_REGION_CONFLICT", "JD 编号与区域不一致");
      }
      return new Route("JD_CODE", target, null, null);
    }
    if (parsed.title() == null || parsed.region() == null) {
      return review("JD_FIELDS_MISSING", "附件名缺少可识别的岗位文本或区域");
    }
    List<Target> matches = targets.stream()
        .filter(target -> parsed.title().equals(target.title()) && target.regions().contains(parsed.region()))
        .toList();
    if (matches.size() > 1) return review("JD_EXACT_MATCH_AMBIGUOUS", "岗位文本和区域精确命中多个 JD");
    if (ruleJdId != null) {
      Target target = targets.stream().filter(item -> ruleJdId.equals(item.jdId())).findFirst().orElse(null);
      if (target == null) return review("CONFIRMED_RULE_JD_UNAVAILABLE", "已确认规则关联的 JD 当前不可用");
      if (!target.ownerValid()) return review("JD_OWNER_INVALID", "JD 当前负责人无效");
      return new Route("CONFIRMED_RULE", target, null, null);
    }
    if (matches.isEmpty()) return review("JD_EXACT_MATCH_NOT_FOUND", "岗位文本和区域未精确命中当前 JD");
    if (!matches.getFirst().ownerValid()) return review("JD_OWNER_INVALID", "JD 当前负责人无效");
    return new Route("EXACT_TITLE_REGION", matches.getFirst(), null, null);
  }

  private static Route review(String code, String detail) {
    return new Route(REVIEW_REQUIRED.getCode(), null, code, detail);
  }

  @Value
  @Accessors(fluent = true)
  public static class Route {
    String matchType;
    Target target;
    String failureCode;
    String failureDetail;
  }
}
