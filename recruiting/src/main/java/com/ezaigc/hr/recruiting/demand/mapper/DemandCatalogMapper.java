package com.ezaigc.hr.recruiting.demand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.recruiting.demand.domain.entity.ClientEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandEntity;
import com.ezaigc.hr.recruiting.demand.domain.dto.PublicJdSourceDTO;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.PositionEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogSqlOperations;
import com.ezaigc.hr.recruiting.recruitment.enums.OfferStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DemandCatalogMapper extends BaseMapper<ClientEntity>, DemandCatalogSqlOperations {

    @SpecialSql("客户列表需要跨模块对象授权和组织范围条件实施数据可见性过滤")
    @Select(
            """
                    <script>
                    SELECT id, code, name, status, owner_user_id, owner_department_id, version,
                           create_id, create_time, update_id, update_time,
                           ext, remark, create_id, create_name, create_time, update_id, update_name, update_time
                      FROM demand_client
                     WHERE (#{status} IS NULL OR status = #{status})
                       AND (#{scopeAll} = TRUE OR owner_user_id = #{actorId}
                            <if test="managedUserIds != null and !managedUserIds.isEmpty()">
                              OR owner_user_id IN <foreach collection="managedUserIds" item="value" open="(" separator="," close=")">#{value}</foreach>
                            </if>
                            <if test="visibleDepartmentIds != null and !visibleDepartmentIds.isEmpty()">
                              OR owner_department_id IN <foreach collection="visibleDepartmentIds" item="value" open="(" separator="," close=")">#{value}</foreach>
                            </if>
                            OR EXISTS (SELECT 1 FROM identity_object_grant g
                                        WHERE g.user_id = #{actorId} AND g.object_type = 'CLIENT'
                                          AND g.object_id = demand_client.id AND g.permission_code = 'demand.read'))
                       AND (#{cursorTime} IS NULL OR update_time &lt; #{cursorTime}
                            OR (update_time = #{cursorTime} AND id &lt; #{cursorId}))
                     ORDER BY update_time DESC, id DESC
                     LIMIT #{limit}
                    </script>
                    """)
    /** 查询客户列表。 */
    List<ClientEntity> findClients(
            @Param("status") String status,
            @Param("actorId") String actorId,
            @Param("scopeAll") boolean scopeAll,
            @Param("managedUserIds") Set<String> managedUserIds,
            @Param("visibleDepartmentIds") Set<String> visibleDepartmentIds,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit);

    @SpecialSql("岗位列表需要联查客户归属和跨模块对象授权以实施数据范围过滤")
    @Select(
            """
                    <script>
                    SELECT p.id, p.client_id, p.project_id, p.code, p.name, p.status, p.version,
                           p.create_id, p.create_time, p.update_id, p.update_time,
                           p.ext, p.remark, p.create_id, p.create_name, p.create_time,
                           p.update_id, p.update_name, p.update_time
                      FROM demand_client_position p
                      JOIN demand_client c ON c.id = p.client_id
                     WHERE (#{clientId} IS NULL OR p.client_id = #{clientId})
                       AND (#{projectId} IS NULL OR p.project_id = #{projectId})
                       AND (#{status} IS NULL OR p.status = #{status})
                       AND (#{scopeAll} = TRUE OR c.owner_user_id = #{actorId}
                            <if test="managedUserIds != null and !managedUserIds.isEmpty()">
                              OR c.owner_user_id IN <foreach collection="managedUserIds" item="value" open="(" separator="," close=")">#{value}</foreach>
                            </if>
                            <if test="visibleDepartmentIds != null and !visibleDepartmentIds.isEmpty()">
                              OR c.owner_department_id IN <foreach collection="visibleDepartmentIds" item="value" open="(" separator="," close=")">#{value}</foreach>
                            </if>
                            OR EXISTS (SELECT 1 FROM identity_object_grant g
                                        WHERE g.user_id = #{actorId} AND g.object_type IN ('CLIENT','CLIENT_POSITION')
                                          AND g.object_id IN (p.client_id, p.id) AND g.permission_code = 'demand.read'))
                       AND (#{cursorTime} IS NULL OR p.update_time &lt; #{cursorTime}
                            OR (p.update_time = #{cursorTime} AND p.id &lt; #{cursorId}))
                     ORDER BY p.update_time DESC, p.id DESC LIMIT #{limit}
                    </script>
                    """)
    /** 查询职位列表。 */
    List<PositionEntity> findPositions(
            @Param("clientId") String clientId,
            @Param("projectId") String projectId,
            @Param("status") String status,
            @Param("actorId") String actorId,
            @Param("scopeAll") boolean scopeAll,
            @Param("managedUserIds") Set<String> managedUserIds,
            @Param("visibleDepartmentIds") Set<String> visibleDepartmentIds,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit);

    @SpecialSql("需求列表需要协作人、组织范围和跨模块对象授权联合实施数据可见性过滤")
    @Select(
            """
                    <script>
                    SELECT id, code, title, client_id, project_id, position_id, recruitment_type,
                           headcount, description, status, owner_user_id, owner_department_id, version,
                           create_id, create_time, update_id, update_time,
                           ext, remark, create_id, create_name, create_time, update_id, update_name, update_time
                      FROM demand_recruitment_demand
                     WHERE (#{status} IS NULL OR status = #{status})
                       AND (#{scopeAll} = TRUE OR owner_user_id = #{actorId}
                            OR EXISTS (SELECT 1 FROM demand_recruitment_collaborator c
                                        WHERE c.demand_id = demand_recruitment_demand.id AND c.user_id = #{actorId})
                            <if test="managedUserIds != null and !managedUserIds.isEmpty()">
                              OR owner_user_id IN <foreach collection="managedUserIds" item="value" open="(" separator="," close=")">#{value}</foreach>
                            </if>
                            <if test="visibleDepartmentIds != null and !visibleDepartmentIds.isEmpty()">
                              OR owner_department_id IN <foreach collection="visibleDepartmentIds" item="value" open="(" separator="," close=")">#{value}</foreach>
                            </if>
                            OR EXISTS (SELECT 1 FROM identity_object_grant g
                                        WHERE g.user_id = #{actorId} AND g.object_type = 'DEMAND'
                                          AND g.object_id = demand_recruitment_demand.id AND g.permission_code = 'demand.read'))
                       AND (#{cursorTime} IS NULL OR update_time &lt; #{cursorTime}
                            OR (update_time = #{cursorTime} AND id &lt; #{cursorId}))
                     ORDER BY update_time DESC, id DESC LIMIT #{limit}
                    </script>
                    """)
    /** 查询招聘需求列表。 */
    List<DemandEntity> findDemands(
            @Param("status") String status,
            @Param("actorId") String actorId,
            @Param("scopeAll") boolean scopeAll,
            @Param("managedUserIds") Set<String> managedUserIds,
            @Param("visibleDepartmentIds") Set<String> visibleDepartmentIds,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit);

    @SpecialSql("新增 JD 版本前必须锁定 JD 行以串行分配版本号")
    @Select("SELECT current_version_no FROM demand_jd WHERE id = #{id} FOR UPDATE")
    Integer lockJdVersionNo(String id);

    @SpecialSql("JD 列表需要联查需求、客户、协作人和跨模块授权以返回受控业务投影")
    @Select(
            """
                    <script>
                    SELECT j.id, j.demand_id, j.source_owner_department_id, j.source_fingerprint, j.applied_from_jd_version_id,
                           j.code, j.job_type_code, j.work_region_code,
                           region.name AS work_region_name, j.distinction, j.public_visible, j.content_shared, j.deleted, j.recruitment_type, j.headcount, j.recruitment_cycle_days,
                           j.title, j.status, j.current_version_no,
                           CASE WHEN j.recruitment_type IS NULL THEN d.client_id ELSE j.client_id END AS client_id,
                           COALESCE(j.owner_user_id, d.owner_user_id, j.create_id) AS owner_user_id,
                           COALESCE(j.owner_department_id, d.owner_department_id, j.source_owner_department_id) AS owner_department_id,
                           j.closed_by_user_id, j.closed_at, j.version, j.ext, j.remark,
                           j.create_id, j.create_name, j.create_time,
                           j.update_id, j.update_name, j.update_time, d.code AS demand_code, c.name AS client_name,
                           COALESCE((SELECT t.status FROM reporting_async_task t
                              JOIN demand_jd_version pv ON pv.id = t.resource_id
                             WHERE t.task_type = 'JD_PARSE' AND pv.jd_id = j.id
                               AND pv.version_no = j.current_version_no
                             ORDER BY t.create_time DESC, t.id DESC LIMIT 1), 'NOT_STARTED') AS current_parse_status,
                           (SELECT COUNT(*) FROM recruitment_application a
                             JOIN recruitment_offer_status_record o ON o.application_id = a.id
                            WHERE a.jd_id = j.id AND """ + OfferStatus.OCCUPIED_SQL_PREDICATE + """
                              AND NOT EXISTS (SELECT 1 FROM recruitment_offer_status_record newer
                                               WHERE newer.application_id = o.application_id
                                                 AND (newer.create_time > o.create_time
                                                   OR (newer.create_time = o.create_time AND newer.id > o.id)))) AS occupied_headcount,
                           (SELECT COUNT(*) FROM recruitment_application a
                             JOIN recruitment_offer_status_record o ON o.application_id = a.id
                            WHERE a.jd_id = j.id AND """ + OfferStatus.ONBOARDED_SQL_PREDICATE + """
                              AND NOT EXISTS (SELECT 1 FROM recruitment_offer_status_record newer
                                               WHERE newer.application_id = o.application_id
                                                 AND (newer.create_time > o.create_time
                                                   OR (newer.create_time = o.create_time AND newer.id > o.id)))) AS onboarded_headcount
                      FROM demand_jd j
                      LEFT JOIN demand_jd_code_option region ON region.kind = 'REGION' AND region.code = j.work_region_code
                      LEFT JOIN demand_recruitment_demand d ON d.id = j.demand_id
                      LEFT JOIN demand_client c ON c.id = CASE WHEN j.recruitment_type IS NULL THEN d.client_id ELSE j.client_id END
                     WHERE (#{query} IS NULL OR j.title LIKE CONCAT('%', #{query}, '%')
                            OR c.name LIKE CONCAT('%', #{query}, '%')
                            OR region.name LIKE CONCAT('%', #{query}, '%') OR CONVERT(j.code USING utf8mb4) COLLATE utf8mb4_bin LIKE CONCAT('%', #{query}, '%'))
                       AND (#{clientId} IS NULL OR CASE WHEN j.recruitment_type IS NULL THEN d.client_id ELSE j.client_id END = #{clientId})
                       AND j.deleted = FALSE
                       AND (#{status} IS NULL OR j.status = #{status})
                       AND (#{scopeAll} = TRUE OR COALESCE(j.owner_user_id, d.owner_user_id, j.create_id) = #{actorId}
                            OR EXISTS (SELECT 1 FROM demand_recruitment_collaborator rc
                                        WHERE rc.demand_id = d.id AND rc.user_id = #{actorId})
                            <if test="managedUserIds != null and !managedUserIds.isEmpty()">
                              OR COALESCE(j.owner_user_id, d.owner_user_id, j.create_id) IN <foreach collection="managedUserIds" item="value" open="(" separator="," close=")">#{value}</foreach>
                            </if>
                            <if test="visibleDepartmentIds != null and !visibleDepartmentIds.isEmpty()">
                              OR COALESCE(j.owner_department_id, d.owner_department_id, j.source_owner_department_id) IN <foreach collection="visibleDepartmentIds" item="value" open="(" separator="," close=")">#{value}</foreach>
                            </if>
                            OR EXISTS (SELECT 1 FROM identity_object_grant g
                                        WHERE g.user_id = #{actorId} AND g.object_type IN ('DEMAND','JD')
                                          AND g.object_id IN (d.id, j.id) AND g.permission_code = 'demand.read'))
                       AND (#{cursorTime} IS NULL OR j.create_time &lt; #{cursorTime}
                            OR (j.create_time = #{cursorTime} AND j.id &lt; #{cursorId}))
                     ORDER BY j.create_time DESC, j.id DESC LIMIT #{limit}
                    </script>
                    """)
    List<JdEntity> findJds(
            @Param("query") String query,
            @Param("clientId") String clientId,
            @Param("status") String status,
            @Param("actorId") String actorId,
            @Param("scopeAll") boolean scopeAll,
            @Param("managedUserIds") Set<String> managedUserIds,
            @Param("visibleDepartmentIds") Set<String> visibleDepartmentIds,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit);

    @SpecialSql("客户总数必须使用与列表相同的状态、组织范围和对象授权")
    @Select(
            """
                    <script>
                    SELECT COUNT(*)
                      FROM demand_client
                     WHERE (#{status} IS NULL OR status = #{status})
                       AND (#{scopeAll} = TRUE OR owner_user_id = #{actorId}
                            <if test="managedUserIds != null and !managedUserIds.isEmpty()">
                              OR owner_user_id IN <foreach collection="managedUserIds" item="value" open="(" separator="," close=")">#{value}</foreach>
                            </if>
                            <if test="visibleDepartmentIds != null and !visibleDepartmentIds.isEmpty()">
                              OR owner_department_id IN <foreach collection="visibleDepartmentIds" item="value" open="(" separator="," close=")">#{value}</foreach>
                            </if>
                            OR EXISTS (SELECT 1 FROM identity_object_grant g
                                        WHERE g.user_id = #{actorId} AND g.object_type = 'CLIENT'
                                          AND g.object_id = demand_client.id AND g.permission_code = 'demand.read'))
                    </script>
                    """)
    long countClients(
            @Param("status") String status,
            @Param("actorId") String actorId,
            @Param("scopeAll") boolean scopeAll,
            @Param("managedUserIds") Set<String> managedUserIds,
            @Param("visibleDepartmentIds") Set<String> visibleDepartmentIds);

    @SpecialSql("JD 总数必须使用与列表相同的搜索条件、组织范围和对象授权")
    @Select(
            """
                    <script>
                    SELECT COUNT(*)
                      FROM demand_jd j
                      LEFT JOIN demand_jd_code_option region ON region.kind = 'REGION' AND region.code = j.work_region_code
                      LEFT JOIN demand_recruitment_demand d ON d.id = j.demand_id
                      LEFT JOIN demand_client c ON c.id = CASE WHEN j.recruitment_type IS NULL THEN d.client_id ELSE j.client_id END
                     WHERE (#{query} IS NULL OR j.title LIKE CONCAT('%', #{query}, '%')
                            OR c.name LIKE CONCAT('%', #{query}, '%')
                            OR region.name LIKE CONCAT('%', #{query}, '%') OR CONVERT(j.code USING utf8mb4) COLLATE utf8mb4_bin LIKE CONCAT('%', #{query}, '%'))
                       AND (#{clientId} IS NULL OR CASE WHEN j.recruitment_type IS NULL THEN d.client_id ELSE j.client_id END = #{clientId})
                       AND j.deleted = FALSE
                       AND (#{status} IS NULL OR j.status = #{status})
                       AND (#{scopeAll} = TRUE OR COALESCE(j.owner_user_id, d.owner_user_id, j.create_id) = #{actorId}
                            OR EXISTS (SELECT 1 FROM demand_recruitment_collaborator rc
                                        WHERE rc.demand_id = d.id AND rc.user_id = #{actorId})
                            <if test="managedUserIds != null and !managedUserIds.isEmpty()">
                              OR COALESCE(j.owner_user_id, d.owner_user_id, j.create_id) IN <foreach collection="managedUserIds" item="value" open="(" separator="," close=")">#{value}</foreach>
                            </if>
                            <if test="visibleDepartmentIds != null and !visibleDepartmentIds.isEmpty()">
                              OR COALESCE(j.owner_department_id, d.owner_department_id, j.source_owner_department_id) IN <foreach collection="visibleDepartmentIds" item="value" open="(" separator="," close=")">#{value}</foreach>
                            </if>
                            OR EXISTS (SELECT 1 FROM identity_object_grant g
                                        WHERE g.user_id = #{actorId} AND g.object_type IN ('DEMAND','JD')
                                          AND g.object_id IN (d.id, j.id) AND g.permission_code = 'demand.read'))
                    </script>
                    """)
    long countJds(
            @Param("query") String query,
            @Param("clientId") String clientId,
            @Param("status") String status,
            @Param("actorId") String actorId,
            @Param("scopeAll") boolean scopeAll,
            @Param("managedUserIds") Set<String> managedUserIds,
            @Param("visibleDepartmentIds") Set<String> visibleDepartmentIds);

    @SpecialSql("公共 JD 内容仅返回已公开的有效当前版本，不扩大普通 JD 库权限")
    @Select("""
            <script>
            SELECT j.id, j.code, j.title, region.name AS work_region_name,
                   j.owner_user_id, j.owner_department_id,
                   j.current_version_no,
                   v.id AS current_version_id, j.create_time
              FROM demand_jd j
              JOIN demand_jd_version v ON v.jd_id = j.id AND v.version_no = j.current_version_no
              LEFT JOIN demand_jd_code_option region ON region.kind = 'REGION' AND region.code = j.work_region_code
             WHERE j.content_shared = TRUE AND j.applied_from_jd_version_id IS NULL
               AND j.status = #{activeStatus} AND j.deleted = FALSE AND v.status = #{publishedStatus}
               AND (#{query} IS NULL OR j.title LIKE CONCAT('%', #{query}, '%')
                    OR region.name LIKE CONCAT('%', #{query}, '%')
                    OR CONVERT(j.code USING utf8mb4) COLLATE utf8mb4_bin LIKE CONCAT('%', #{query}, '%'))
               AND (#{cursorTime} IS NULL OR j.create_time &lt; #{cursorTime}
                    OR (j.create_time = #{cursorTime} AND j.id &lt; #{cursorId}))
             ORDER BY j.create_time DESC, j.id DESC LIMIT #{limit}
            </script>
            """)
    List<PublicJdSourceDTO> findPublicJdSources(
            @Param("query") String query, @Param("activeStatus") String activeStatus,
            @Param("publishedStatus") String publishedStatus, @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") String cursorId, @Param("limit") int limit);

    @SpecialSql("公共 JD 内容总数使用与列表相同的共享、状态及搜索条件")
    @Select("""
            <script>
            SELECT COUNT(*) FROM demand_jd j
              JOIN demand_jd_version v ON v.jd_id = j.id AND v.version_no = j.current_version_no
              LEFT JOIN demand_jd_code_option region ON region.kind = 'REGION' AND region.code = j.work_region_code
             WHERE j.content_shared = TRUE AND j.applied_from_jd_version_id IS NULL
               AND j.status = #{activeStatus} AND j.deleted = FALSE AND v.status = #{publishedStatus}
               AND (#{query} IS NULL OR j.title LIKE CONCAT('%', #{query}, '%')
                    OR region.name LIKE CONCAT('%', #{query}, '%')
                    OR CONVERT(j.code USING utf8mb4) COLLATE utf8mb4_bin LIKE CONCAT('%', #{query}, '%'))
            </script>
            """)
    long countPublicJdSources(
            @Param("query") String query, @Param("activeStatus") String activeStatus,
            @Param("publishedStatus") String publishedStatus);

    @SpecialSql("公共 JD 内容详情必须在 SQL 内再次限制共享与当前状态")
    @Select("""
            SELECT j.id, j.code, j.title, region.name AS work_region_name,
                   j.owner_user_id, j.owner_department_id,
                   j.current_version_no,
                   v.id AS current_version_id, v.raw_text, j.create_time
              FROM demand_jd j
              JOIN demand_jd_version v ON v.jd_id = j.id AND v.version_no = j.current_version_no
              LEFT JOIN demand_jd_code_option region ON region.kind = 'REGION' AND region.code = j.work_region_code
             WHERE j.id = #{jdId} AND j.content_shared = TRUE AND j.applied_from_jd_version_id IS NULL
               AND j.status = #{activeStatus} AND j.deleted = FALSE AND v.status = #{publishedStatus}
            """)
    PublicJdSourceDTO findPublicJdSource(@Param("jdId") String jdId, @Param("activeStatus") String activeStatus,
            @Param("publishedStatus") String publishedStatus);

    @SpecialSql("JD 详情需要联查需求和客户以构造授权上下文投影")
    @Select(
            """
                    SELECT j.id, j.demand_id, j.source_owner_department_id, j.source_fingerprint, j.applied_from_jd_version_id,
                           j.code, j.job_type_code, j.work_region_code,
                           region.name AS work_region_name, j.distinction, j.public_visible, j.content_shared, j.deleted, j.recruitment_type, j.headcount, j.recruitment_cycle_days,
                           j.title, j.status, j.current_version_no,
                           CASE WHEN j.recruitment_type IS NULL THEN d.client_id ELSE j.client_id END AS client_id,
                           COALESCE(j.owner_user_id, d.owner_user_id, j.create_id) AS owner_user_id,
                           COALESCE(j.owner_department_id, d.owner_department_id, j.source_owner_department_id) AS owner_department_id,
                           j.closed_by_user_id, j.closed_at, j.version, j.ext, j.remark,
                           j.create_id, j.create_name, j.create_time,
                           j.update_id, j.update_name, j.update_time, d.code AS demand_code, c.name AS client_name,
                           COALESCE((SELECT t.status FROM reporting_async_task t
                              JOIN demand_jd_version pv ON pv.id = t.resource_id
                             WHERE t.task_type = 'JD_PARSE' AND pv.jd_id = j.id
                               AND pv.version_no = j.current_version_no
                             ORDER BY t.create_time DESC, t.id DESC LIMIT 1), 'NOT_STARTED') AS current_parse_status,
                           (SELECT COUNT(*) FROM recruitment_application a
                             JOIN recruitment_offer_status_record o ON o.application_id = a.id
                            WHERE a.jd_id = j.id AND """ + OfferStatus.OCCUPIED_SQL_PREDICATE + """
                              AND NOT EXISTS (SELECT 1 FROM recruitment_offer_status_record newer
                                               WHERE newer.application_id = o.application_id
                                                 AND (newer.create_time > o.create_time
                                                   OR (newer.create_time = o.create_time AND newer.id > o.id)))) AS occupied_headcount,
                           (SELECT COUNT(*) FROM recruitment_application a
                             JOIN recruitment_offer_status_record o ON o.application_id = a.id
                            WHERE a.jd_id = j.id AND """ + OfferStatus.ONBOARDED_SQL_PREDICATE + """
                              AND NOT EXISTS (SELECT 1 FROM recruitment_offer_status_record newer
                                               WHERE newer.application_id = o.application_id
                                                 AND (newer.create_time > o.create_time
                                                   OR (newer.create_time = o.create_time AND newer.id > o.id)))) AS onboarded_headcount
                      FROM demand_jd j
                      LEFT JOIN demand_jd_code_option region ON region.kind = 'REGION' AND region.code = j.work_region_code
                      LEFT JOIN demand_recruitment_demand d ON d.id = j.demand_id
                      LEFT JOIN demand_client c ON c.id = CASE WHEN j.recruitment_type IS NULL THEN d.client_id ELSE j.client_id END
                     WHERE j.id = #{id}
                    """)
    JdEntity findJd(String id);

    @SpecialSql("新业务与软删除、归属或可见性修改必须锁定同一 JD 行；FOR UPDATE 读取最新提交状态")
    @Select("""
            SELECT j.id, j.demand_id, j.source_owner_department_id, j.source_fingerprint, j.applied_from_jd_version_id,
                           j.code, j.job_type_code, j.work_region_code,
                           region.name AS work_region_name, j.distinction, j.public_visible, j.content_shared, j.deleted, j.recruitment_type, j.headcount, j.recruitment_cycle_days,
                           j.title, j.status, j.current_version_no,
                           CASE WHEN j.recruitment_type IS NULL THEN d.client_id ELSE j.client_id END AS client_id,
                           COALESCE(j.owner_user_id, d.owner_user_id, j.create_id) AS owner_user_id,
                           COALESCE(j.owner_department_id, d.owner_department_id, j.source_owner_department_id) AS owner_department_id,
                           j.owner_department_id AS catalog_owner_department_id,
                           j.closed_by_user_id, j.closed_at, j.version, j.ext, j.remark,
                           j.create_id, j.create_name, j.create_time,
                           j.update_id, j.update_name, j.update_time
              FROM demand_jd j
                      LEFT JOIN demand_jd_code_option region ON region.kind = 'REGION' AND region.code = j.work_region_code
              LEFT JOIN demand_recruitment_demand d ON d.id = j.demand_id
             WHERE j.id = #{id}
             FOR UPDATE
            """)
    JdEntity findJdForUpdate(String id);

    @SpecialSql("HC 编辑在 JD 行锁后执行锁定读，按逻辑 JD 跨版本读取当前 Offer 投影")
    @Select("""
            SELECT a.id FROM recruitment_application a
            JOIN recruitment_offer_status_record o ON o.application_id = a.id
            LEFT JOIN recruitment_offer_status_record newer ON newer.application_id = o.application_id
              AND (newer.create_time > o.create_time
                OR (newer.create_time = o.create_time AND newer.id > o.id))
            WHERE a.jd_id = #{jdId} AND """ + OfferStatus.OCCUPIED_SQL_PREDICATE + """
              AND newer.id IS NULL
            FOR UPDATE
            """)
    List<String> findOccupiedApplicationIdsForUpdate(String jdId);

    @SpecialSql("HC 展示按逻辑 JD 跨版本读取每条 Application 的当前 Offer 投影")
    @Select("""
            SELECT COUNT(*) FROM recruitment_application a
            JOIN recruitment_offer_status_record o ON o.application_id = a.id
            LEFT JOIN recruitment_offer_status_record newer ON newer.application_id = o.application_id
              AND (newer.create_time > o.create_time
                OR (newer.create_time = o.create_time AND newer.id > o.id))
            WHERE a.jd_id = #{jdId} AND """ + OfferStatus.OCCUPIED_SQL_PREDICATE + """
              AND newer.id IS NULL
            """)
    int countOccupiedHeadcount(String jdId);

    @SpecialSql("工作流模板版本号需要数据库聚合保证同一 JD 版本下的递增编号")
    @Select(
            "SELECT COALESCE(MAX(version_no), 0) FROM demand_workflow_template_version WHERE"
                    + " jd_version_id = #{jdVersionId}")
    /** 查询最大流程版本序号。 */
    int findMaxWorkflowVersionNo(String jdVersionId);

    @SpecialSql("激活模板前必须锁定同一 JD 版本下的模板集合以保护唯一活动槽")
    @Select(
            "SELECT id FROM demand_workflow_template_version WHERE jd_version_id = #{jdVersionId} FOR"
                    + " UPDATE")
    List<String> lockWorkflowTemplates(String jdVersionId);

    @SpecialSql("当前 JD 版本需要按 JD 当前版本号联查不可由单表 Wrapper 表达")
    @Select(
            """
                    SELECT v.id, v.jd_id, v.version_no, v.title, v.raw_text, v.status,
                           v.source_type, v.client_id, v.recruitment_type, v.work_region_code,
                           v.content_owner_user_id, v.content_fingerprint,
                           v.create_id, v.create_time, v.version,
                           v.ext, v.remark, v.create_id, v.create_name, v.create_time,
                           v.update_id, v.update_name, v.update_time
                      FROM demand_jd_version v
                      JOIN demand_jd j ON j.id = v.jd_id
                     WHERE j.demand_id = #{demandId} AND v.version_no = j.current_version_no
                    """)
    JdVersionEntity findCurrentJdVersionForDemand(String demandId);
}
