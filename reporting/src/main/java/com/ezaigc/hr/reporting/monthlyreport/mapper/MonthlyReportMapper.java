package com.ezaigc.hr.reporting.monthlyreport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportEntity;
import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportStatus;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MonthlyReportMapper extends BaseMapper<MonthlyReportEntity> {
    @SpecialSql("任务和报告在短事务中按统一顺序锁定，拒绝迟到产物覆盖")
    @Select("SELECT id,report_month,data_as_of,status,task_id,phase,owner_user_id,owner_department_id,"
            + "idempotency_key,request_fingerprint,generation_key,generation_fingerprint,management_remarks,"
            + "snapshot_json,offer_count,total_score,error_code,error_detail,expires_at,"
            + "ext,remark,create_id,create_name,create_time,update_id,update_name,update_time,version "
            + "FROM reporting_monthly_report WHERE id=#{id} FOR UPDATE")
    MonthlyReportEntity lock(@Param("id") String id);

    @SpecialSql("以状态索引锁定活动月报范围，当前读检查公司队列上限；并发空范围插入冲突由事务回滚")
    @Select("SELECT id FROM reporting_monthly_report WHERE status IN ('" + MonthlyReportStatus.Codes.UPLOADING
            + "','" + MonthlyReportStatus.Codes.VALIDATING + "','" + MonthlyReportStatus.Codes.QUEUED + "') FOR UPDATE")
    List<String> lockActiveIds();
}
