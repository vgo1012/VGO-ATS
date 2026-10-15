/** 最多保留 10 条搜索条件。 */
export const MAX_SEARCH_CONDITIONS = 10;

/** 按搜索栏的既有语义合并条件：去空、去重并截断到上限。首项为主关键词，其余为附加条件。 */
export function mergeSearchTerms(terms: string[]) {
  return [...new Set(terms.map(term => term.trim()).filter(Boolean))].slice(0, MAX_SEARCH_CONDITIONS);
}
