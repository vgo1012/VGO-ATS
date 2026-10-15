import { afterEach, expect, it, vi } from "vitest";
import { downloadFile } from "../api/download";
import { request } from "../api/http";
import { monthlyReportApi } from "./api";

vi.mock("../api/http", () => ({ request: vi.fn() }));
vi.mock("../api/download", () => ({ downloadFile: vi.fn() }));
afterEach(() => vi.resetAllMocks());

it("deletes with version and reason without an idempotency key", async () => {
  vi.mocked(request).mockResolvedValue(undefined);
  await expect(monthlyReportApi.delete("report-1", "3", "误上传")).resolves.toBeUndefined();
  expect(request).toHaveBeenCalledWith("/monthly-reports/report-1", {
    method: "DELETE", body: { version: "3", reason: "误上传" },
  });
});

it("encodes bounded history filters and cursors", async () => {
  vi.mocked(request).mockResolvedValue({ items: [], nextCursor: null });
  await monthlyReportApi.list("2026-08", "next+/=");
  expect(request).toHaveBeenCalledWith(
    "/monthly-reports?limit=20&month=2026-08&cursor=next%2B%2F%3D",
  );
});

it("uploads the two spreadsheets with the fixed multipart field names", async () => {
  vi.mocked(request).mockResolvedValue({});
  const offer = new File(["offer"], "offer.xlsx");
  const demand = new File(["demand"], "demand.xlsx");
  await monthlyReportApi.create(
    {
      offerFile: offer,
      demandFile: demand,
      month: "2026-08",
      asOfDate: "2026-09-14",
    },
    "key-1",
  );
  const [path, options] = vi.mocked(request).mock.calls[0]!;
  const body = options?.body as FormData;
  expect(path).toBe("/monthly-reports");
  expect(options).toMatchObject({ method: "POST", idempotencyKey: "key-1" });
  expect(body.get("offerFile")).toBe(offer);
  expect(body.get("demandFile")).toBe(demand);
  expect(body.get("month")).toBe("2026-08");
  expect(body.get("asOfDate")).toBe("2026-09-14");
});

it("sends optimistic versions and downloads through the guarded file client", async () => {
  vi.mocked(request).mockResolvedValue({});
  await monthlyReportApi.generate(
    "report-1",
    { version: "3", remarks: "关注拒绝原因", acknowledgeWarnings: true },
    "key-2",
  );
  expect(request).toHaveBeenCalledWith("/monthly-reports/report-1/generate", {
    method: "POST",
    body: { version: "3", remarks: "关注拒绝原因", acknowledgeWarnings: true },
    idempotencyKey: "key-2",
  });
  await monthlyReportApi.download("report-1", "月报.docx");
  expect(downloadFile).toHaveBeenCalledWith(
    "/monthly-reports/report-1/download",
    "月报.docx",
  );
});
