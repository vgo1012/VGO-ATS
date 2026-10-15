import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    target: 'es2022',
    assetsDir: '_build',
    sourcemap: process.env.GENERATE_SOURCEMAP === 'true',
    chunkSizeWarningLimit: 550,
    rolldownOptions: {
      output: {
        strictExecutionOrder: true,
        codeSplitting: {
          groups: [
            {
              name: 'react-vendor',
              test: /node_modules[\\/](?:react|react-dom|react-router|scheduler)[\\/]/,
              priority: 30,
            },
            {
              name: 'ui-vendor',
              test: /node_modules[\\/](?:antd|@ant-design|@rc-component)[\\/]/,
              maxSize: 300_000,
              priority: 20,
            },
            {
              name: 'vendor',
              test: /node_modules[\\/]/,
              maxSize: 300_000,
              priority: 10,
            },
          ],
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    include: ['src/**/*.test.{ts,tsx}'],
    css: true,
    // Ant Design 组件树在 jsdom 下极耗 CPU；16 核机器并行两个 worker 仍会让单项测试超过 15 秒。
    maxWorkers: 1,
    // 与既有用例的 15s 显式上限对齐，作为限流之外的第二道防线。
    testTimeout: 15_000,
    hookTimeout: 15_000,
    coverage: {
      reporter: ['text', 'html'],
    },
  },
});
