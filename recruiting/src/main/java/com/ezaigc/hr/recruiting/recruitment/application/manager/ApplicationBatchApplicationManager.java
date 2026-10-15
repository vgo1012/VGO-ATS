package com.ezaigc.hr.recruiting.recruitment.application.manager;

import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.CreateApplicationDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationBatchCreateDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationBatchItemResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationBatchResponse;
import com.ezaigc.hr.recruiting.recruitment.enums.ApplicationBatchItemStatus;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 批次不持有事务；每个候选人通过现有代理方法独立提交，不依赖 AI 筛选结果。 */
@Slf4j
@Component
public class ApplicationBatchApplicationManager {
  private static final String ACTIVE_EXISTS = "ACTIVE_APPLICATION_EXISTS";
  @Resource private RecruitmentApplicationManager applications;
  @Resource private ApplicationPersistenceService persistence;
  @Resource private DemandQueryService demands;

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ApplicationBatchResponse create(ApplicationBatchCreateDTO batch, String key) {
    List<CreateApplicationDTO> commands = batch.getItems();
    var results = new ArrayList<ApplicationBatchItemResponse>(commands.size());
    for (var command : commands) {
      // 子键按候选人稳定生成，超时重试或只重试失败项时保持原键，不制造第二个活动。
      String itemKey = Sha256Utils.hashUtf8(key + ":" + command.getCandidateId());
      try {
        String id = applications.createApplicationWithPath(command, batch.getSelectedStepCodes(), itemKey).getApplication().getId();
        results.add(result(command, id, ApplicationBatchItemStatus.SUCCEEDED, null, "已推荐到招聘流程"));
      } catch (PlatformException failure) {
        if (ACTIVE_EXISTS.equals(failure.code())) {
          try {
            var jd = demands.requireJdVersion(command.getJdVersionId());
            var existing = persistence.findActiveForCandidateAndJd(command.getCandidateId(), jd.getJdId());
            if (existing != null && applications.canProcessJdVersion(command.getJdVersionId())) {
              String visibleId = applications.applicationResponse(existing.getId()).getApplication().getId();
              results.add(result(command, visibleId, ApplicationBatchItemStatus.ALREADY_EXISTS, null, "已在招聘流程"));
              continue;
            }
          } catch (PlatformException accessFailure) {
            failure = accessFailure;
          } catch (RuntimeException lookupFailure) {
            log.error("批量招聘重复活动核验失败 candidateId={}", command.getCandidateId(), SafeLogException.from(lookupFailure));
            results.add(result(command, null, ApplicationBatchItemStatus.FAILED,
                "APPLICATION_BATCH_ITEM_FAILED", "请重试此候选人，已创建记录不会重复生成"));
            continue;
          }
        }
        results.add(result(command, null, ApplicationBatchItemStatus.FAILED, failure.code(), failure.getMessage()));
      } catch (RuntimeException failure) {
        log.error("批量创建招聘活动单项失败 candidateId={}", command.getCandidateId(), SafeLogException.from(failure));
        results.add(result(command, null, ApplicationBatchItemStatus.FAILED,
            "APPLICATION_BATCH_ITEM_FAILED", "请重试此候选人，已创建记录不会重复生成"));
      }
    }
    return new ApplicationBatchResponse(List.copyOf(results));
  }

  private static ApplicationBatchItemResponse result(CreateApplicationDTO command, String id,
      ApplicationBatchItemStatus status, String code, String message) {
    return new ApplicationBatchItemResponse(command.getCandidateId(), command.getResumeVersionId(),
        id, status, code, message);
  }
}
