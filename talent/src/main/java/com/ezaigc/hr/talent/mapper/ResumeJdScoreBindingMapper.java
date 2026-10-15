package com.ezaigc.hr.talent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.talent.domain.entity.ResumeJdScoreBinding;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ResumeJdScoreBindingMapper extends BaseMapper<ResumeJdScoreBinding> {
  String COLUMNS = "id,operation_id,candidate_id,resume_version_id,jd_id,jd_version_id,"
      + "default_match_run_id,selected_by_user_id,owner_department_id,selected_at,version,ext,remark,"
      + "create_id,create_name,create_time,update_id,update_name,update_time";

  @SpecialSql("按候选人批量读取不可变岗位评分绑定，并以最新选择优先组装当前岗位上下文")
  @Select("<script>SELECT " + COLUMNS + " FROM talent_resume_jd_score_binding WHERE candidate_id IN "
      + "<foreach collection=\"candidateIds\" item=\"id\" open=\"(\" separator=\",\" close=\")\">#{id}</foreach> "
      + "ORDER BY selected_at DESC,id DESC</script>")
  List<ResumeJdScoreBinding> findForCandidates(@Param("candidateIds") List<String> candidateIds);

  @SpecialSql("按幂等操作号回读同批不可变岗位评分绑定，保证重复请求返回首次结果")
  @Select("SELECT " + COLUMNS
      + " FROM talent_resume_jd_score_binding WHERE operation_id=#{operationId} ORDER BY id")
  List<ResumeJdScoreBinding> findByOperation(String operationId);
}
