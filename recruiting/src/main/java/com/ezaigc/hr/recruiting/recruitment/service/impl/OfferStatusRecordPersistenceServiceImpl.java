package com.ezaigc.hr.recruiting.recruitment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OfferStatusRecord;
import com.ezaigc.hr.recruiting.recruitment.mapper.OfferStatusRecordMapper;
import com.ezaigc.hr.recruiting.recruitment.service.OfferStatusRecordPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** OfferStatusRecord 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class OfferStatusRecordPersistenceServiceImpl
    extends ServiceImpl<OfferStatusRecordMapper, OfferStatusRecord>
    implements OfferStatusRecordPersistenceService {

  @Override
  public int insertOffer(
      String id,
      String applicationId,
      String recordType,
      String status,
      String terminationReason,
      String applicationOutcome,
      String reason,
      String correctsId,
      String reopensId,
      String source,
      String sourceReference,
      String sourceStatus,
      Instant occurredAt,
      String actorId,
      Instant now) {
    OfferStatusRecord offer = new OfferStatusRecord();
    offer.setId(id);
    offer.setApplicationId(applicationId);
    offer.setRecordType(recordType);
    offer.setStatus(status);
    offer.setTerminationReason(terminationReason);
    offer.setApplicationOutcome(applicationOutcome);
    offer.setReason(reason);
    offer.setCorrectsRecordId(correctsId);
    offer.setReopensRecordId(reopensId);
    offer.setSource(source);
    offer.setSourceReference(sourceReference);
    offer.setSourceStatus(sourceStatus);
    offer.setOccurredAt(occurredAt);
    offer.setOperatedByUserId(actorId);
    offer.setCreateTime(now);
    return affected(this.save(offer));
  }

  @Override
  public List<OfferStatusRecord> findOffers(String applicationId) {
    return this.list(
        Wrappers.lambdaQuery(OfferStatusRecord.class)
            .eq(OfferStatusRecord::getApplicationId, applicationId)
            .orderByAsc(OfferStatusRecord::getCreateTime, OfferStatusRecord::getId));
  }

  @Override
  public OfferStatusRecord findOffer(String applicationId, String id) {
    return this.getOne(
        Wrappers.lambdaQuery(OfferStatusRecord.class)
            .eq(OfferStatusRecord::getApplicationId, applicationId)
            .eq(OfferStatusRecord::getId, id));
  }

  @Override
  public OfferStatusRecord findOfferForUpdate(String applicationId, String id) {
    return this.getOne(
        Wrappers.lambdaQuery(OfferStatusRecord.class)
            .eq(OfferStatusRecord::getApplicationId, applicationId)
            .eq(OfferStatusRecord::getId, id)
            .last("FOR UPDATE"));
  }

  @Override
  public OfferStatusRecord latestOffer(String applicationId) {
    return this.getOne(
        Wrappers.lambdaQuery(OfferStatusRecord.class)
            .eq(OfferStatusRecord::getApplicationId, applicationId)
            // 回填的业务时间可能存在时钟偏差，当前状态必须取系统最后追加的事实。
            .orderByDesc(OfferStatusRecord::getCreateTime, OfferStatusRecord::getId)
            .last("LIMIT 1"));
  }

  @Override
  public OfferStatusRecord latestOfferForUpdate(String applicationId) {
    return baseMapper.latestOfferForUpdate(applicationId);
  }

  @Override
  public int countOccupiedByJd(String jdId) {
    return baseMapper.findOccupiedApplicationIdsForUpdate(jdId).size();
  }

  private static int affected(boolean changed) {
    return changed ? 1 : 0;
  }
}
