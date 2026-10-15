package com.ezaigc.hr.recruiting.demand.mapper;

import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.recruiting.demand.domain.entity.MailJdRoutingRow;
import com.ezaigc.hr.recruiting.demand.service.MailJdRoutingStore;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MailJdRoutingMapper extends MailJdRoutingStore {

  @SpecialSql("读取所有当前有效 JD 的路由事实，由应用层执行 NFKC 后的精确比较")
  @Select("""
      SELECT j.id AS jd_id, v.id AS jd_version_id, j.code, v.title, v.version_no,
             j.owner_user_id, j.owner_department_id, region.name AS work_region_name, f.facts_json
      FROM demand_jd j
      LEFT JOIN demand_jd_code_option region ON region.kind='REGION' AND region.code=j.work_region_code
      JOIN demand_jd_version v
        ON v.jd_id=j.id AND v.version_no=j.current_version_no AND v.status=#{publishedStatus}
      LEFT JOIN demand_jd_fact_snapshot f
        ON f.jd_version_id=v.id AND f.current_marker='Y'
      WHERE j.deleted=FALSE AND j.status=#{activeStatus}
      ORDER BY j.id
      """)
  List<MailJdRoutingRow> findActiveTargets(String publishedStatus, String activeStatus);

  @SpecialSql("人工关联时锁定当前有效 JD 与版本，保证提交时快照一致")
  @Select("""
      SELECT j.id AS jd_id, v.id AS jd_version_id, j.code, v.title, v.version_no,
             j.owner_user_id, j.owner_department_id, region.name AS work_region_name, NULL AS facts_json
      FROM demand_jd j
      LEFT JOIN demand_jd_code_option region ON region.kind='REGION' AND region.code=j.work_region_code
      JOIN demand_jd_version v
        ON v.jd_id=j.id AND v.version_no=j.current_version_no AND v.status=#{publishedStatus}
      WHERE j.id=#{jdId} AND j.deleted=FALSE AND j.status=#{activeStatus}
      FOR SHARE
      """)
  MailJdRoutingRow findCurrentTargetForShare(String jdId, String publishedStatus, String activeStatus);
}
