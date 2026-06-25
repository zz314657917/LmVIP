# Agent Matrix

## Roles

- Planner: Codex. Owns compatibility plan, contract, scope, and final decisions.
- Generator: Codex in this run. Implements build/module split and compatibility diagnostics.
- Evaluator: Codex. Reviews diff, runs Gradle checks, inspects jar names and class major versions.

## Worker Policy

- No worker is active for this sprint.
- If this task is resumed with workers, Developer and QA workers must use isolated worktrees under `E:/codex-worktrees/LmVIP/`.
