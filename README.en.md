<p align="center">
  <strong>English</strong> · <a href="README.md">简体中文</a>
</p>

<p align="center">
  <img src="ui/public/assets/vgo-mark.svg" width="72" alt="VGO mark">
</p>

<h1 align="center">VGO ATS</h1>

<p align="center"><strong>Where talent meets opportunity.</strong></p>
<p align="center">An AI-assisted recruiting workspace connecting resumes, jobs, and hiring workflows.</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-2563EB?style=flat-square" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring_Boot-4.1-2563EB?style=flat-square" alt="Spring Boot 4.1">
  <img src="https://img.shields.io/badge/React-19-2563EB?style=flat-square" alt="React 19">
  <img src="https://img.shields.io/badge/Docker-Compose-2563EB?style=flat-square" alt="Docker Compose">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Personal_Non--Commercial-334155?style=flat-square" alt="Personal non-commercial use only"></a>
</p>

<p align="center">
  <a href="#about">🎯 About</a> ·
  <a href="#features">✨ Features</a> ·
  <a href="#running-locally">🚀 Quick Start</a> ·
  <a href="#development">🛠️ Development</a> ·
  <a href="#faq">❓ FAQ</a> ·
  <a href="#license">📄 License</a>
</p>

<p align="center">
  <img src="ui/public/assets/vgo-talent-connection.png" width="520" alt="VGO talent connection illustration: connecting candidates, resumes, and recruiting workflows around a job">
</p>

> **Personal non-commercial use only.** Individuals may use this project for personal learning, research, testing, and their own non-commercial use. Commercial use and internal business use by companies or other organizations are prohibited, even when no fee is charged. See [LICENSE](LICENSE) for the full terms.

<a id="about"></a>

## 🎯 About

Maintained by the **VGO Team**, VGO ATS is an Applicant Tracking System built around the recruiting process. It brings job management, resume intake, structured parsing, AI assessment, recruiting progress, interview scheduling, and hiring delivery into one workspace, giving screening decisions supporting evidence and keeping a record of each step.

The project includes a React frontend and a Java/Spring backend and runs locally with Docker Compose. This public release is intended for individuals studying recruiting domain modeling, researching AI applications, and exploring a complete recruiting workflow.

- 📌 **Collaborate around facts**: Original job descriptions, resume versions, interview feedback, and actual onboarding are stored separately, with traceable process records.
- 🔎 **Review the basis for decisions**: AI provides assessments and reasons by dimension; users review the materials and decide on screening, progression, and hiring.
- 🔒 **Keep data in a controlled environment**: The local configuration uses a separate database, search service, and file volumes. External models and integrations are enabled as needed.

<a id="features"></a>

## ✨ Features

| Capability | What it does |
| --- | --- |
| 💼 **Jobs and public JDs** | Manage original job descriptions, headcount, and recruiting periods. Apply a reusable public JD as an independent job, preserving its title and original text while maintaining recruiting settings separately. |
| 📥 **Resume intake and parsing review** | Upload PDF, DOC, and DOCX files in batches and associate them with jobs and source channels. Review parsing, scoring, duplicate, identity conflict, and retry states. AI processing requires enabling a model first. |
| 🧠 **Five-dimensional AI assessment** | Provide professional assessments across core skills, job experience, relevant years of experience, responsibility fit, and industry and project experience. Adjust reference rankings with personal scoring profiles. |
| 👥 **Candidates and shared talent pool** | View candidates according to role and object-level permissions, with keyword and condition filters. Candidates from the shared pool move into a personal workspace after a job is selected and the current scoring round is completed. |
| 🔄 **Recruiting pipeline** | Track communication, successive interview rounds, client submissions, hiring, and offers, recording the actual operator, time, and outcome. |
| 🗓️ **Interview calendar** | Bring interviews and communication appointments together, with details, rescheduling, and cancellation linked to the recruiting workflow. |
| 🤝 **Hiring delivery** | Distinguish onboarding in progress from completed onboarding. Confirm actual onboarding, correct dates, or return candidates to the shared talent pool while preserving history. |
| 📊 **Recruiting insights and monthly reports** | View funnels, trends, locations, channels, headcount, and personnel statistics. Upload two Excel files to generate an editable Word monthly report using fixed rules. |

Accounts, departments, roles, source channels, personal screening profiles, and background tasks also have management interfaces. The server controls access to pages and operations.

<a id="a-complete-workflow"></a>

### 🧭 A Complete Workflow

1. 💼 **Prepare jobs**: Create a JD or apply a public JD copy, then maintain headcount, recruiting periods, and recruiting settings.
2. 📥 **Receive resumes**: Select a job and source, upload files in batches, and resolve failures, duplicates, and identity issues in parsing review.
3. 🧠 **Assess and screen**: After enabling AI, review structured information, five-dimensional assessments, and personal reference rankings. The user decides whether to proceed.
4. 🔄 **Advance recruiting**: Record communication, interviews, client feedback, hiring, and offers in the recruiting pipeline, and arrange appointments through the interview calendar.
5. 🤝 **Complete delivery**: Confirm actual onboarding, preserve the process history, and review the corresponding recruiting statistics.

<a id="how-ai-participates"></a>

### 🧠 How AI Participates

AI extracts information and provides assessments. Users confirm screening, recommendations, and hiring decisions. The project preserves original assessments, fact versions, and historical results so that newer information does not replace the evidence underlying past decisions.

Existing professional assessments can be reused for the same frozen JD facts, resume facts, prompt version, and result schema version. When personal weights change, Java recalculates reference scores from the five saved raw dimension scores. The public default assigns **20%** to each dimension as a general example only.

<a id="running-locally"></a>

## 🚀 Running Locally

<a id="1-prepare-your-environment"></a>

### 🧰 1. Prepare Your Environment

Install and start **Docker Desktop**, or use Docker Engine with **Docker Compose v2**. To try the project, you do not need to install Java, Node.js, MySQL, or Elasticsearch on the host.

The first build downloads public images and Maven and npm dependencies. Ensure network access is available and allocate enough memory and disk space to Docker.

<a id="2-start-the-project"></a>

### ▶️ 2. Start the Project

After downloading the source, run the following in the project root containing `compose.yaml`:

```bash
docker compose up -d --build --wait
```

Once startup completes, open **[http://localhost:5173](http://localhost:5173)**.

[compose.yaml](compose.yaml) builds and starts the frontend, backend, MySQL, and Elasticsearch. Only `127.0.0.1:5173` is exposed on the host. The browser accesses pages and the API through the same entry point. Database, search, and backend ports are used only within the container network.

<a id="3-first-login"></a>

### 🔑 3. First Login

| Item | Local example |
| --- | --- |
| 👤 Login account | `admin`, or the work email below |
| 📧 Work email | `admin@example.org` |
| 🔑 Initial password | `local-development-admin-password` |

These are public, disposable local development values. **You must change the password on first login.** Once an administrator account has been initialized, restarting does not overwrite its changed password. Other accounts receive random one-time passwords when an administrator creates or resets them.

<a id="4-check-status-and-stop"></a>

### ⏹️ 4. Check Status and Stop

```bash
# Check containers and health status
docker compose ps

# View recent logs
docker compose logs --tail=100

# Stop the application and retain named volumes
docker compose down
```

The database, search indexes, files, and backend logs persist in the named volumes `mysql_data`, `elasticsearch_data`, `object_data`, and `backend_logs`, respectively. Existing data and changed account passwords remain after containers are stopped and recreated.

<a id="what-is-enabled-by-default"></a>

### 🔎 What Is Enabled by Default?

The local setup uses the `dev,docker` profiles, a separate `vgo_hr` database, and **LOCAL file storage**, with no MinIO or OSS configuration required. AI, mailbox collection, WeCom, and the message broker are disabled locally by default. Disabled capabilities are clearly identified; parsing and scoring do not return fabricated successful results.

Successful startup therefore means the local services are ready. To experience JD/resume parsing and professional scoring in full, configure a model first. Synthetic jobs and resumes are recommended for your first trial.

<details>
<summary><strong>🗄️ Database initialization and upgrades</strong></summary>

By default, Flyway runs the frozen `V0001` and 37 forward migrations for a new, separate database in this public release. Existing databases from other sources require a separate review of their schema and migration history; this release's upgrades cannot be applied to them directly.

The [final-state schema script](docs/sql/hr_ai_complete_schema.sql) is an alternative manual initialization path for an empty database. Do not initialize a database with this script and then run the default Flyway initialization on it. The first public release includes only 3 generic synthetic job title suggestions.

If Flyway reports an applied migration that cannot be resolved, first check that the artifacts, runtime classpath, and migration resources are complete. Do not bypass the issue by disabling validation, ignoring missing migrations, using `repair`, or forcing the full baseline to run.

</details>

<a id="configuration-and-optional-capabilities"></a>

## ⚙️ Configuration and Optional Capabilities

| Capability | Local default | Required before enabling |
| --- | --- | --- |
| 🧠 AI parsing and professional scoring | Disabled | Model provider, model and access configuration, the `hr.ai.parsing.enabled` switch, and a separate Base64-encoded 32-byte archive key. |
| 🖼️ Multimodal assisted parsing | Disabled | A model supporting visual input, the corresponding switch, and document processing budgets. |
| 📬 Mailbox intake | Disabled | Mailbox connection and authorization information, identification and assignment rules. The first activation establishes a cursor baseline; subsequent processing handles new mail. |
| 🏢 WeCom | Directory reading, organization synchronization, and new meeting creation disabled | An enterprise application, organization mappings, credentials, and the corresponding capability switches. |
| 📨 RabbitMQ | Local publishing and consumption disabled | Broker, credentials, TLS, and publish/consume switches. The production profile enables them according to its own configuration. |
| 📁 Attachment storage | Local files | No additional configuration is needed locally. Other storage adapters are managed through deployment configuration; the workspace does not provide a cloud storage management page. |

Configuration entry points:

- [Shared runtime configuration](bootstrap/src/main/resources/application.yml): Service ports, tasks, document budgets, and general settings.
- [Local development configuration](bootstrap/src/main/resources/application-dev.yml): Local database, search, and file storage.
- [Container overrides](bootstrap/src/main/resources/application-docker.yml): Service addresses within Compose containers.
- [Production configuration](bootstrap/src/main/resources/application-prod.yml) and [secrets configuration example](deploy/application-prod-secrets.example.yml): Override entry points for separate deployments. The deployer must replace example placeholders.

Keep real keys, resumes, and business data in a controlled environment, outside source code, Issues, screenshots, and release packages. Before enabling an external model, confirm authorization and the scope of data processing. The development scanner is suitable only for trying synthetic files; a public upload endpoint requires a healthy malware scanning service.

<a id="technology-and-architecture"></a>

## 🧱 Technology and Architecture

| Layer | Technology and responsibility |
| --- | --- |
| 💻 Frontend | React 19, TypeScript 5, Vite 8, Ant Design 6, TanStack Query |
| ⚙️ Backend | Java 21, Spring Boot 4.1, Spring AI 2, MyBatis-Plus |
| 🗄️ Data and search | MySQL 8, Elasticsearch 8, Flyway |
| 🐳 Runtime | Modular monolith, Docker Compose, Nginx same-origin proxy |
| ✅ Quality checks | JUnit, architecture and contract tests, Vitest, Playwright |

The backend is a **modular monolith**, with `bootstrap` as its only startup entry point. Business modules form compile-time boundaries and run within the same application. Parsing, scoring, search, and task processing each retain their own state, resource limits, and recovery logic. The frontend lives in `ui/` and communicates with the backend through same-origin `/api/v1`.

```text
VGO_ATS/
├── bootstrap/       # Startup, configuration, runtime resources, and database migrations
├── identity/        # Accounts, organizations, roles, and object-level authorization
├── talent/          # Resume intake, candidates, facts, and versions
├── recruiting/      # JDs, recruiting workflows, interviews, and client submissions
├── intelligence/    # AI assessment, personal rankings, and search
├── integration/     # Model, document, storage, and external system adapters
├── reporting/       # Metrics, monthly reports, asynchronous tasks, and recovery
├── common/          # Shared capabilities across modules
├── contract-test/   # Architecture, interface, SQL, and other contract checks
├── ui/              # React frontend, product context, and visual guidelines
├── docs/            # SQL scripts and architecture diagrams
├── deploy/          # Deployment scripts, proxy, and configuration templates
└── compose.yaml     # Complete local environment
```

Java namespaces and Maven artifact identifiers retain the existing project names for call and migration compatibility. The public product name and visual identity are VGO ATS.

<a id="development"></a>

## 🛠️ Development

<a id="development-environment"></a>

### 💻 Development Environment

- **Backend**: JDK 21. The repository includes Maven Wrapper; a separate Maven installation is unnecessary.
- **Frontend**: Node.js 22.12.0 or later. Install dependencies using the repository lockfile.
- **Local debugging**: Prepare separate, reachable MySQL and Elasticsearch services whose addresses match the development configuration. The database and search services in Compose do not expose host ports.

For frontend development, run these commands in the repository root:

```bash
npm --prefix ui ci
npm --prefix ui run dev
```

Vite uses port `5173` by default and proxies `/api` to `http://localhost:8080`. Ensure the local backend is available before starting, and avoid using the same port as the Compose frontend entry point. See [ui/README.md](ui/README.md) for frontend commands and directory guidance.

For backend development, use the IntelliJ IDEA [shared run configuration](.run/HrPlatformApplication.run.xml) named `HrPlatformApplication（含数据库迁移资源）`. It runs Make followed by `-pl bootstrap -am process-resources`, generating and copying resources such as `bootstrap/target/generated-resources/db/migration`. An automatic configuration that runs only Make does not replace this step.

Use the following JVM arguments when debugging Java on the host so that local database and search connections bypass that Java process's proxy:

```text
-DsocksNonProxyHosts=localhost|127.*|[::1]
-Dhttp.nonProxyHosts=localhost|127.*|[::1]
```

For the initial local administrator setup, refer to the public `HR_BOOTSTRAP_ADMIN_*` settings in `compose.yaml`. These examples are only for isolated local environments.

<a id="verification-commands"></a>

### ✅ Verification Commands

```bash
# Backend tests and quality checks (project root)
JAVA_TOOL_OPTIONS=-Djava.awt.headless=true sh mvnw -B -ntp clean verify

# Frontend: lint, type checking, unit tests, and production build
npm --prefix ui run quality

# Browser regression tests
npm --prefix ui run test:e2e
```

Before the first browser regression run, execute `npx playwright install chromium` in `ui/` to install the browser. Alternatively, set `PLAYWRIGHT_CHANNEL=chrome` to select an installed Chrome. Integration tests with real MySQL, search, messaging, and models require an explicitly configured, isolated test environment. Mock test results cannot replace acceptance checks against real services.

<a id="faq"></a>

## ❓ FAQ

<details>
<summary><strong>🧠 Why are parsing and scoring unavailable after startup?</strong></summary>

External model calls are disabled locally by default. Check the model, access configuration, capability switches, and archive key, then inspect background task status. Providing an API key alone does not enable all model capabilities.

</details>

<details>
<summary><strong>🔑 Why does the default password fail, or why is it not restored after a restart?</strong></summary>

An existing database preserves changed passwords; initialization does not overwrite an existing administrator. Default values apply only to the first login in a fresh local environment. Other accounts use one-time passwords generated when an administrator creates or resets them.

</details>

<details>
<summary><strong>🌐 Why can the host not access 8080, 3306, or 9200?</strong></summary>

The root Compose setup maps only the frontend entry point `127.0.0.1:5173` to the host. Backend, database, and search communicate over the container network. Use the single frontend address in your local browser. Debugging source code on the host requires separately provisioned, reachable infrastructure services.

</details>

<details>
<summary><strong>💾 Will stopping containers delete resumes and accounts?</strong></summary>

The `docker compose down` command used here retains named volumes. Data, files, and changed account passwords remain in those volumes. Clearing data is a separate operation; confirm the target and backups first.

</details>

<details>
<summary><strong>⚖️ Can I deploy this for my company for free, or offer it as a paid service?</strong></summary>

This project permits personal non-commercial use only. Free deployment, internal organizational use, or acting in an individual's name does not exempt use from the commercial and organizational business restrictions. Uses outside the permitted scope require prior, explicit written authorization from the VGO Team. See [LICENSE](LICENSE) for the full terms.

</details>

<a id="feedback-and-contributions"></a>

## 🤝 Feedback and Contributions

Use this repository's Issues to report reproducible problems or suggest improvements. Include steps, expected and actual results, how you run the project, and relevant versions. Redact personal information and credentials from logs and screenshots.

When submitting changes, explain the reason, scope of impact, and verification results, and follow [AGENTS.md](AGENTS.md) and the current license. Only submit code, documentation, samples, and assets that you have the right to contribute. Use synthetic test data.

Engineering entry points: [Frontend guide](ui/README.md) · [Product context](ui/PRODUCT.md) · [Visual guidelines](ui/DESIGN.md) · [Backend development guidelines](.agents/skills/hr-ai-backend-development/SKILL.md) · [Backend verification guidelines](.agents/skills/hr-ai-change-verification/SKILL.md).

<a id="license"></a>

## 📄 License

Copyright (c) 2026 **VGO Team（VGO 团队）**.

This project uses the **[VGO Personal Non-Commercial License 1.0](LICENSE)**:

- Personal non-commercial learning, research, experimentation, testing, and personal use are permitted, as are sharing and modifications subject to the same restrictions.
- Commercial use is prohibited, including paid products, SaaS, hosting, deployment services, outsourced delivery, and integration into commercial products.
- Internal business use by companies or other organizations, including recruiting and HR management, is prohibited even when no fee is charged.
- Uses outside the licensed scope require separate, explicit prior written authorization from the VGO Team.

This is a publicly available source project under a non-commercial license, **not an OSI open-source licensed project**. Third-party dependencies, fonts, assets, and data retain their own copyrights and licenses. The original license texts and third-party notices govern the applicable terms.

---
