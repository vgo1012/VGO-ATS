package com.ezaigc.hr.intelligence.search.application.provider;

import com.ezaigc.hr.intelligence.search.domain.dto.IndexPage;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchCriteria;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchRebuildAttempt;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import java.util.List;

/** 搜索索引模块能力端口。 */
public interface SearchIndexPort {
  boolean durable();

  /** 只供首次受控导入核验远端别名候选，业务查询和增量写入不得据此路由。 */
  String activeIndex();

  /** 查询只使用 MySQL 批准的物理目标，游标绑定其代际。 */
  IndexPage search(SearchIndexTarget target, SearchCriteria criteria, String cursor, int limit);

  /** 只供首次初始化核验；标准 mapping/名称前缀本身不证明初始批准来源。 */
  boolean isManagedBootstrap(String indexName);

  /** 验证批准物理目标，不得在持久搜索后端缺失时自动改选别名或创建替代索引。 */
  void ensureApprovedIndex(SearchIndexTarget target);

  /** 创建只允许在对应持久 CREATING 意图登记后执行一次。 */
  void createBootstrap(String indexName, String resourceId);

  void createRebuildTarget(SearchRebuildAttempt attempt, String resourceId);

  /** 精确验证资源 marker 后删除物理目标及写 alias；不存在视为成功。 */
  void retireIndex(String indexName, String resourceId);

  /** 人工核对原 IO 已终止后纳管精确目标，补 marker/写 alias；404 返回 false。 */
  boolean adoptIndex(String indexName, String resourceId);

  /** 仅用于 ledger 与远端 marker 的固定归属边界，不执行外部 IO。 */
  String resourceScope();

  /** 在批准事务外确保完整目标已经可以查询。 */
  void prepareRebuild(SearchRebuildAttempt attempt);

  /** 创建独立目标并在搜索系统中声明批次；较早批次不得撤销较新声明。 */
  void beginRebuild(SearchRebuildAttempt attempt);

  /** 按相同内容版本规则写入指定实际索引；单页不能随业务别名切换改变目标。 */
  void upsertBatch(SearchIndexTarget target, List<SearchDocument> documents);

  /** 准备远端别名并原子核验搜索侧批次；业务生效仍需 MySQL 批准事务成功。 */
  void activateRebuild(SearchRebuildAttempt attempt);
}
