# 后端架构与编码边界

## 模块与依赖

仓库使用 Maven 根聚合、多业务模块平铺结构。`bootstrap` 是一期唯一 Spring Boot 运行入口和可部署 JAR；异步消费者、定时任务和受控批处理作为对应业务模块内的运行职责随同一进程装配，不创建独立 `worker`、`batch` Maven 模块或启动类。公共能力位于独立 `common`，跨基础设施语义验证位于 `contract-test`。业务 Maven 模块以架构文档为准：`identity`、`talent`、`recruiting`、`intelligence`、`integration`、`reporting`；其中 `recruiting` 以 `com.ezaigc.hr.recruiting.<slice>` 承载 demand/recruitment/interview/submission，`intelligence` 以 `com.ezaigc.hr.intelligence.<slice>` 承载 matching/search。React 前端位于本仓库 `ui/`，由根目录 Git 统一管理，通过 npm 独立构建，不进入 Maven Reactor。

```text
controller -> application service -> domain service/aggregate
                         |              |
                         +-> assembler  +-> port -> infrastructure adapter

consumer module -> provider public API/domain event -> provider module
```

- `bootstrap` 只承担组合根、HTTP 入口和全应用装配，不拥有业务规则。消费者、迁移、全量重算和索引重建的规则仍由业务模块拥有，并通过独立线程池、租约、并发上限和启停配置与在线请求隔离。只有生产证据证明需要独立扩缩容或发布边界时，才通过架构变更增加新的组合根。
- `common` 只放两个以上独立模块语义完全一致、依赖稳定且无业务所有者的 ID、时钟、事件、Web/ProblemDetail、MyBatis 装配、JSON、脱敏、安全上下文和 Trace 能力。
- `common` 不依赖业务模块、不拥有业务表，不包含招聘 DTO/Entity/Mapper、状态机、评分规则或供应商适配器。`CommonUtils` 仅承载无依赖、无状态、无业务语义的标量与字符串基础方法；禁止 `BaseService`、万能响应 DTO 和共享业务 Entity。
- `common` 不得仅因方便而让所有模块被动引入完整 Web、MyBatis、消息或云 SDK；若技术能力并非所有模块共同需要，优先拆为小型公共模块或由使用方显式依赖。
- 模块依赖只声明在直接使用方；禁止循环依赖。需要同步能力时暴露窄接口，需要解耦或最终一致时发布领域事件。

## 模块内包结构

业务模块参考 springAi 使用 `api`、`application/manager`、`application/assembler`、`application/support`、`config`、`constant`、`controller`、`domain/dto|entity|request|response`、`enums`、`infrastructure`、`mapper`、`service/port|impl`。这些是职责位置而非空目录清单：没有跨模块调用就不建 `api`，没有真实接口或多实现边界就不机械创建 `Service + ServiceImpl`。

- `api` 只放跨 Maven 模块的窄能力接口及必要 DTO；调用方不得导入提供方内部包。
- `service/` 根目录的 `*Service` 是继承 MyBatis-Plus `IService` 的持久化接口；`service/impl/` 仅放继承 `ServiceImpl` 的薄持久化实现。Manager、Worker 等依赖接口，不直接依赖 ServiceImpl。
- `application/manager/*Manager` 编排用例，通过 `service` 或 `application/provider` 访问持久化或外部能力。只复用 API 做准入校验的策略放在 `application/support`；不要为满足 Manager 门禁引入无用依赖，也不要把业务编排放进持久化 ServiceImpl。具体边界见 [ModuleBoundaryArchitectureTest](../../../../contract-test/src/test/java/com/ezaigc/hr/architecture/ModuleBoundaryArchitectureTest.java)。
- `service/port` 表达本模块所需的对象存储、模型、搜索、通知等能力，实现在 `infrastructure`。
- Request/Response 不跨 HTTP 边界，Entity 不跨 HTTP 或模块边界，Mapper 不返回 Response。

## Java 导入规范

- 生产和测试代码中的类型、注解统一使用 `import` 与短名，覆盖返回值、参数、构造器、类字面量和静态调用；不得为了省略导入而在正文堆叠完整包名。
- 只有真实同名或遮蔽冲突保留必要的全限定引用；不能改变原有类型绑定来缩短名称。
- SQL/普通字符串、文本块、反射与配置中的类名、注释不按 Java 类型引用替换。清理必须经过语法树识别及编译验证。
- `contract-test` 的 `JavaImportPolicyContractTest` 同时检查全部生产和测试源码，默认随根 `verify` 执行。命令与例外见 [Java 导入规范与门禁](../../../../docs/Java导入规范与门禁.md)。

## 分层职责

- Controller：HTTP 映射、认证上下文、校验、响应状态与 OpenAPI 描述；不访问 Mapper，不写业务规则。
- Application Service：用例编排、权限决策、事务、幂等、聚合装载与端口调用；方法名表达用户意图。
- Domain：表达业务规则的聚合、值对象、策略、状态机和领域服务不得依赖 Spring MVC、MyBatis、供应商 SDK。此约束按职责判断；本仓库 `domain/entity` 是持久化映射位置，可使用 MyBatis 映射注解，不得仅因包名含 `domain` 搬迁 Entity 或误作领域聚合。
- Infrastructure：Mapper、MyBatis-Plus、消息、搜索、存储、模型、邮件和外部系统适配器。
- Assembler：只做边界转换，不查库、不鉴权、不调用远端。

## API、错误与并发

- REST 路径统一位于 `/api/v1`；资源、动作和状态码保持一致，批量与长任务返回稳定任务 ID。
- 使用 RFC 9457 `ProblemDetail` 风格，扩展稳定 `code`、`traceId`、字段错误和可恢复提示；不照搬 springAi 的 `ApiResponse` 包装。
- 公开 Request/Response 写清单位、格式、敏感性、枚举和状态语义。Java `long` ID 在 OpenAPI/JSON 边界按字符串处理，避免 JavaScript 精度损失。
- 数据库约束、唯一键、乐观锁和带前置状态的更新是最终并发防线。分布式锁只能保护短窗口，不能替代幂等和单调状态机。
- 外部回调、消息、重复提交、定时任务和重试都要有幂等键。记录版本冲突并返回可恢复错误，不静默覆盖。

## MyBatis 与配置

- Mapper 显式列字段，禁止 `SELECT *`；复杂搜索写可解释 SQL，避免把 MyBatis-Plus 通用 CRUD 当领域模型。
- 分页拦截器位于链末尾，在线每页默认不超过 100；`Page/IPage` 不暴露到 HTTP。
- 启用并测试乐观锁、防全表更新/删除、数据范围等拦截器；数据范围必须覆盖子查询、联表、批处理和管理接口，不能假设 springAi 的实现可直接复用。
- 迁移采用版本化工具和向前兼容策略；表、索引、唯一约束、回滚/修复路径与应用改动同批提交。
- 环境差异通过配置和适配器表达。配置 Bean 使用校验、单位明确的 `Duration`/`DataSize` 等类型；生产密钥只由环境或密钥服务注入。
