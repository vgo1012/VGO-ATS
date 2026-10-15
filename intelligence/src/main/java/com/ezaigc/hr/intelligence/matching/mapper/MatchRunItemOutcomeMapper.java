package com.ezaigc.hr.intelligence.matching.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRunItemOutcome;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MatchRunItemOutcomeMapper extends BaseMapper<MatchRunItemOutcome> {
  /** 终态统计使用有界当前读，避免可重复读视图遗漏已提交结果。 */
  @SpecialSql("锁定运行条目并读取最新状态，最多多读一项检测越界数据")
  @Select("SELECT status FROM matching_match_run_item_outcome WHERE match_run_id=#{runId} LIMIT #{limit} FOR UPDATE")
  List<String> lockOutcomeStatuses(String runId, int limit);
}
