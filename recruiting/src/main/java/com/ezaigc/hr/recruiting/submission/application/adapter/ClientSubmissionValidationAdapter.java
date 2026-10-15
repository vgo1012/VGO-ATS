package com.ezaigc.hr.recruiting.submission.application.adapter;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientSubmission;
import com.ezaigc.hr.recruiting.submission.enums.SubmissionDeliveryStatus;
import com.ezaigc.hr.recruiting.submission.service.SubmissionPersistenceService;
import jakarta.annotation.Resource;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientSubmissionValidationAdapter {

  @Resource private SubmissionPersistenceService persistence;

  /** 校验并获取发送与匹配。 */
  @Transactional(readOnly = true)
  public void requireSentAndMatching(
      String submissionId,
      String applicationId,
      String candidateId,
      String clientId,
      String jdId,
      String jdVersionId,
      String resumeVersionId) {
    validate(persistence.find(submissionId), applicationId, candidateId, clientId, jdId, jdVersionId, resumeVersionId);
  }

  /** 甲方面试排期先锁推荐，再锁 Application，与推荐撤回使用相同锁顺序。 */
  @Transactional
  public void requireLockedSentAndMatching(
      String submissionId, String applicationId, String candidateId, String clientId,
      String jdId, String jdVersionId, String resumeVersionId) {
    validate(persistence.lock(submissionId), applicationId, candidateId, clientId, jdId, jdVersionId, resumeVersionId);
  }

  /** 查询已有推荐事实；不把已撤回事实作为异常，供下一步去重和恢复使用。 */
  @Transactional(readOnly = true)
  public boolean isSentAndMatching(String submissionId, String applicationId, String candidateId,
      String clientId, String jdId, String jdVersionId, String resumeVersionId) {
    return sentAndMatching(persistence.find(submissionId), applicationId, candidateId, clientId,
        jdId, jdVersionId, resumeVersionId);
  }

  private static boolean sentAndMatching(ClientSubmission value, String applicationId,
      String candidateId, String clientId, String jdId, String jdVersionId, String resumeVersionId) {
    return value != null && SubmissionDeliveryStatus.SENT.matches(value.getDeliveryStatus())
        && value.getSentAt() != null
        && Objects.equals(value.getApplicationId(), applicationId)
        && Objects.equals(value.getCandidateId(), candidateId)
        && Objects.equals(value.getClientId(), clientId)
        && Objects.equals(value.getJdId(), jdId)
        && Objects.equals(value.getJdVersionId(), jdVersionId)
        && Objects.equals(value.getResumeVersionId(), resumeVersionId);
  }

  private void validate(ClientSubmission value, String applicationId, String candidateId, String clientId,
      String jdId, String jdVersionId, String resumeVersionId) {
    if (!sentAndMatching(value, applicationId, candidateId, clientId, jdId, jdVersionId, resumeVersionId)) {
      throw new PlatformException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "CLIENT_SUBMISSION_MISMATCH",
          "客户推荐未发送或与招聘活动、候选人、客户、JD、JD 版本、简历版本不完全一致",
          false);
    }
  }
}
