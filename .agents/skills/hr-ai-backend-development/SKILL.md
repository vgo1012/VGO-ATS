---
name: hr-ai-backend-development
description: Apply this repository's architecture, domain, security, and data rules when implementing or reviewing Java/Spring backend code, tests, Maven configuration, API contracts, migrations, or runtime quality gates. Exclude frontend-only work, prose-only edits, and read-only audits of AGENTS.md or skill instructions.
---

# HR AI 后端开发规范

在本仓库实施后端纵向切片。产品需求和技术架构已形成确认基线；实施参数尚未明确时，不把建议伪装为定案，也不为未来能力预建空模块。

## 与全局 Skill 的关系

本 Skill 独立定义本仓库关键约束。仅遇到本项目尚未定义的 Java/Spring 工程问题时，按需参考已安装的 `$java-spring-modular-development` 对应章节，不重复执行其规划、文档读取和验证流程。冲突时以已确认的本项目文档和本 Skill 为准。

## 开始工作

1. 读取适用的 `AGENTS.md`（若存在）；业务语义或架构决策需要依据时，读取 `docs/HR_AI招聘赋能系统_产品需求文档.md` 和 `docs/HR_AI招聘赋能系统_技术方案与架构设计.md` 中相关章节。同一会话已读且未变化的内容不重复读取；局部修复只读相关实现与规范。
2. 搜索现有模块、契约、SQL、配置、事件和测试，确认业务所有者、调用方、事实源及异步边界。新增 Spring 组件或持久化类型前，核对 [ModuleBoundaryArchitectureTest](../../../contract-test/src/test/java/com/ezaigc/hr/architecture/ModuleBoundaryArchitectureTest.java) 的注入与分层规则。
3. 把改动收敛为一个可验证纵向切片：入口、权限、用例、领域规则、持久化、事件/外部适配和测试缺一不可时一起完成。
4. 保留无关用户改动；不从其他项目复制业务代码、表模型或密钥；仅使用经本项目验证的通用工程模式，并尊重代码来源的授权边界。

## 按需读取

| 改动涉及 | 必读参考 |
|---|---|
| Maven 模块、Java 包、分层、REST、事务、MyBatis | [references/architecture-and-code.md](references/architecture-and-code.md) |
| Candidate、Resume、Application、评分、推荐、面试、权限 | [references/domain-invariants.md](references/domain-invariants.md) |
| 简历解析、模型、任务、RabbitMQ、Outbox、搜索、对象存储 | [references/ai-async-and-data.md](references/ai-async-and-data.md) |
| 新增或修改测试场景、性能、安全、可观测性 | [references/testing-and-quality.md](references/testing-and-quality.md) 中受影响项 |

只改测试时仍读取测试所验证边界对应的参考。审查或解释请求不授权修改代码。

## 始终遵守

- 技术基线为 Java 21、Spring Boot 4.1.x、Spring AI 2.0.x、MySQL 8、MyBatis/MyBatis-Plus；补丁版本在开工时锁定，不使用漂移版本。
- 仓库采用参考 springAi 的 Maven 根聚合、多业务模块平铺结构，并保留独立 `common` 模块；业务模块内部按 `api/application/controller/domain/mapper/service/infrastructure` 组织，但只创建真实需要的包。
- 按业务模块组织代码。跨模块只依赖提供方窄 API 或领域事件，不导入对方 Mapper、Entity 或内部 Service，不跨模块直接写表。
- 本仓库 Spring 托管组件统一使用 `jakarta.annotation.Resource` 字段注入，不使用依赖注入构造器、`@Autowired` 字段或 `@Resource` 方法；此项目约定覆盖通用技能的构造器注入建议。普通值对象和非 Spring 托管类不受此注入约定限制。
- Controller 只处理协议；应用层编排权限与事务；领域服务表达规则；基础设施适配数据库和外部系统。
- HTTP 输入为 `*Request`，输出为 `*Response`，内部传输为 `*DTO`/`*Command`/`*Query`，持久化映射为 `*Entity`；边界语义相同才复用对象。
- 对写操作执行服务端权限检查、Bean Validation、幂等与并发保护。前端隐藏按钮不构成权限控制。
- 数据库事务保持短小；模型、OCR、对象存储、邮件和消息 Broker 网络调用不得长期占用事务。
- 业务事实、AI 证据和人工评价分层保存；AI 不得自动淘汰候选人，风险和不确定性必须带证据暴露给 HR。
- 配置使用带校验的 `@ConfigurationProperties`。真实共享、预发或生产密钥和个人敏感数据不写入源码、明文配置、提示词日志或异常响应；允许按根 `AGENTS.md` 配置明确可丢弃的本地开发凭据，生产密钥由部署环境或 Secret 服务提供。
- 新增规则同步测试；新增事件、状态或版本必须证明重复执行、迟到事件、失败恢复和审计语义。

## 验证与交付

验证命令、覆盖替代、外部测试隔离和交付证据统一遵循 [hr-ai-change-verification](../hr-ai-change-verification/SKILL.md)，不另行执行重复流程。重复缺陷优先固化成单元测试、架构契约测试或静态门禁；不要把一次性事故细节堆进本 Skill。
