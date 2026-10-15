# React 前端工程边界

## 建议结构

```text
src/app/             # Router、Providers、错误边界、主题与启动
src/pages/           # 路由级组合，不直接实现数据基础设施
src/features/        # upload、matching、interview、submission 等用例
src/entities/        # Candidate、ResumeVersion、Application 等展示模型
src/widgets/         # 工作台区块、搜索结果、详情布局、任务抽屉
src/shared/api/      # OpenAPI 生成类型、HTTP、ProblemDetail、任务轮询
src/shared/auth/     # 会话、能力、字段可见性、路由守卫
src/shared/ui/       # Token 封装和无业务共享组件
src/shared/lib/      # 无副作用通用函数
```

- 依赖由 `app/pages -> widgets/features -> entities/shared` 单向流动；业务特性不得反向依赖页面。
- 只被一个特性使用的组件、hook、schema 和 query key 与特性共置。进入 `shared` 前必须证明两个以上独立业务语义一致。
- 页面负责组合和路由上下文，不出现巨大 `services.ts`、全局类型垃圾桶或跨域万能表格。

## 数据与契约

- 浏览器只访问同源 `/api/v1`；集中 HTTP 客户端处理 credentials、CSRF、traceId、ProblemDetail、AbortSignal 和 401 会话失效。
- Cookie 使用 HttpOnly/Secure/SameSite；前端只持有 CSRF 令牌等非身份秘密，不把长期 Token 放入 localStorage。
- OpenAPI 生成的 Wire 类型只在 `shared/api` 使用。ID、游标、版本号保持字符串；日期在边界解析；禁止页面随意 `as` 强转。
- TanStack Query 的 key 由实体/特性工厂管理；mutation 只使受影响 key 失效或精确更新，不能刷新整个应用。
- 乐观更新仅用于可回滚、结果明确的轻量操作；阶段迁移、风险推进等副作用动作要防重复提交、保存前态并在失败时恢复。

## 路由、表单与长任务

- 路由模块懒加载，并分别处理认证失败、无权限、404、可恢复业务错误和不可恢复错误。
- URL 保存可分享的搜索词、筛选、排序、页码和视图模式；临时对话框状态不污染 URL。
- RHF + Zod 是复杂表单唯一状态源；服务端字段错误映射到对应控件，失败后保留用户输入。未保存更改需要离开提示。
- 简历解析、评分、批量导入、索引重建使用任务 ID。V1 以 3～5 秒轮询服务端任务快照为唯一实时进度方案：任务页或任务抽屉可见时轮询，隐藏标签页降频，离开相关界面后停止；终态后停止轮询。
- V1 不实现 SSE 或 WebSocket。页面刷新后按任务 ID 从服务端快照恢复，不依赖内存进度；未来若增加 SSE，仍只能作为任务事实的传输优化，不能改变任务契约或状态真相。

## 反馈与文案

- 保存、发送、删除、复制等短暂结果使用统一 Toast；业务错误通过公共 FeedbackAlert / useAppFeedback 在页面正中显示红色浮层，5 秒自动消失并可手动关闭。字段校验、加载/空状态、结构化长任务进度和错误恢复按钮就地保留；同一结果不重复显示。
- 按用户认识的业务动作命名：`推荐给甲方`、`带风险推进`、`重新解析`，不用 `提交`、`处理数据` 等含糊词。
- 错误说明发生了什么、哪些内容已保存和下一步；禁止用成功 Toast 掩盖部分失败。
