# Frontend Directory Agent Instructions

## Repository boundary

- This `ui/` directory owns the VGO ATS web frontend, frontend-specific skills, and product/design context within the shared `VGO_ATS` repository. Paths below are relative to `ui/` unless stated otherwise.
- Production frontend code belongs in `src/`.
- The Java/Spring backend remains at the parent repository root. Do not copy backend modules or Maven build files into `ui/`, add `ui/` to the Maven Reactor, or create a nested Git repository. Run frontend commands here or use `npm --prefix ui` from the repository root.

## Required context

- Before production frontend changes, load `.agents/skills/hr-ai-frontend-development/SKILL.md` and only the references relevant to the affected behavior. Reuse unchanged context already read in this session.
- For layout, visual direction or product behavior changes, read the relevant sections of `PRODUCT.md` and `DESIGN.md`; a local default-value, copy or CSS fix needs only the applicable rule and incumbent implementation. Do not read the entire product history for each fix.
- Run frontend skill scripts with `ui/` as their working directory so generated context remains in the frontend.
- Existing ATS controls and clear local changes use the HR AI frontend skill. Use an available design skill for requested visual exploration/review or an unresolved design decision; do not stack design workflows for routine implementation.
- Shared product, architecture, permission and implementation facts remain in root `docs/`. Follow relevant links when the current change needs those facts.

## Delivery rules

- Git branch, focused Chinese commits, existing-change preservation, authorization, planning isolation and multi-agent coordination inherit [root AGENTS.md](../AGENTS.md); do not maintain a second copy here.
- Keep AI recommendations evidence-led and preserve the HR user's final decision authority.
- Select validation using [testing-and-accessibility.md](.agents/skills/hr-ai-frontend-development/references/testing-and-accessibility.md); reuse the same task's current evidence instead of repeating equivalent checks.
- Keep generated dependencies such as `node_modules/` out of version control.
