---
name: hr-ai-frontend-development
description: Implement or review React/TypeScript frontend behavior and visuals in VGO_ATS/ui. Use for affected UI code and tests; load only relevant references. Excludes backend-only work and instruction/documentation-only reviews or edits that do not change application behavior.
---

# HR AI 前端开发与视觉规范

## 目录作用域

- 前端开发根目录是仓库中的 `ui/`；本技能正文与参考中提到的 `src/`、`PRODUCT.md`、`DESIGN.md` 和前端命令均相对此目录。先读 [目录规则](../../../AGENTS.md)。
- 产品与设计上下文分别是 [PRODUCT.md](../../../PRODUCT.md) 和 [DESIGN.md](../../../DESIGN.md)；共享需求、架构和权限以 [根目录文档](../../../../docs/README.md) 为准。
- npm 命令、设计工具及前端 Skill 脚本以 `ui/` 为工作目录；从仓库根目录运行 npm 时使用 `npm --prefix ui`。后端 Maven 与 Git 仍由同一仓库根目录管理。

构建面向招聘人员的高密度工作台，采用蓝色按钮、浅蓝画布与柔和虚化背景。实现服从本项目 React/Ant Design 架构，并保留 AI 证据与人工决策边界。

## 工作流程

1. 复用本会话已读且未变化的适用 `AGENTS.md`。业务行为变更仅查 [PRODUCT.md](../../../PRODUCT.md) 的相关章节及其根文档链接；视觉变更仅查 [DESIGN.md](../../../DESIGN.md) 对应规则和现有实现。明确的文案、默认值或局部样式修复不加载无关业务历史。
2. 只检查本次涉及的路由、Token、共享组件、API 边界、查询键或测试；不逐项检查未受影响的架构。
3. 按改动风险选择受影响的状态和恢复路径；新页面、权限或长任务变化才展开对应完整状态矩阵。
4. 优先复用 Ant Design 和现有招聘业务组件；新增视觉原语前证明现有 Token/组件不足。
5. 按验证参考检查实际触及的流程、焦点或视口；视觉正确性由浏览器证据确认，不以静态代码推断。

## 按需读取

| 改动涉及 | 必读参考 |
|---|---|
| React 目录、路由、API、Query、表单、认证、任务轮询 | [references/frontend-engineering.md](references/frontend-engineering.md) |
| 颜色、排版、密度、Ant Design Token、页面骨架 | [references/visual-system.md](references/visual-system.md) |
| 工作台、搜索、候选人详情、AI 评分、管道、长任务 | [references/ats-interaction-patterns.md](references/ats-interaction-patterns.md) |
| 响应式、可访问性、组件/E2E 测试、前端代码验收 | [references/testing-and-accessibility.md](references/testing-and-accessibility.md) |
| 明确要求无后端演示原型或纯前端 Mock 页面 | [references/demo-prototype-patterns.md](references/demo-prototype-patterns.md) |

## 始终遵守

- 基线为 React 19.2.x + TypeScript + Vite 8.x SPA、Ant Design 6.x、React Router Data Mode、TanStack Query v5、React Hook Form + Zod。
- 不引入 Next.js、Node BFF、React Server Components；生产物是由 Nginx 提供的静态文件，同源访问 Java `/api/v1`。
- 页面不拼 API URL、不直接维护服务端缓存、不散落权限字符串。OpenAPI 生成 Wire 类型，基础设施边界规范化为前端领域类型。
- TanStack Query 管服务端状态；本地 UI 状态就地管理。没有跨模块复杂客户端状态证据时不引入 Redux/Zustand。
- Ant Design Form 与 React Hook Form 不得同时拥有同一字段；复杂业务表单以 RHF 为唯一状态源。
- 前端权限守卫只改善体验；详情、字段、下载、导出和动作权限由后端最终裁剪/拒绝。无权时呈现脱敏协作入口，不伪装数据不存在。
- AI 结果必须同时呈现证据、覆盖率/置信度、版本和人工判断入口；不得用单个总分或红绿颜色替代四通道信息。
- 不使用全局隐藏滚动条、10px 正文、硬编码颜色、`limit: 9999`、页面内权限决定和巨型客户端组件。

## 完成门槛

依据 [testing-and-accessibility.md](references/testing-and-accessibility.md) 选择必要检查并复用当前证据。保留本次涉及的权限、数据范围、失败恢复和键盘可用性；不为了局部改动重验无关页面、状态或全部视口。不适用项无需制造测试或未完成清单。
