package com.ezaigc.hr.talent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.talent.domain.entity.AffectedWorkflow;
import com.ezaigc.hr.talent.domain.entity.CompanyBlacklistRecord;
import com.ezaigc.hr.talent.domain.entity.RestrictionSummary;
import com.ezaigc.hr.talent.enums.RiskSubmissionDeliveryStatus;
import com.ezaigc.hr.talent.enums.RiskSubmissionProcessStatus;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RiskMapper extends BaseMapper<CompanyBlacklistRecord>, RiskSqlOperations {

  @SpecialSql("当前公司黑名单详情需要联查当前投影和不可变风险记录")
  @Select(
      "SELECT"
          + " r.id,r.candidate_id,r.record_type,r.status,r.reason_code,r.reason_detail,r.source_channel,r.evidence_reference,r.corrects_record_id,r.recorded_by_user_id,r.occurred_at,r.version,r.ext,r.remark,r.create_id,r.create_name,r.create_time,r.update_id,r.update_name,r.update_time"
          + " FROM risk_company_blacklist_current c JOIN risk_company_blacklist_record r ON"
          + " r.id=c.record_id WHERE c.candidate_id=#{candidateId}")
  CompanyBlacklistRecord currentBlacklist(String candidateId);

  @SpecialSql("活动申请计数跨越 Recruitment 模块表，无法由 Talent 实体 wrapper 表达")
  @Select(
      "SELECT COUNT(*) FROM recruitment_application WHERE candidate_id=#{candidateId} AND"
          + " terminal_at IS NULL")
  /** 统计生效应聘流程列表。 */
  int countActiveApplications(String candidateId);

  @SpecialSql("活动推荐计数跨越 Submission 模块表且包含组合状态语义")
  @Select(
      "SELECT COUNT(*) FROM submission_client_submission WHERE candidate_id=#{candidateId} AND"
          + " delivery_status IN ('"
          + RiskSubmissionDeliveryStatus.Codes.DRAFT
          + "','"
          + RiskSubmissionDeliveryStatus.Codes.SENT
          + "') AND process_status NOT IN ('"
          + RiskSubmissionProcessStatus.Codes.ACCEPTED
          + "','"
          + RiskSubmissionProcessStatus.Codes.REJECTED
          + "','"
          + RiskSubmissionProcessStatus.Codes.EXPIRED
          + "')")
  /** 统计生效客户推荐列表。 */
  int countActiveSubmissions(String candidateId);

  @SpecialSql("黑名单影响面需要合并申请与客户推荐两类跨模块资源投影")
  @Select(
      "SELECT 'APPLICATION' resource_type,a.id resource_id,a.id application_id,NULL client_id,a.jd_id,a.current_stage status,a.owner_user_id,a.update_time"
          + " FROM recruitment_application a WHERE a.candidate_id=#{candidateId} AND a.terminal_at IS NULL"
          + " UNION ALL SELECT 'CLIENT_SUBMISSION',s.id,s.application_id,s.client_id,s.jd_id,CONCAT(s.delivery_status,'/',s.process_status),s.owner_user_id,s.update_time"
          + " FROM submission_client_submission s WHERE s.candidate_id=#{candidateId} AND s.delivery_status IN ('"
          + RiskSubmissionDeliveryStatus.Codes.DRAFT
          + "','"
          + RiskSubmissionDeliveryStatus.Codes.SENT
          + "') AND s.process_status NOT IN ('"
          + RiskSubmissionProcessStatus.Codes.ACCEPTED
          + "','"
          + RiskSubmissionProcessStatus.Codes.REJECTED
          + "','"
          + RiskSubmissionProcessStatus.Codes.EXPIRED
          + "') ORDER BY update_time DESC,resource_id DESC LIMIT #{limit}")
  List<AffectedWorkflow> affected(String candidateId, int limit);

  @SpecialSql("黑名单影响清单合并两类资源后按更新时间与唯一资源编号进行游标分页")
  @Select("""
      <script>
      SELECT resource_type,resource_id,application_id,client_id,jd_id,status,owner_user_id,update_time
      FROM (
        SELECT 'APPLICATION' resource_type,a.id resource_id,a.id application_id,NULL client_id,
          a.jd_id,a.current_stage status,a.owner_user_id,a.update_time
        FROM recruitment_application a WHERE a.candidate_id=#{candidateId} AND a.terminal_at IS NULL
        UNION ALL
        SELECT 'CLIENT_SUBMISSION',s.id,s.application_id,s.client_id,s.jd_id,
          CONCAT(s.delivery_status,'/',s.process_status),s.owner_user_id,s.update_time
        FROM submission_client_submission s WHERE s.candidate_id=#{candidateId}
          AND s.delivery_status IN ('""" + RiskSubmissionDeliveryStatus.Codes.DRAFT + "','"
          + RiskSubmissionDeliveryStatus.Codes.SENT + "') AND s.process_status NOT IN ('"
          + RiskSubmissionProcessStatus.Codes.ACCEPTED + "','" + RiskSubmissionProcessStatus.Codes.REJECTED + "','" + RiskSubmissionProcessStatus.Codes.EXPIRED + "')" + """
      ) affected
      <where>
        <if test="beforeTime != null">
          update_time &lt; #{beforeTime} OR (update_time = #{beforeTime} AND resource_id &lt; #{beforeId})
        </if>
      </where>
      ORDER BY update_time DESC,resource_id DESC LIMIT #{limit}
      </script>
      """)
  List<AffectedWorkflow> affectedPage(String candidateId, Instant beforeTime, String beforeId, int limit);

  @SpecialSql("有效限制摘要需要合并公司黑名单与客户限制并联查客户名称")
  @Select(
      """
      SELECT 'COMPANY_BLACKLIST' restriction_type,NULL client_id,NULL client_name,r.reason_detail reason,r.source_channel,r.occurred_at,NULL expires_at,TRUE blocking
        FROM risk_company_blacklist_current c JOIN risk_company_blacklist_record r ON r.id=c.record_id WHERE c.candidate_id=#{candidateId} AND c.active=1
      UNION ALL
      SELECT 'CLIENT_RESTRICTION',r.client_id,dc.name,r.reason_detail,r.source_channel,r.source_occurred_at,r.expires_at,FALSE
        FROM submission_client_restriction_current c JOIN submission_client_restriction r ON r.id=c.record_id JOIN demand_client dc ON dc.id=r.client_id
       WHERE c.candidate_id=#{candidateId} AND c.active=1 AND (c.expires_at IS NULL OR c.expires_at>#{now})
      """)
  List<RestrictionSummary> restrictions(String candidateId, Instant now);

  @SpecialSql("按当前页批量合并有效全局和指定公司标签，避免逐行查询")
  @Select("""
      <script>
      SELECT candidate_id FROM risk_company_blacklist_current
      WHERE active=1 AND candidate_id IN
      <foreach collection="candidateIds" item="id" open="(" separator="," close=")">#{id}</foreach>
      UNION
      SELECT candidate_id FROM submission_client_restriction_current
      WHERE active=1 AND (expires_at IS NULL OR expires_at &gt; #{now}) AND candidate_id IN
      <foreach collection="candidateIds" item="id" open="(" separator="," close=")">#{id}</foreach>
      </script>
      """)
  List<String> blacklistTags(List<String> candidateIds, Instant now);
}
