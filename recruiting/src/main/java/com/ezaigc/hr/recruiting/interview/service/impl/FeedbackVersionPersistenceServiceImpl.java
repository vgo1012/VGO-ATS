package com.ezaigc.hr.recruiting.interview.service.impl;

import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.MAX_FEEDBACK_VERSIONS_PER_BATCH;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.interview.domain.entity.FeedbackVersion;
import com.ezaigc.hr.recruiting.interview.mapper.FeedbackVersionMapper;
import com.ezaigc.hr.recruiting.interview.service.FeedbackVersionPersistenceService;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** FeedbackVersion 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class FeedbackVersionPersistenceServiceImpl
    extends ServiceImpl<FeedbackVersionMapper, FeedbackVersion>
    implements FeedbackVersionPersistenceService {

  /** 新增反馈版本。 */
  @Override
  public int insertFeedbackVersion(
      String id,
      String feedbackId,
      int versionNo,
      String verdict,
      String comment,
      String externalSource,
      Instant occurredAt,
      String reason,
      String actorId,
      Instant now) {
    FeedbackVersion version = new FeedbackVersion();
    version.setId(id);
    version.setFeedbackId(feedbackId);
    version.setVersionNo(versionNo);
    version.setVerdict(verdict);
    version.setShortComment(comment);
    version.setExternalFeedbackSource(externalSource);
    version.setFeedbackOccurredAt(occurredAt);
    version.setChangeReason(reason);
    version.setCreateId(actorId);
    version.setCreateTime(now);
    return baseMapper.insert(version);
  }

  /** 查询反馈版本。 */
  @Override
  public FeedbackVersion findFeedbackVersion(String feedbackId, int versionNo) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(FeedbackVersion.class)
            .eq(FeedbackVersion::getFeedbackId, feedbackId)
            .eq(FeedbackVersion::getVersionNo, versionNo));
  }

  @Override
  public Map<String, FeedbackVersion> findFeedbackVersions(Map<String, Integer> versions) {
    if (versions == null || versions.size() > MAX_FEEDBACK_VERSIONS_PER_BATCH
        || versions.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getKey().isBlank()
            || entry.getValue() == null || entry.getValue() < 1)) {
      throw new IllegalArgumentException("面评版本批量查询参数无效或超过上限");
    }
    if (versions.isEmpty()) {
      return Map.of();
    }
    var query = Wrappers.lambdaQuery(FeedbackVersion.class);
    query.and(conditions -> versions.forEach((id, version) -> conditions.or(pair ->
        pair.eq(FeedbackVersion::getFeedbackId, id).eq(FeedbackVersion::getVersionNo, version))));
    return baseMapper.selectList(query).stream()
        .collect(Collectors.toUnmodifiableMap(FeedbackVersion::getFeedbackId, value -> value));
  }
}
