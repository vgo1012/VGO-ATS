import type { MonthlyReportDimensionMetric } from "./api";

export const monthlyReportMetricRowKey = (
  value: MonthlyReportDimensionMetric,
) => `${value.dimension}-${value.group ?? ""}-${value.key}`;

export const formatMonthlyReportPercent = (value: number | null) =>
  value === null ? "—" : `${value}`.replace(/\.0+$/, "") + "%";
