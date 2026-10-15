import {
  FileExcelOutlined,
  FileWordOutlined,
  ReloadOutlined,
} from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  App,
  Button,
  DatePicker,
  Descriptions,
  Empty,
  Input,
  Progress,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  Upload,
} from "antd";
import type { UploadFile } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { useEffect, useMemo, useRef, useState } from "react";
import { ApiProblemError } from "../api/problem";
import { WorkspaceDialog } from "../components/WorkspaceDialog";
import {
  activeMonthlyReportStatuses,
  monthlyReportApi,
  type MonthlyReportDetail,
  type MonthlyReportDimensionMetric,
  type MonthlyReportStatus,
  type MonthlyReportSummary,
  type MonthlyReportPage,
} from "./api";
import {
  formatMonthlyReportPercent,
  monthlyReportMetricRowKey,
} from "./presentation";
import {
  fileObjectFingerprint,
  resolveIdempotencyDraft,
  type IdempotencyDraft,
} from "./idempotencyDraft";
import { ReportDiagnostics } from "./ReportDiagnostics";
import "./monthly-reports.css";

const FILE_LIMIT = 10 * 1024 * 1024;
const statusCopy: Record<
  MonthlyReportStatus,
  { label: string; color: string }
> = {
  UPLOADING: { label: "上传中", color: "processing" },
  VALIDATING: { label: "校验中", color: "processing" },
  INVALID: { label: "校验未通过", color: "error" },
  READY: { label: "可生成", color: "success" },
  QUEUED: { label: "等待生成", color: "processing" },
  RUNNING: { label: "生成中", color: "processing" },
  SUCCEEDED: { label: "已生成", color: "success" },
  FAILED: { label: "生成失败", color: "error" },
  CANCELLING: { label: "取消中", color: "warning" },
  CANCELLED: { label: "已取消", color: "default" },
  EXPIRED: { label: "文件已过期", color: "default" },
};
const errorText = (error: unknown) =>
  error instanceof ApiProblemError
    ? error.problem.detail
    : error instanceof Error
      ? error.message
      : "操作失败，请稍后重试。";
const fileFrom = (value: UploadFile[]) =>
  value[0]?.originFileObj instanceof File ? value[0].originFileObj : undefined;
function StatusTag({ status }: { status: MonthlyReportStatus }) {
  const copy = statusCopy[status];
  return <Tag color={copy.color}>{copy.label}</Tag>;
}

function MetricTable({
  title,
  items,
}: {
  title: string;
  items: MonthlyReportDimensionMetric[];
}) {
  if (!items.length) return null;
  return (
    <section>
      <Typography.Title level={5}>{title}</Typography.Title>
      <div className="monthly-report-breakdown">
        <Table
          size="small"
          pagination={items.length > 10 ? { pageSize: 10 } : false}
          rowKey={monthlyReportMetricRowKey}
          dataSource={items}
          columns={[
            {
              title: "名称",
              dataIndex: "key",
              fixed: "left",
              width: 180,
              render: (value: string, row) => (
                <Space orientation="vertical" size={0}>
                  <span>{value}</span>
                  {row.group ? (
                    <Typography.Text type="secondary">
                      {row.group}
                    </Typography.Text>
                  ) : null}
                </Space>
              ),
            },
            { title: "Offer 数量", dataIndex: "count", width: 110 },
            { title: "分值", dataIndex: "score", width: 100 },
            { title: "平均每条分值", dataIndex: "averageScore", width: 130 },
            {
              title: "数量占比",
              dataIndex: "countShare",
              width: 110,
              render: formatMonthlyReportPercent,
            },
            {
              title: "分值贡献",
              dataIndex: "scoreShare",
              width: 110,
              render: formatMonthlyReportPercent,
            },
          ]}
        />
      </div>
    </section>
  );
}

function ReportPreview({
  report,
  onChanged,
}: {
  report: MonthlyReportDetail;
  onChanged: (value: MonthlyReportDetail) => void;
}) {
  const { message } = App.useApp();
  const client = useQueryClient();
  const [remarks, setRemarks] = useState(report.remarks ?? "");
  const [ack, setAck] = useState(false);
  const actionDraft = useRef<IdempotencyDraft | undefined>(undefined);
  const refresh = async (value: MonthlyReportDetail) => {
    onChanged(value);
    client.setQueryData(["monthly-reports", "detail", value.id], value);
    await client.invalidateQueries({ queryKey: ["monthly-reports", "list"] });
  };
  const mutation = useMutation({
    mutationFn: async (action: "generate" | "retry" | "cancel") => {
      const fingerprint = JSON.stringify({
        action,
        id: report.id,
        version: report.version,
        ...(action === "generate"
          ? { remarks: remarks.trim(), acknowledgeWarnings: ack }
          : {}),
      });
      actionDraft.current = resolveIdempotencyDraft(
        actionDraft.current,
        fingerprint,
      );
      const key = actionDraft.current.key;
      if (action === "generate")
        return monthlyReportApi.generate(
          report.id,
          {
            version: report.version,
            remarks: remarks.trim() || null,
            acknowledgeWarnings: ack,
          },
          key,
        );
      if (action === "retry")
        return monthlyReportApi.retry(
          report.id,
          report.version,
          "重新生成月度汇报",
          key,
        );
      return monthlyReportApi.cancel(
        report.id,
        report.version,
        "用户在月度汇报工作区取消任务",
        key,
      );
    },
    onSuccess: async (value) => {
      actionDraft.current = undefined;
      await refresh(value);
      void message.success("操作已提交");
    },
  });
  const download = useMutation({
    mutationFn: () =>
      monthlyReportApi.download(
        report.id,
        report.files.find((file) => file.kind === "REPORT")?.fileName ??
          `${report.month}-招聘月度汇报.docx`,
      ),
  });
  const snapshot = report.snapshot;
  const emptyReady = report.status === "READY" && snapshot?.offerSummary.totalCount === 0;
  const warnings = report.diagnostics.filter(
    (value) => value.severity === "WARNING",
  );
  return (
    <div className="monthly-report-preview">
      <Space wrap>
        <StatusTag status={emptyReady ? "INVALID" : report.status} />
        <Typography.Text type="secondary">
          报告月 {report.month} · 数据截至 {report.asOfDate}
        </Typography.Text>
      </Space>
      {activeMonthlyReportStatuses.has(report.status) ? (
        <Progress
          percent={report.progressPercent ?? 0}
          status={report.status === "CANCELLING" ? "exception" : "active"}
          aria-label={`处理进度 ${report.progressPercent ?? 0}%`}
        />
      ) : null}
      {emptyReady ? <Alert type="error" showIcon title="所选月份没有 Offer 数据，不能生成汇报" description="请核对 Offer 发送日期，选择对应的报告月份后重新上传校验。" /> : null}
      {report.errorDetail ? (
        <Alert type="error" showIcon title={report.errorDetail} />
      ) : null}
      <ReportDiagnostics items={report.diagnostics} />
      {snapshot ? (
        <>
          <div className="monthly-report-summary">
            <div>
              <span>Offer 数量</span>
              <b>{snapshot.offerSummary.totalCount}</b>
            </div>
            <div>
              <span>计分有效数量</span>
              <b>{snapshot.offerSummary.scoredCount}</b>
            </div>
            <div>
              <span>总分</span>
              <b>{snapshot.offerSummary.totalScore.toFixed(2)}</b>
            </div>
          </div>
          <Descriptions
            size="small"
            bordered
            column={{ xs: 1, sm: 2, md: 4 }}
            items={[
              {
                key: "onboarded",
                label: "已入职 Offer",
                children:
                  snapshot.offerSummary.statusCounts["已入职offer"] ?? 0,
              },
              {
                key: "accepted",
                label: "已接受 Offer",
                children:
                  snapshot.offerSummary.statusCounts["已接受offer"] ?? 0,
              },
              {
                key: "rejected",
                label: "已拒绝 Offer",
                children:
                  snapshot.offerSummary.statusCounts["已拒绝offer"] ?? 0,
              },
              {
                key: "sent",
                label: "已发 Offer",
                children: snapshot.offerSummary.statusCounts["已发offer"] ?? 0,
              },
              {
                key: "rejectedRate",
                label: "拒绝占比",
                children: formatMonthlyReportPercent(
                  snapshot.offerSummary.rejectedRate,
                ),
              },
              {
                key: "acceptedRate",
                label: "接受及入职占比",
                children: formatMonthlyReportPercent(
                  snapshot.offerSummary.acceptedOrOnboardedRate,
                ),
              },
              {
                key: "onboardedRate",
                label: "已入职占比",
                children: formatMonthlyReportPercent(
                  snapshot.offerSummary.onboardedRate,
                ),
              },
            ]}
          />
          <Alert
            type="info"
            showIcon
            title="需求数据独立展示"
            description="需求表与 Offer 表没有稳定的共同需求编号，因此不计算招聘完成率或缺口。"
          />
          <Descriptions
            size="small"
            bordered
            column={{ xs: 1, sm: 2, md: 3 }}
            items={[
              {
                key: "approved",
                label: "审批通过需求",
                children: `${snapshot.demandSummary.approvedDemandCount} 项 / ${snapshot.demandSummary.approvedHeadcount} 人`,
              },
              {
                key: "applied",
                label: "本月申请",
                children: `${snapshot.demandSummary.monthAppliedDemandCount} 项 / ${snapshot.demandSummary.monthAppliedHeadcount} 人`,
              },
              {
                key: "expected",
                label: "本月期望到岗",
                children: `${snapshot.demandSummary.monthExpectedDemandCount} 项 / ${snapshot.demandSummary.monthExpectedHeadcount} 人`,
              },
            ]}
          />
          {snapshot.analysis.length ? (
            <section>
              <Typography.Title level={5}>事实与后续关注</Typography.Title>
              {snapshot.analysis.map((value) => (
                <Alert
                  key={value.ruleId}
                  type="info"
                  showIcon={false}
                  title={value.text}
                  description={value.recommendation}
                />
              ))}
            </section>
          ) : null}
          {Array.from(
            new Set(snapshot.dimensions.map((item) => item.dimension)),
          ).map((key) => (
            <MetricTable
              key={key}
              title={
                (
                  {
                    employment: "雇佣关系",
                    group: "Offer 小组",
                    department: "一级部门",
                    channel: "人员来源",
                    gradeBand: "职级分档",
                    sender: "Offer 发送人",
                  } as Record<string, string>
                )[key] ?? key
              }
              items={snapshot.dimensions.filter(
                (item) => item.dimension === key,
              )}
            />
          ))}
          <Typography.Text type="secondary">
            规则版本 {snapshot.ruleVersion}
          </Typography.Text>
        </>
      ) : report.status === "READY" ? (
        <Alert
          type="warning"
          title="内容预览尚未返回"
          description="刷新详情后再确认生成，避免在缺少快照时冻结报告。"
        />
      ) : (
        <div className="monthly-report-empty">
          完成校验后将在这里显示内容预览。
        </div>
      )}
      {(report.status === "READY" && !emptyReady) || report.status === "FAILED" ? (
        <Input.TextArea
          aria-label="管理备注"
          value={remarks}
          maxLength={2000}
          showCount
          rows={3}
          placeholder="选填；备注与固定统计内容分区保存"
          onChange={(event) => setRemarks(event.target.value)}
        />
      ) : null}
      {report.status === "READY" && !emptyReady && warnings.length ? (
        <label>
          <input
            aria-label="确认校验警告"
            type="checkbox"
            checked={ack}
            onChange={(event) => setAck(event.target.checked)}
          />{" "}
          已阅读并确认上述警告，按当前快照生成
        </label>
      ) : null}
      {mutation.error ? (
        <Alert
          type="error"
          showIcon
          title={errorText(mutation.error)}
          action={
            <Button
              onClick={() =>
                void client.invalidateQueries({
                  queryKey: ["monthly-reports", "detail", report.id],
                })
              }
            >
              刷新状态
            </Button>
          }
        />
      ) : null}
      {download.error ? (
        <Alert
          type="error"
          showIcon
          title={errorText(download.error)}
          action={<Button onClick={() => download.mutate()}>重试下载</Button>}
        />
      ) : null}
      <div className="monthly-report-actions">
        {report.status === "READY" && !emptyReady ? (
          <Button
            type="primary"
            icon={<FileWordOutlined />}
            disabled={!snapshot || Boolean(warnings.length && !ack)}
            loading={mutation.isPending}
            onClick={() => mutation.mutate("generate")}
          >
            生成 Word 汇报
          </Button>
        ) : null}
        {report.status === "FAILED" && report.retryable ? (
          <Button
            type="primary"
            loading={mutation.isPending}
            onClick={() => mutation.mutate("retry")}
          >
            重试生成
          </Button>
        ) : null}
        {["UPLOADING", "VALIDATING", "QUEUED", "RUNNING"].includes(
          report.status,
        ) ? (
          <Button
            danger
            loading={mutation.isPending}
            onClick={() => mutation.mutate("cancel")}
          >
            取消任务
          </Button>
        ) : null}
        {report.status === "SUCCEEDED" ? (
          <Button
            type="primary"
            icon={<FileWordOutlined />}
            loading={download.isPending}
            onClick={() => download.mutate()}
          >
            下载 DOCX
          </Button>
        ) : null}
      </div>
    </div>
  );
}

export function MonthlyReportsPage() {
  const { message } = App.useApp();
  const client = useQueryClient();
  const today = dayjs();
  const [month, setMonth] = useState<Dayjs>(() => dayjs().subtract(1, "month").startOf("month"));
  const [asOf, setAsOf] = useState<Dayjs>(today);
  const [offerFiles, setOfferFiles] = useState<UploadFile[]>([]);
  const [demandFiles, setDemandFiles] = useState<UploadFile[]>([]);
  const [filterMonth, setFilterMonth] = useState<string | undefined>(undefined);
  const [cursors, setCursors] = useState<string[]>([]);
  const [selectedId, setSelectedId] = useState<string | undefined>(undefined);
  const [deleteTarget, setDeleteTarget] = useState<MonthlyReportSummary>();
  const [deleteReason, setDeleteReason] = useState("");
  const uploadDraft = useRef<IdempotencyDraft | undefined>(undefined);
  const fileIdentities = useRef(new WeakMap<File, string>());
  const cursor = cursors.at(-1);
  const history = useQuery({
    queryKey: ["monthly-reports", "list", filterMonth, cursor],
    queryFn: () => monthlyReportApi.list(filterMonth, cursor),
    retry: false,
  });
  const effectiveSelectedId = selectedId ?? history.data?.items[0]?.id;
  const detail = useQuery({
    queryKey: ["monthly-reports", "detail", effectiveSelectedId],
    queryFn: ({ signal }) =>
      monthlyReportApi.detail(effectiveSelectedId!, signal),
    enabled: Boolean(effectiveSelectedId),
    retry: false,
    refetchInterval: (query) =>
      query.state.data &&
      activeMonthlyReportStatuses.has(query.state.data.status)
        ? 3000
        : false,
  });
  const selected = detail.data;
  const selectedStatus = selected?.status;
  useEffect(() => {
    if (selectedStatus && !activeMonthlyReportStatuses.has(selectedStatus))
      void client.invalidateQueries({ queryKey: ["monthly-reports", "list"] });
  }, [client, selectedStatus]);
  const upload = useMutation({
    mutationFn: () => {
      const offerFile = fileFrom(offerFiles),
        demandFile = fileFrom(demandFiles);
      if (!offerFile || !demandFile)
        throw new Error("请选择 Offer 和招聘需求两份 Excel");
      const fingerprint = JSON.stringify({
        offerFile: fileObjectFingerprint(offerFile, fileIdentities.current),
        demandFile: fileObjectFingerprint(demandFile, fileIdentities.current),
        month: month.format("YYYY-MM"),
        asOfDate: asOf.format("YYYY-MM-DD"),
      });
      uploadDraft.current = resolveIdempotencyDraft(
        uploadDraft.current,
        fingerprint,
      );
      return monthlyReportApi.create(
        {
          offerFile,
          demandFile,
          month: month.format("YYYY-MM"),
          asOfDate: asOf.format("YYYY-MM-DD"),
        },
        uploadDraft.current.key,
      );
    },
    onSuccess: async (value) => {
      uploadDraft.current = undefined;
      setOfferFiles([]);
      setDemandFiles([]);
      setSelectedId(value.id);
      client.setQueryData(["monthly-reports", "detail", value.id], value);
      await client.invalidateQueries({ queryKey: ["monthly-reports", "list"] });
      void message.success("文件已上传，正在校验固定规则");
    },
  });
  const deletion = useMutation({
    mutationFn: (target: MonthlyReportSummary) =>
      monthlyReportApi.delete(target.id, target.version, deleteReason.trim()),
    onSuccess: async (_, target) => {
      await client.cancelQueries({ queryKey: ["monthly-reports", "detail", target.id] });
      await client.cancelQueries({ queryKey: ["monthly-reports", "list"] });
      client.setQueriesData<MonthlyReportPage>(
        { queryKey: ["monthly-reports", "list"] },
        (value) => value ? { ...value, items: value.items.filter((item) => item.id !== target.id) } : value,
      );
      setSelectedId((value) => value === target.id ? undefined : value);
      client.removeQueries({ queryKey: ["monthly-reports", "detail", target.id], exact: true });
      if (history.data?.items.length === 1 && cursors.length) setCursors([]);
      setDeleteTarget(undefined);
      setDeleteReason("");
      void message.success("汇报已删除");
      await client.invalidateQueries({ queryKey: ["monthly-reports", "list"] });
    },
  });
  const invalidDate =
    asOf.isBefore(month.startOf("month"), "day") || asOf.isAfter(today, "day");
  const invalidFiles = [fileFrom(offerFiles), fileFrom(demandFiles)].some(
    (file) => file && file.size > FILE_LIMIT,
  );
  const resetDeletion = deletion.reset;
  const columns = useMemo(
    () => [
      { title: "报告月份", dataIndex: "month", width: 110 },
      { title: "数据截至", dataIndex: "asOfDate", width: 120 },
      {
        title: "状态",
        dataIndex: "status",
        width: 120,
        render: (value: MonthlyReportStatus) => <StatusTag status={value} />,
      },
      {
        title: "Offer",
        dataIndex: "offerCount",
        width: 90,
        render: (value: number | null) => value ?? "—",
      },
      {
        title: "总分",
        dataIndex: "totalScore",
        width: 100,
        render: (value: number | null) =>
          value === null ? "—" : value.toFixed(2),
      },
      {
        title: "创建时间",
        dataIndex: "createTime",
        width: 170,
        render: (value: string) => dayjs(value).format("YYYY-MM-DD HH:mm"),
      },
      {
        title: "操作", className: 'table-action-cell',
        key: "action",
        fixed: "right" as const,
        width: 140,
        render: (_: unknown, row: MonthlyReportSummary) => (
          <Space size={6}>
          <Button type="link" onClick={() => setSelectedId(row.id)}>
            查看
          </Button>
          <Tooltip title={activeMonthlyReportStatuses.has(row.status) ? "任务处理中，请先取消任务或等待处理完成" : undefined}>
            <span>
              <Button type="link" danger disabled={activeMonthlyReportStatuses.has(row.status)}
                onClick={() => { resetDeletion(); setDeleteReason(""); setDeleteTarget(row); }}>
                删除
              </Button>
            </span>
          </Tooltip>
          </Space>
        ),
      },
    ],
    [resetDeletion],
  );
  return (
    <div className="monthly-reports-page">
      <WorkspaceDialog title="删除月度汇报" open={Boolean(deleteTarget)}
        okText="确认删除" cancelText="取消" confirmLoading={deletion.isPending}
        okButtonProps={{ danger: true, disabled: !deleteReason.trim() }}
        cancelButtonProps={{ disabled: deletion.isPending }}
        closable={!deletion.isPending} keyboard={!deletion.isPending}
        mask={{ closable: !deletion.isPending }}
        onCancel={() => { if (!deletion.isPending) setDeleteTarget(undefined); }}
        onOk={() => { if (deleteTarget && !deletion.isPending && deleteReason.trim()) deletion.mutate(deleteTarget); }}>
        <p>确认删除 {deleteTarget?.month} 的月度汇报？删除后，汇报和源文件将无法再查看或下载，实际文件稍后由系统回收。此操作无法撤销。</p>
        <Input.TextArea aria-label="删除原因" placeholder="请填写删除原因" maxLength={500}
          value={deleteReason} disabled={deletion.isPending}
          onChange={(event) => setDeleteReason(event.target.value)} />
        {deletion.error ? <Alert type="error" showIcon title="删除失败，汇报仍保留"
          description={errorText(deletion.error)}
          action={<Button onClick={() => { setDeleteTarget(undefined); void history.refetch(); }}>刷新状态</Button>} /> : null}
      </WorkspaceDialog>
      <header className="ledger-heading">
        <div>
          <Typography.Title level={1}>招聘月度汇报</Typography.Title>
          <Typography.Paragraph>上传两份 Excel，校验固定口径，预览后生成可编辑 Word 汇报。</Typography.Paragraph>
        </div>
        <Tag color="blue">国内范围 · 固定统计口径</Tag>
      </header>
      <div className="monthly-report-grid">
        <section className="monthly-report-panel">
          <Typography.Title level={4}>新建汇报</Typography.Title>
          <div className="monthly-report-form">
            <label>
              报告月份
              <DatePicker.MonthPicker
                aria-label="报告月份"
                value={month}
                allowClear={false}
                style={{ width: "100%" }}
                onChange={(value) => {
                  if (value) {
                    setMonth(value);
                    if (asOf.isBefore(value.startOf("month"), "day"))
                      setAsOf(value.endOf("month"));
                  }
                }}
              />
            </label>
            <label>
              数据截至日期
              <DatePicker
                aria-label="数据截至日期"
                value={asOf}
                allowClear={false}
                maxDate={today}
                style={{ width: "100%" }}
                onChange={(value) => value && setAsOf(value)}
              />
            </label>
            <Alert
              className="monthly-report-period-note"
              type="info"
              showIcon
              title={`将统计 ${month.format("YYYY年MM月")} 发送的 Offer`}
              description="报告月份默认上个月，请与 Offer 发送日期核对；上传日期不决定统计月份。数据截至日期填写源表所反映状态的日期。"
            />
            <div className="monthly-report-upload" role="group" aria-label="Offer 明细 Excel">
              <Typography.Text strong>Offer 明细 Excel</Typography.Text>
              <Upload.Dragger
                accept=".xlsx"
                maxCount={1}
                beforeUpload={() => false}
                fileList={offerFiles}
                onChange={({ fileList }) => setOfferFiles(fileList)}
              >
                <FileExcelOutlined />
                <p>选择 Offer 明细（.xlsx，≤ 10 MiB）</p>
              </Upload.Dragger>
            </div>
            <div className="monthly-report-upload" role="group" aria-label="招聘需求 Excel">
              <Typography.Text strong>招聘需求 Excel</Typography.Text>
              <Upload.Dragger
                accept=".xlsx"
                maxCount={1}
                beforeUpload={() => false}
                fileList={demandFiles}
                onChange={({ fileList }) => setDemandFiles(fileList)}
              >
                <FileExcelOutlined />
                <p>选择招聘需求（.xlsx，≤ 10 MiB）</p>
              </Upload.Dragger>
            </div>
            {invalidDate ? (
              <Alert
                className="full"
                type="error"
                showIcon
                title="数据截至日期须在报告月首日至今天之间"
              />
            ) : null}
            {invalidFiles ? (
              <Alert
                className="full"
                type="error"
                showIcon
                title="单份文件不能超过 10 MiB"
              />
            ) : null}
            {upload.error ? (
              <Alert
                className="full"
                type="error"
                showIcon
                title={errorText(upload.error)}
              />
            ) : null}
            <Button
              className="full monthly-report-submit"
              type="primary"
              loading={upload.isPending}
              disabled={
                invalidDate ||
                invalidFiles ||
                !fileFrom(offerFiles) ||
                !fileFrom(demandFiles)
              }
              onClick={() => upload.mutate()}
            >
              上传并校验
            </Button>
          </div>
        </section>
        <section className="monthly-report-panel">
          <Typography.Title level={4}>内容预览</Typography.Title>
          {detail.isPending && effectiveSelectedId ? (
            <div className="monthly-report-empty">正在读取报告详情…</div>
          ) : detail.error ? (
            <Alert
              type="error"
              showIcon
              title="详情加载失败"
              description={errorText(detail.error)}
              action={
                <Button onClick={() => void detail.refetch()}>重试</Button>
              }
            />
          ) : selected ? (
            <ReportPreview
              key={`${selected.id}-${selected.version}`}
              report={selected}
              onChanged={(value) =>
                client.setQueryData(
                  ["monthly-reports", "detail", value.id],
                  value,
                )
              }
            />
          ) : (
            <div className="monthly-report-empty">
              上传新文件或从历史记录选择一份汇报。
            </div>
          )}
        </section>
      </div>
      <section className="monthly-report-panel">
        <div className="monthly-reports-header">
          <Typography.Title level={4}>历史汇报</Typography.Title>
          <Space>
            <Select
              aria-label="按月份筛选"
              allowClear
              placeholder="全部月份"
              value={filterMonth}
              options={Array.from({ length: 12 }, (_, index) => {
                const value = today.subtract(index, "month").format("YYYY-MM");
                return { value, label: value };
              })}
              onChange={(value) => {
                setFilterMonth(value);
                setCursors([]);
                setSelectedId(undefined);
              }}
            />
            <Button
              aria-label="刷新历史"
              icon={<ReloadOutlined />}
              onClick={() => void history.refetch()}
            />
          </Space>
        </div>
        {history.error ? (
          <Alert
            type="error"
            showIcon
            title="历史汇报加载失败"
            description={errorText(history.error)}
            action={
              <Button onClick={() => void history.refetch()}>重试</Button>
            }
          />
        ) : (
          <div className="monthly-report-history">
            <Table<MonthlyReportSummary>
              loading={history.isPending}
              rowKey="id"
              dataSource={history.data?.items ?? []}
              columns={columns}
              pagination={false}
              locale={{
                emptyText: <Empty description="还没有月度汇报" />,
              }}
            />
          </div>
        )}
        <div className="monthly-report-actions">
          <Button
            disabled={!cursors.length}
            onClick={() => setCursors((value) => value.slice(0, -1))}
          >
            上一页
          </Button>
          <Button
            disabled={!history.data?.nextCursor}
            onClick={() => {
              const nextCursor = history.data?.nextCursor;
              if (nextCursor) setCursors((value) => [...value, nextCursor]);
            }}
          >
            下一页
          </Button>
        </div>
      </section>
    </div>
  );
}
