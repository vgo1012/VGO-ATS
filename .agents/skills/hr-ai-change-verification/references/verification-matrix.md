# 后端变更验证矩阵

从改动类型选择适用行。命令是当前仓库的基线；按需核对构建配置及测试，已确认且未变化时无需重读。表中的测试与门禁是覆盖要求，不是每项单独运行一次的命令清单；模块或根验证实际包含所需项时可合并执行。

## 基础命令

| 目标 | PowerShell 命令 | 适用范围 |
|---|---|---|
| 单个测试类 | `.\mvnw.cmd -pl <module> -am '-Dtest=<TestClass>' '-Dsurefire.failIfNoSpecifiedTests=false' test` | 聚焦回归或新行为 |
| 模块及依赖 | `.\mvnw.cmd -pl <module> -am verify` | 单模块生产代码、测试或配置 |
| 架构与契约 | `.\mvnw.cmd -pl contract-test -am test` | 模块、包、API、依赖、Mapper/SQL 布局变化 |
| 全量后端 | `.\mvnw.cmd clean verify` | 合并前或声称整个后端通过 |
| 定向 diff 检查 | `git diff --check -- <paths>` | 本轮修改文件的空白错误；不能替代编译和测试 |

指定测试时保留 `-Dsurefire.failIfNoSpecifiedTests=false`，因为 `-am` 会包含没有该测试类的上游模块。不要添加 `-DskipTests`。

## 风险路由

| 改动 | 最低证据 | 追加检查 |
|---|---|---|
| 纯值对象、转换、确定性领域规则 | 直接单元测试 + 模块 `verify` | 影响公共 API 或跨模块类型时加 `contract-test` |
| Controller、请求/响应、鉴权、字段裁剪 | 正反向 Controller/服务行为测试 + 模块 `verify` | 权限矩阵、越权、缺失/非法输入；跨模块契约变化加 `contract-test` |
| MyBatis Mapper、SQL、事务、唯一约束、乐观锁 | SQL/事务集成测试 + 模块 `verify` | 运行外部 MySQL 门禁；不能用 H2 声明 MySQL 兼容 |
| Flyway 或 `docs/sql` 基线 | 迁移解析/布局契约 + `contract-test` + 全量 `verify` | 外部 MySQL 上空库、重复执行；涉及升级时还需代表性历史数据路径 |
| Maven 模块、依赖或包结构 | Maven Enforcer + 模块 `verify` + `contract-test` | 检查 Reactor 收敛、循环依赖、跨模块内部包和组合根反向依赖 |
| Outbox、消息、Worker、长任务 | 重复、乱序、超时、重试耗尽、失败终态和恢复测试 | 有显式测试环境时验证真实 Broker/协议；检查事务不包住慢网络调用 |
| 搜索投影、游标、权限投影 | 稳定排序、旧游标失效、权限收窄和 MySQL 二次拒绝测试 | 有显式测试环境时验证真实搜索服务及重建路径 |
| 对象存储、文件解析、ClamAV | MIME 伪装、大小/页数上限、扫描 fail-closed、超时和隔离测试 | 有显式测试环境时验证真实 MinIO/ClamAV 协议；不得上传真实简历 |
| AI Schema、Prompt、模型适配 | Schema/证据边界、非法输出、Prompt 注入、超时/限流、人工回退测试 | 只用合成/匿名样本；有明确测试配置时才调用真实模型 |
| 日志、指标、健康检查、生产配置 | 脱敏、默认危险配置拒绝启动、liveness/readiness 行为测试 | 没有压测、恢复演练或实际部署证据时不得声明生产就绪 |

## 外部 MySQL 门禁

仅当以下变量明确存在且目标确认是测试数据库时运行，不输出变量值：

- `HR_EXTERNAL_MYSQL_IT=true`
- `HR_DB_URL`
- `HR_DB_USER`
- `HR_DB_PASSWORD`

变量齐全不等于测试已经隔离。执行前核对测试实际使用的 JDBC/Spring DataSource：优先直接连接本次创建的随机临时库，不能把开发业务库作为破坏性夹具。MySQL Connector/J 的 Statement 可能保留创建时的数据库；禁止在切库后复用旧 Statement 执行未限定库名的 DDL。隔离回归应在可丢弃的来源库放置非空哨兵，验证其结构/数据不变，并只清理本次成功创建的临时库。不得仅以 `setCatalog` 的存在认定安全隔离。

`PlatformExternalMySqlIT` 必须使用本次新建、库名为 `hr_platform_it_<32位小写十六进制>` 的空库；另设置 `HR_EXTERNAL_OBJECT_ROOT` 为本次创建的空目录，其父目录名必须与库名相同。测试使用独立 `external-it` Profile，在 Spring 创建数据源/Flyway/管理员之前检查隔离，明确绑定上述数据库变量，其他外部系统采用本地适配器。这只证明真实 MySQL 业务链，不证明搜索、对象存储或模型供应商兼容。运行方须在测试进程结束后清理自身临时库和经过路径核对的对象目录。

命令：

```powershell
.\mvnw.cmd -pl bootstrap -am '-Dtest=PlatformExternalMySqlIT' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

变量缺失时记录“未运行：外部 MySQL 测试环境未配置”。不自动启动中间件，不得用共享或生产数据库补位；已授权的本机可丢弃环境按 README 准备后，仍须验证测试库隔离。

## 失败判定

- 构建、测试或契约命令非零退出即失败；不要只看输出末尾的局部 `SUCCESS`。
- 从第一个应用拥有的编译错误、测试失败或异常根因开始分析，区分环境失败、既有失败和本轮回归。
- 修复后必须重新执行原失败路径；可运行原命令，也可使用实际包含该路径的模块或根验证，并从测试报告确认原失败测试已执行且通过。扩大后的命令若跳过该路径，不能作为修复证据。
- 警告若指向即将失效的运行方式、安全降级或测试未被发现，必须报告；普通第三方噪声可注明但不夸大。
