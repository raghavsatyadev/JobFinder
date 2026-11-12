---
trigger: always_on
description: JobFinder-specific overrides and facts for the shared agent-workflow skills and rules. Wins over them.
---

# Project overrides — JobFinder

Shared skills/rules are links into agent-workflow (`scripts/link-project.sh`); this file and
`.agents/flows/` are this repo's own.

- **Theme / M3E**: generic `material3-expressive` applies; helpers come from the SCUS reference.
- **Preview example**: `androidCMP/src/screenshotTest/kotlin/io/github/raghavsatyadev/jobfinder/screenshots/AgentsScreenshotTest.kt AgentsCopiedScreenshot`.
- **Tests**: see `no-unit-tests` and `shared-code-first` rules (project-local).
- **Device flows**: `.agents/flows/` (`tabs.yaml` runs against the fake backend).
