package com.ezaigc.hr.recruiting.demand.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdResumeSourceEntity;
import org.apache.ibatis.annotations.Insert;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import com.ezaigc.hr.common.web.pagination.StableCursor;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface JdResumeSourceMapper extends BaseMapper<JdResumeSourceEntity> {

  @SpecialSql("唯一键冲突保持原记录，以数据库原子插入保证并发邮件重放幂等")
  @Insert("""
    INSERT INTO demand_jd_resume_source
    (id,mail_source_id,candidate_id,candidate_name,resume_version_id,original_file_name,content_sha256,create_id,create_time,update_id,update_time)
    VALUES (#{id},#{mailSourceId},#{candidateId},#{candidateName},#{resumeVersionId},#{originalFileName},#{contentSha256},#{createId},#{createTime},#{updateId},#{updateTime})
    ON DUPLICATE KEY UPDATE id=id
    """)
  int insertIfAbsent(JdResumeSourceEntity row);
  @SpecialSql("附件来源列表联查邮件的 JD 版本归属并使用稳定游标分页")
  @Select("""
    <script>
    SELECT s.id,s.mail_source_id,s.candidate_id,s.candidate_name,s.resume_version_id,s.original_file_name,s.content_sha256,s.ext,s.remark,s.create_id,s.create_name,s.create_time,s.update_id,s.update_name,s.update_time,s.version FROM demand_jd_resume_source s JOIN demand_mail_source_message m ON m.id=s.mail_source_id
    WHERE m.jd_version_id=#{jdVersionId}
    AND (#{scopeAll} = TRUE OR m.create_id = #{actorId})
    <if test="cursor != null">AND (s.create_time &lt; #{cursor.sortTime} OR (s.create_time=#{cursor.sortTime} AND s.id &lt; #{cursor.id}))</if>
    ORDER BY s.create_time DESC,s.id DESC LIMIT #{fetchSize}
    </script>
    """)
  List<JdResumeSourceEntity> findPage(@Param("jdVersionId") String jdVersionId,@Param("cursor") StableCursor cursor,@Param("fetchSize") int fetchSize, @Param("actorId") String actorId, @Param("scopeAll") boolean scopeAll);

  @SpecialSql("来件简历总数按邮件绑定的 JD 版本统计")
  @Select("""
    SELECT COUNT(*) FROM demand_jd_resume_source s
    JOIN demand_mail_source_message m ON m.id=s.mail_source_id
    WHERE m.jd_version_id=#{jdVersionId}
    """)
  long countByJdVersion(@Param("jdVersionId") String jdVersionId);

}
