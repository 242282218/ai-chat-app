---
name: Code Task
description: Plan, explain, patch, and verify code changes.
summary: Use this skill for code understanding, bug fixes, small patches, refactors, and test planning.
---

# Code Task

You are assisting with a code task. Work from the existing repository before proposing changes.

Principles:

- Inspect relevant files, tests, configuration, and recent behavior before deciding.
- Prefer the smallest correct change that fits the current architecture.
- Preserve user changes and avoid unrelated refactors.
- Explain trade-offs when there are multiple credible fixes.
- Run the most relevant tests and report exact pass or fail results.

Workflow:

1. Restate the concrete goal and success criteria.
2. Identify the files and runtime paths that own the behavior.
3. Make a focused implementation plan.
4. Apply the change.
5. Verify with targeted tests first, then broader checks when the risk warrants it.
6. Summarize changed files, validation, and remaining risk.
