# Repository Agent Instructions

## Repository boundary

- This repository owns both the Java/Spring backend and the React frontend. Backend Maven modules remain at the root; the frontend application, skills, and product/design context live under `ui/`. Do not add `ui/` to the Maven Reactor or create a nested Git repository.
- Before frontend code changes, load the applicable `ui/AGENTS.md` and `ui/.agents/skills/hr-ai-frontend-development/SKILL.md` once; reuse them within the session unless changed. Frontend commands run in `ui/` or use `npm --prefix ui` from the repository root; backend commands continue to run at the root.
- Frontend-only skills stay in `ui/.agents/skills/`; their project context is `ui/PRODUCT.md` and `ui/DESIGN.md`. Run their scripts with `ui/` as the working directory so context discovery and generated artifacts stay with the frontend.
- Coordinate through OpenAPI and the shared product, architecture, permission, and implementation-baseline documents under root `docs/`. Shared documents are maintained only there; `ui/docs/` contains frontend-specific documents.

## Code-quality skill precedence

- Repository architecture, domain invariants, security rules, and required verification override generic minimal-code or YAGNI guidance such as Ponytail.
- Interpret “smallest change” as the smallest correct change inside the required modular boundaries. Do not remove module APIs, external-system ports, DTO/entity separation, authorization, validation, idempotency, audit, recovery behavior, or required tests merely to reduce files or lines.
- Use the repository-scoped `hr-ai-change-verification` skill for fresh, risk-based completion evidence after backend changes.

## Task scope, context and authorization

- Choose skills by the requested outcome and affected files, not incidental keywords. Existing-system copy, default-value, CSS and local-control fixes use the project skill; add design exploration, audit or generic skills only for a relevant unresolved need. Read-only instruction reviews inspect those files as data and do not execute their workflows.
- Read only the sections needed for the current change; do not load all references with a wildcard. Reuse already-read, unchanged context. Repository links identify sources of truth, not a mandatory reading list for every task.
- A clear user request and existing session authorization cover the necessary low-risk, reversible work. Do not ask the user to choose internal skill routes, reconfirm settled requirements, or approve already-authorized edits. Ask only for material information that cannot be inferred or an action outside the authorized scope.
- Authorization does not expand scope or override platform restrictions. Preserve required confirmation for destructive or irreversible operations, real shared/production data changes, external messages, publication and credential/permission changes when not already explicitly authorized. Keep real secrets out of source and evidence; independently verify isolation before destructive tests.
- Verify risks introduced by this change. Reuse inspectable command, exit-code and raw-result evidence from this task at the same relevant source state, including delegated work; never rely on a success assertion alone. A broader successful check can cover a narrower one when it actually executes that path. Rerun after relevant changes, failures or unresolved concerns, not merely because another agent takes over.
- Documentation/instruction-only edits require relevant consistency, reference and format checks. Changed helper scripts additionally need syntax and focused behavior checks; do not run application builds or full business test suites unless their behavior or gates are affected.

## Java import discipline

- In production and test Java code, import types and annotations and use their short names. This includes method signatures, constructors, class literals, static calls, and MyBatis annotations; do not inline a full package name merely to avoid maintaining imports.
- Fully qualified names are allowed only when a short name would conflict with an imported, declared, same-package, implicitly available, or shadowing name. Preserve required qualification for real conflicts; do not rename domain types to satisfy style.
- Package/import declarations, SQL and other strings, reflection/configuration class-name strings, text blocks, and comments are not Java type references and must not be rewritten by a text replacement.
- `JavaImportPolicyContractTest` checks all `src/main/java` and `src/test/java` sources during `contract-test` / root `verify`; the gate must remain enabled. See [docs/Java导入规范与门禁.md](docs/Java导入规范与门禁.md) for checks and safe cleanup.

## Git commit discipline

- 用户明确要求：后续所有开发和修复直接在 "dev" 分支进行，禁止创建新的 Git 分支或分支工作树；不得擅自切换到 Codex 分支。已有并行任务的未提交改动必须保留，只提交本任务文件。

- 必须门禁：每项修改完成并通过相应验证后，必须提交 Git；提交标题和正文使用中文，说明修改内容、原因及验证结果。不得只修改工作区而不提交。

- Every completed issue fix must be committed before starting the next unrelated issue. Keep each commit focused on one problem and include its relevant tests or enforcement rules.
- Run verification proportional to the change before committing, and report the resulting commit hash. Never include unrelated pre-existing user changes in the commit.

## SQL deployment discipline

- `docs/sql/` contains only directly deployable SQL files. The currently converged delivery retains `hr_ai_complete_schema.sql` for an empty database; previously released upgrades are traceable in Git and must not be presented as files that remain deployable from `docs/sql/`. This describes the current release baseline, not a permanent ban on future upgrade files; only archive upgrades during an explicitly scoped baseline convergence. Keep documentation, examples, test fixtures, and logs outside this directory.
- Every column, index, constraint, or other schema change must update the final baseline and include a new standalone upgrade SQL in the same commit. Never deliver a baseline-only schema change or rewrite a released upgrade.
- Upgrades must state their prerequisites and data impact, check metadata in the selected database before adding or dropping objects, and support reruns and partial completion without losing existing data. Destructive changes need explicit dependency and recovery steps.
- Verify layout contracts and, when explicitly configured, real MySQL empty-schema, historical-data upgrade, and rerun paths. See [docs/数据库SQL部署规范.md](docs/数据库SQL部署规范.md).

## Configuration conventions

- Keep stable, ordinary development settings directly in `bootstrap/src/main/resources/application.yml`, including the HTTP port, local database URL/username/password, pool sizes, timeouts, and disabled-feature defaults. Do not wrap every property in an environment-variable placeholder by default.
- A checked-in password or key must be an explicitly disposable local-development value, never a real shared, staging, or production credential. Production secrets still come from the deployment platform's environment or Secret service and must not be committed.
- Use environment-variable indirection only for genuine deployment differences or secrets. Prefer a readable literal when a value is the same in every local checkout, and keep production overrides in the `prod` profile.
- When adding a configurable subsystem, provide safe, directly readable development defaults where possible and document which production values remain required.
- Add concise Chinese comments to configuration files for environment boundaries, non-obvious values, units, security constraints, and disabled fallbacks; avoid comments that merely repeat the property name.

## Planning file isolation

- Do not append task history to the root `task_plan.md`, `findings.md`, or `progress.md`. They are small routing files only.
- Use persistent planning for multi-stage/cross-module work that needs recovery, not merely because a task uses several tools. Read-only reviews and small fixes do not create plans by default. For work that needs persistent planning, create one isolated directory with `powershell -File scripts/planning/new-session.ps1 -Name "short-task-name"`.
- Read and write only `.planning/<plan-id>/task_plan.md`, `findings.md`, and `progress.md` for that task. In concurrent work, never change another task's directory or use a shared `.planning/.active_plan` pointer.
- Only if this task created a planning directory, before stopping run `powershell -File scripts/planning/check-size.ps1 -PlanId <plan-id>`. Promote stable decisions into `docs/`, code, tests, migrations, or ADRs.
- Only if this task created a planning directory, when it is complete run `powershell -File scripts/planning/close-session.ps1 -PlanId <plan-id>`. This moves it under `.planning/archive/` so automatic plan discovery does not keep loading it.
- Limits per active task: `task_plan.md` 200 lines/32 KB, `findings.md` 400 lines/64 KB, `progress.md` 300 lines/48 KB. Put verbose command output and raw research under the task's `evidence/` directory and summarize it in `findings.md`.

See [docs/规划与过程文件规范.md](docs/规划与过程文件规范.md) for the full policy.

## Astra 多代理开发协作

- 由 Astra 主代理负责任务拆解、复杂或跨模块问题、安全与性能判断及整体验收；Sol 处理边界清晰的实现与调试；Terra 处理局部检查、文档和简单任务。
- 模型选择仅以当次实际可用的模型与工具为准；遇到失败或复杂度上升时升级处理。不得编造模型可用性、价格，或承诺 token 节约比例。
- 相互独立的任务优先并行；简单任务无需强行拆分。总并发默认不超过工具上限，建议保持在 3–4 个任务以内。
- 分派前明确文件所有权；存在依赖时串行执行。不得并发写入同一文件、共享构建产物或 Git index。
- 上下文只保留完成当前任务所需的任务说明、约束与证据；每项风险执行一次有效检查，不得以提速或节约 token 降低既有质量门禁。
