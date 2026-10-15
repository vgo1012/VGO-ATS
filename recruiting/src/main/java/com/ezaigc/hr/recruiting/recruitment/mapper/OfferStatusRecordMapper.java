package com.ezaigc.hr.recruiting.recruitment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OfferStatusRecord;
import com.ezaigc.hr.recruiting.recruitment.enums.OfferStatus;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OfferStatusRecordMapper extends BaseMapper<OfferStatusRecord> {

  @SpecialSql("JD 行锁后锁定当前占用 HC 的 Application 与最新 Offer 行，防止并发入职超额")
  @Select("""
      SELECT a.id
        FROM recruitment_application a
        JOIN recruitment_offer_status_record o ON o.application_id = a.id
        LEFT JOIN recruitment_offer_status_record newer ON newer.application_id = o.application_id
          AND (newer.create_time > o.create_time
            OR (newer.create_time = o.create_time AND newer.id > o.id))
       WHERE a.jd_id = #{jdId}
         AND """ + OfferStatus.OCCUPIED_SQL_PREDICATE + """
         AND newer.id IS NULL
       FOR UPDATE
      """)
  List<String> findOccupiedApplicationIdsForUpdate(@Param("jdId") String jdId);

  @SpecialSql("Offer 写路径锁定最新事实，确保等待并发事务后使用当前读而非旧快照")
  @Select("""
      SELECT id, application_id, record_type, status, termination_reason, application_outcome,
             reason, corrects_record_id, reopens_record_id, source, source_reference, source_status,
             occurred_at, operated_by_user_id, ext, remark, create_id, create_name, create_time,
             update_id, update_name, update_time, version
        FROM recruitment_offer_status_record
       WHERE application_id = #{applicationId}
       ORDER BY create_time DESC, id DESC
       LIMIT 1 FOR UPDATE
      """)
  OfferStatusRecord latestOfferForUpdate(@Param("applicationId") String applicationId);
}
