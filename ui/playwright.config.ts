import { defineConfig, devices } from '@playwright/test';

const browserChannel = (process.env.PLAYWRIGHT_CHANNEL
  ?? (process.platform === 'win32' && !process.env.CI ? 'chrome' : undefined)) as 'chrome' | 'msedge' | undefined;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  reporter: 'html',
  use: {
    baseURL: 'http://127.0.0.1:4173',
    trace: 'on-first-retry',
    ...(browserChannel ? { channel: browserChannel } : {}),
  },
  webServer: {
    command: 'npm run build && npm run preview -- --host 127.0.0.1',
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
  ],
});
