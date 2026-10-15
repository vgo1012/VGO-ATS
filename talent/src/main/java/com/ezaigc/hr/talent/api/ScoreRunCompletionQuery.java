package com.ezaigc.hr.talent.api;

import java.util.Collection;
import java.util.Set;

/** 人才模块校验已绑定的公司默认评分是否完整成功；调用方负责候选人与简历的数据范围。 */
public interface ScoreRunCompletionQuery {
  /** 单次查询上限；完整历史由调用方分批查询并合并成功运行。 */
  int MAX_RUNS_PER_BATCH = 1000;

  Set<String> successfulRunIds(Collection<String> runIds);
}
