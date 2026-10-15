import { requestFile } from './http';

export async function downloadFile(path: string, fileName: string): Promise<void> {
  const blob = await requestFile(path);
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName || '下载文件';
  anchor.hidden = true;
  document.body.append(anchor);
  try {
    anchor.click();
  } finally {
    anchor.remove();
    // Allow the browser to consume the URL before releasing the file buffer.
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  }
}
