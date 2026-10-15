package com.ezaigc.hr.recruiting.demand.application.adapter;

import static com.ezaigc.hr.recruiting.demand.enums.JdStatus.ACTIVE;
import static com.ezaigc.hr.recruiting.demand.enums.JdVersionStatus.PUBLISHED;

import com.ezaigc.hr.common.infrastructure.json.JsonNodeValues;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.IdentityDirectoryService;
import com.ezaigc.hr.recruiting.demand.api.MailJdRoutingService;
import com.ezaigc.hr.recruiting.demand.domain.entity.MailJdRoutingRow;
import com.ezaigc.hr.recruiting.demand.service.MailJdRoutingStore;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class MailJdRoutingAdapter implements MailJdRoutingService {

  @Resource private MailJdRoutingStore mapper;
  @Resource private IdentityDirectoryService identities;
  @Resource private ObjectMapper json;

  @Override
  @Transactional(readOnly = true)
  public List<Target> activeTargets() {
    // ponytail: 当前活跃 JD 全量通常很小；超过 5000 条后再增加规范化路由投影表。
    Map<String, Boolean> ownerValidity = new HashMap<>();
    return mapper.findActiveTargets(PUBLISHED.getCode(), ACTIVE.getCode()).stream()
        .map(row -> target(row, ownerValidity.computeIfAbsent(row.getOwnerUserId(), this::validOwner)))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Target requireCurrent(String jdId) {
    if (jdId == null || jdId.isBlank()) {
      throw PlatformExceptions.unprocessable("MAIL_JD_REQUIRED", "请选择当前 JD");
    }
    MailJdRoutingRow row = mapper.findCurrentTargetForShare(
        jdId, PUBLISHED.getCode(), ACTIVE.getCode());
    return Optional.ofNullable(row)
        .map(value -> target(value, validOwner(value.getOwnerUserId())))
        .filter(Target::ownerValid)
        .orElseThrow(() -> PlatformExceptions.conflict(
            "MAIL_JD_NOT_ROUTABLE", "JD 已关闭、当前版本不可用或负责人无效"));
  }

  private Target target(MailJdRoutingRow row, boolean ownerValid) {
    return new Target(
        row.getJdId(),
        row.getJdVersionId(),
        row.getCode(),
        MailJdRoutingService.normalize(row.getTitle()),
        row.getVersionNo(),
        row.getOwnerUserId(),
        row.getOwnerDepartmentId(), regions(row),
        ownerValid && row.getOwnerDepartmentId() != null);
  }

  private boolean validOwner(String ownerUserId) {
    return ownerUserId != null && identities.isRecruiterOwner(ownerUserId);
  }

  private List<String> regions(MailJdRoutingRow row) {
    String selectedRegion = MailJdRoutingService.normalize(row.getWorkRegionName());
    if (selectedRegion != null) return List.of(selectedRegion);
    String factsJson = row.getFactsJson();
    if (factsJson == null || factsJson.isBlank()) return List.of();
    try {
      return JsonNodeValues.strings(json.readTree(factsJson).get("fulfillmentConditions"), "locations")
          .stream().map(MailJdRoutingService::normalize).filter(value -> value != null).distinct().toList();
    } catch (Exception exception) {
      throw new IllegalStateException("当前 JD 区域事实无法读取", exception);
    }
  }
}
