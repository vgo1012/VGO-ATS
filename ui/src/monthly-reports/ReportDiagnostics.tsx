import { Alert } from "antd";
import type { MonthlyReportDiagnostic } from "./api";

export function ReportDiagnostics({ items }: { items: MonthlyReportDiagnostic[] }) {
  if (!items.length) return null;
  const groups = new Map<string, [MonthlyReportDiagnostic, ...MonthlyReportDiagnostic[]]>();
  for (const item of items) {
    const key = JSON.stringify([item.severity, item.code, item.message]);
    const group = groups.get(key);
    if (group) group.push(item);
    else groups.set(key, [item]);
  }
  const hasErrors = items.some((item) => item.severity === "ERROR");
  const count = (values: MonthlyReportDiagnostic[]) =>
    values.reduce((total, item) => total + (item.count ?? 1), 0);
  return (
    <Alert
      className="monthly-report-validation"
      type={hasErrors ? "error" : "warning"}
      showIcon
      title={`${hasErrors ? "校验未通过" : "校验提示"}：${count(items)} 项`}
      description={
        <details open={hasErrors}>
          <summary>查看校验明细（{groups.size} 类）</summary>
          <div className="monthly-report-diagnostics" role="region" aria-label="校验明细" tabIndex={0}>
            {Array.from(groups, ([key, values]) => (
              <section key={key}>
                <strong>{values[0].message}（{count(values)} 项）</strong>
                <ul>
                  {values.map((value, index) => (
                    <li key={index} className="monthly-report-diagnostic-source">
                      {[
                        value.source,
                        value.sheet,
                        value.row != null && `第 ${value.row} 行`,
                        value.column,
                      ].filter(Boolean).join(" · ") || "未提供行号"}
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        </details>
      }
    />
  );
}
