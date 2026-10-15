# VGO_ATS 前端（ui）

VGO AI 招聘赋能系统的 React 前端，位于 `VGO_ATS/ui`；Java/Spring 后端保留在同一仓库根目录。前后端统一使用根目录 Git，下面的路径和命令以 `ui/` 为当前目录。

## 前端开发 Skills

- [HR AI 前端开发与视觉规范](.agents/skills/hr-ai-frontend-development/SKILL.md)
- 视觉改动遵循当前 VGO 设计系统；明确的设计探索任务可按需使用开发者可用的设计工具。

## 产品与设计上下文

- [产品上下文](PRODUCT.md)
- [VGO 设计系统](DESIGN.md)
- [共享研发文档导航](../docs/README.md)

## 目录说明

- `src/`：正式前端源码。
- `.agents/skills/`：前端工程与 UI/UX 专用技能。
- 共享产品、架构、权限和开发基线统一维护在仓库根目录的 [docs/](../docs/README.md)。

## 本地运行

完整本地环境使用仓库根目录 `compose.yaml`，启动后访问 `http://localhost:5173`；无需在宿主机安装 Node 或 Java。启动、初始化及可选外部服务配置见 [根目录说明](../README.md)。

## 前端源码开发

在仓库根目录先执行 `cd ui`，然后运行：

```bash
npm ci
npm run dev
```

验证命令（同样在 `ui/` 执行）：

```bash
npm run quality
npm run test:e2e
```

开发服务器默认把 `/api` 代理到 `http://localhost:8080`。环境变量只配置服务地址，不存放账号、令牌或业务规则。

生产部署只打包本目录 `npm run build` 生成的 `dist/`（仓库根目录下的 `ui/dist/`），无需包含源码、开发依赖、文档、原型或测试截图。默认不生成源码映射；调试构建可在 PowerShell 中先设置 `$env:GENERATE_SOURCEMAP='true'`，构建后用 `Remove-Item Env:GENERATE_SOURCEMAP` 清除设置。

`.impeccable/`、`.impeccable-live*`、`playwright-report/` 和 `test-results/` 属于本地生成资料，不纳入版本控制，使用完可删除。

若本机尚未安装 Playwright Chromium，可先执行 `npx playwright install chromium`；也可在已安装 Chrome 的开发机上用 `$env:PLAYWRIGHT_CHANNEL='chrome'; npm run test:e2e` 执行相同用例。CI 默认使用 Playwright 管理的浏览器，不设置该变量。

品牌 SVG 为项目原创。地区格网不是行政边界；城市目录采用明确许可的 2023 快照，具体来源与限制见 [城市数据说明](src/demand/cities-2023.README.md)。
