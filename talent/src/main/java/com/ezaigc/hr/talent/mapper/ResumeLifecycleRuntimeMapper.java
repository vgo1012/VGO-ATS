package com.ezaigc.hr.talent.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycleRuntime;
import java.time.Instant;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
@Mapper
public interface ResumeLifecycleRuntimeMapper extends BaseMapper<ResumeLifecycleRuntime> {
  @SpecialSql("单例持久化首次启用时间，重复启动不能把存量计时向后推")
  @Insert("INSERT IGNORE INTO talent_resume_lifecycle_runtime(id,activated_at,calendar_generation,completed_generation,version,create_time,update_time) VALUES('RESUME_LIFECYCLE',#{now},0,0,0,COALESCE(#{now},CURRENT_TIMESTAMP(6)),COALESCE(#{now},CURRENT_TIMESTAMP(6)))")
  int ensure(Instant now);
  @SpecialSql("第一次启用时才固定存量时钟，维护日历不代表启用")
  @Update("UPDATE talent_resume_lifecycle_runtime SET activated_at=#{now},update_time=#{now},version=version+1 WHERE id='RESUME_LIFECYCLE' AND activated_at IS NULL")
  int activate(Instant now);
  @SpecialSql("数据库租约串行化多实例调度，过期可恢复")
  @Update("UPDATE talent_resume_lifecycle_runtime SET lease_owner=#{owner},lease_until=#{until},update_time=#{now},version=version+1 WHERE id='RESUME_LIFECYCLE' AND (lease_until IS NULL OR lease_until<=#{now} OR lease_owner=#{owner})")
  int claim(String owner,Instant now,Instant until);
  @SpecialSql("持有运行租约才能标记结算完成，日历新版独立等待后续重算")
  @Update("UPDATE talent_resume_lifecycle_runtime SET completed_generation=#{generation},last_settlement_date=#{date},update_time=#{now},version=version+1 WHERE id='RESUME_LIFECYCLE' AND lease_owner=#{owner} AND lease_until>#{now}")
  int complete(String owner,long generation,LocalDate date,Instant now);
  @SpecialSql("仅释放本次持有的租约")
  @Update("UPDATE talent_resume_lifecycle_runtime SET lease_owner=NULL,lease_until=NULL,update_time=#{now},version=version+1 WHERE id='RESUME_LIFECYCLE' AND lease_owner=#{owner}")
  int release(String owner,Instant now);
  @SpecialSql("发布只标记重算，不在持有年度指针时锁简历")
  @Update("UPDATE talent_resume_lifecycle_runtime SET calendar_generation=calendar_generation+1,version=version+1 WHERE id='RESUME_LIFECYCLE'")
  int calendarChanged();
}
