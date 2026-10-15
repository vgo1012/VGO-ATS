package com.ezaigc.hr.talent.mapper;

import com.ezaigc.hr.talent.domain.entity.WorkCalendarRevisionEntity;
import com.ezaigc.hr.talent.enums.WorkCalendarRevisionStatus;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarYearEntity;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarDayEntity;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WorkCalendarMapper extends BaseMapper<WorkCalendarRevisionEntity> {
  @SpecialSql("年度锁与聚合批量维护必须使用固定显式 SQL")
  @Select("""
      SELECT calendar_year,published_revision_id,version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time FROM talent_work_calendar_year WHERE calendar_year=#{year} FOR UPDATE
      """)
  WorkCalendarYearEntity lockYear(@Param("year") int year);

  @SpecialSql("年度锁与聚合批量维护必须使用固定显式 SQL")
  @Select("""
      SELECT calendar_year,published_revision_id,version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time FROM talent_work_calendar_year WHERE calendar_year=#{year}
      """)
  WorkCalendarYearEntity findYear(@Param("year") int year);

  @SpecialSql("年度锁与聚合批量维护必须使用固定显式 SQL")
  @Insert("""
      INSERT IGNORE INTO talent_work_calendar_year(calendar_year,version) VALUES(#{year},0)
      """)
  int ensureYear(@Param("year") int year);

  @SpecialSql("年度锁与聚合批量维护必须使用固定显式 SQL")
  @Update("""
      UPDATE talent_work_calendar_year SET published_revision_id=#{revisionId},version=version+1 WHERE calendar_year=#{year}
      """)
  int publishPointer(@Param("year") int year, @Param("revisionId") String revisionId);

  @SpecialSql("年度锁与聚合批量维护必须使用固定显式 SQL")
  @Select("""
      SELECT id,calendar_year,revision,status,source_note,published_by,published_name,publish_reason,published_at,recalculation_id,preview_id,preview_fingerprint,preview_published_id,preview_expires_at,version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time FROM talent_work_calendar_revision WHERE calendar_year=#{year} ORDER BY revision DESC LIMIT 50
      """)
  List<WorkCalendarRevisionEntity> revisions(@Param("year") int year);

  @SpecialSql("年度锁与聚合批量维护必须使用固定显式 SQL")
  @Select("""
      SELECT id,calendar_revision_id,calendar_date,day_type,description,version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time FROM talent_work_calendar_day WHERE calendar_revision_id=#{revisionId} ORDER BY calendar_date
      """)
  List<WorkCalendarDayEntity> days(@Param("revisionId") String revisionId);

  @SpecialSql("年度锁与聚合批量维护必须使用固定显式 SQL")
  @Insert("""
      <script>INSERT INTO talent_work_calendar_day(id,calendar_revision_id,calendar_date,day_type,description,version) VALUES <foreach collection="days" item="day" separator=",">(#{day.id},#{day.calendarRevisionId},#{day.calendarDate},#{day.dayType},#{day.description},0)</foreach></script>
      """)
  int insertDays(@Param("days") List<WorkCalendarDayEntity> days);

  @SpecialSql("年度锁与聚合批量维护必须使用固定显式 SQL")
  @Update("""
      <script>UPDATE talent_work_calendar_day SET day_type=#{dayType},description=#{description},version=version+1 WHERE calendar_revision_id=#{revisionId} AND calendar_date IN <foreach collection="dates" item="date" open="(" separator="," close=")">#{date}</foreach></script>
      """)
  int updateDays(@Param("revisionId") String revisionId, @Param("dates") List<LocalDate> dates, @Param("dayType") String dayType, @Param("description") String description);

  @SpecialSql("年度锁与聚合批量维护必须使用固定显式 SQL")
  @Update("UPDATE talent_work_calendar_revision SET preview_id=NULL,preview_fingerprint=NULL,preview_published_id=NULL,preview_expires_at=NULL,version=version+1 WHERE id=#{id} AND status='" + WorkCalendarRevisionStatus.Codes.DRAFT + "'")
  int clearPreview(@Param("id") String id);

  @SpecialSql("年度锁与聚合批量维护必须使用固定显式 SQL")
  @Update("UPDATE talent_work_calendar_revision SET preview_id=#{previewId},preview_fingerprint=#{fingerprint},preview_published_id=#{publishedId},preview_expires_at=#{expiresAt} WHERE id=#{id} AND status='" + WorkCalendarRevisionStatus.Codes.DRAFT + "'")
  int setPreview(@Param("id") String id, @Param("previewId") String previewId, @Param("fingerprint") String fingerprint, @Param("publishedId") String publishedId, @Param("expiresAt") Instant expiresAt);

}
