import { describe, expect, it } from "vitest";
import {
  formatMonthlyReportPercent,
  monthlyReportMetricRowKey,
} from "./presentation";

describe("monthly report presentation", () => {
  it("shows percentage points directly and preserves an unknown denominator", () => {
    expect(formatMonthlyReportPercent(12.3)).toBe("12.3%");
    expect(formatMonthlyReportPercent(0)).toBe("0%");
    expect(formatMonthlyReportPercent(null)).toBe("—");
  });

  it("keeps same-name senders in different groups as distinct rows", () => {
    const base = {
      dimension: "sender",
      key: "张三",
      count: 1,
      score: 1,
      averageScore: 1,
      statusCounts: {},
      countShare: 10,
      scoreShare: 10,
    };
    expect(monthlyReportMetricRowKey({ ...base, group: "示例组甲" })).not.toBe(
      monthlyReportMetricRowKey({ ...base, group: "示例组乙" }),
    );
  });
});
