package com.ezaigc.hr.talent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.talent.domain.entity.ResumeModelRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import java.time.Instant;

@Mapper
public interface ResumeModelRunMapper extends BaseMapper<ResumeModelRun> {
  /** 同模块内经抽取关联任务与版本，不读取或修改其他任务的模型记录。 */
  @SpecialSql("模型运行需经抽取运行关联共享任务，在单条联表更新中校验任务和版本绑定并终结运行状态")
  @Update("""
      UPDATE talent_resume_ai_model_run m
      JOIN talent_resume_extraction_run e ON e.id = m.extraction_run_id
      SET m.status = #{failedStatus}, m.error_code = #{errorCode}, m.finished_at = #{now},
          m.update_id = #{actorId}, m.update_time = #{now}, m.version = m.version + 1
      WHERE e.task_id = #{taskId} AND e.resume_version_id = #{versionId}
        AND m.resume_version_id = #{versionId} AND m.status = #{runningStatus}
      """)
  int terminateWithoutWorker(@Param("taskId") String taskId, @Param("versionId") String versionId,
      @Param("runningStatus") String runningStatus, @Param("failedStatus") String failedStatus,
      @Param("errorCode") String errorCode, @Param("now") Instant now, @Param("actorId") String actorId);
}
