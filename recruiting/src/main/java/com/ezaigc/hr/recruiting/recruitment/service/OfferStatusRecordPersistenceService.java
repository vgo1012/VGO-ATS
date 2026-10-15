package com.ezaigc.hr.recruiting.recruitment.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OfferStatusRecord;
import java.time.Instant;
import java.util.List;

/** OfferStatusRecord 实体的持久化服务。 */
public interface OfferStatusRecordPersistenceService extends IService<OfferStatusRecord> {

  int insertOffer(
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
      Instant now);

  /** 按系统写入时间展示追加历史，保留记录中的原始业务发生时间。 */
  List<OfferStatusRecord> findOffers(String applicationId);

  OfferStatusRecord findOffer(String applicationId, String id);

  /** 在申请锁内当前读取指定 Offer 事实，仍绑定申请和记录 ID。 */
  OfferStatusRecord findOfferForUpdate(String applicationId, String id);

  /** 当前有效状态取最后追加记录，不以调用方回填的业务发生时间决定覆盖顺序。 */
  OfferStatusRecord latestOffer(String applicationId);

  OfferStatusRecord latestOfferForUpdate(String applicationId);

  /** 按逻辑 JD 跨全部版本统计当前占用 HC 的 Application 数。 */
  int countOccupiedByJd(String jdId);
}
