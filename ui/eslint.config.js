import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import tseslint from 'typescript-eslint';

const sourceFiles = ['src/**/*.{ts,tsx}'];
const toolingFiles = ['e2e/**/*.ts', '*.config.ts'];

export default tseslint.config(
  // 浏览器测试会重建这些产物目录，避免 lint 遍历时与清理操作竞争。
  { ignores: ['.agents/**', 'dist', 'coverage', 'test-results', 'playwright-report', 'ui-prototypes/**', 'node_modules'] },
  { ...js.configs.recommended, files: ['*.{js,mjs,cjs}'] },
  ...tseslint.configs.recommended.map((config) => ({ ...config, files: toolingFiles })),
  ...tseslint.configs.recommendedTypeChecked.map((config) => ({ ...config, files: sourceFiles })),
  {
    files: sourceFiles,
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.browser,
      parserOptions: {
        project: ['./tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.flat.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      '@typescript-eslint/consistent-type-imports': 'error',
      '@typescript-eslint/no-misused-promises': ['error', { checksVoidReturn: false }],
    },
  },
  {
    files: toolingFiles,
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.node,
    },
  },
);
