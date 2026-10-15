package com.ezaigc.hr.talent.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycle;
import com.ezaigc.hr.talent.domain.dto.ResumeLifecycleReadyDTO;
import com.ezaigc.hr.talent.enums.CandidateStatus;
import com.ezaigc.hr.talent.enums.ResumeIntakeStatus;
import com.ezaigc.hr.talent.enums.ResumeIntakeScoreStatus;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
@Mapper
public interface ResumeLifecycleMapper extends BaseMapper<ResumeLifecycle> {
  @SpecialSql("锁定简历当前周期，路由命令已持有版本锁")
  @Select("SELECT id,candidate_id,resume_version_id,cycle_no,active_marker,entered_at,phase,warning_level,elapsed_workdays,next_check_at,share_due_at,settled_through,calendar_revisions,pause_reason,shared_at,closed_reason,version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time FROM talent_resume_lifecycle WHERE resume_version_id=#{resumeVersionId} AND active_marker='Y' FOR UPDATE")
  ResumeLifecycle lockCurrent(String resumeVersionId);
  @SpecialSql("从已完成默认评分的当前版本恢复遗漏周期，按版本游标扫描避免单批饥饿")
  @Select("SELECT r.candidate_id,v.id AS resume_version_id,MIN(i.update_time) AS entered_at FROM talent_resume r JOIN talent_resume_version v ON v.resume_id=r.id AND v.version_no=r.current_version_no JOIN talent_candidate c ON c.id=r.candidate_id AND c.status='" + CandidateStatus.Codes.ACTIVE + "' JOIN talent_resume_intake i ON i.resume_version_id=v.id JOIN talent_intake_batch b ON b.id=i.batch_id WHERE i.status='" + ResumeIntakeStatus.Codes.COMPLETED + "' AND i.score_status='" + ResumeIntakeScoreStatus.Codes.SUCCEEDED + "' AND i.score_run_id IS NOT NULL AND b.jd_version_id IS NOT NULL AND v.id>#{afterId} AND NOT EXISTS(SELECT 1 FROM talent_resume_lifecycle l WHERE l.resume_version_id=v.id) GROUP BY r.candidate_id,v.id ORDER BY v.id LIMIT #{limit}")
  List<ResumeLifecycleReadyDTO> ready(String afterId, int limit);
  @SpecialSql("同一版本新周期采用历史最大轮次，调用方持有路由锁")
  @Select("SELECT COALESCE(MAX(cycle_no),0) FROM talent_resume_lifecycle WHERE resume_version_id=#{resumeVersionId}")
  int lastCycle(String resumeVersionId);
}
