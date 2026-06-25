# LmVIP P/G/E Status

phase: done
qa_mode: plugin
current_task: task-001-dual-artifact-1201
owner: Codex
updated_at: 2026-06-25

## Current Gate

Implement the approved dual-artifact compatibility plan:

- legacy artifact: `LmVIP-1.12.2.jar`, Java 8, current 1.12.2 baseline.
- modern artifact: `LmVIP-1.20.1.jar`, Java 17, Paper + Arclight 1.20.1 target.

## Next Legal Action

Close this sprint after commit/push. QA verification has passed for Gradle tests, clean build, jar metadata, and class major version checks.

Runtime evidence:

- Arclight 1.20.1 `cell-06` basic smoke passed with LmCore database profile `LmVIP`, LuckPerms, and PlaceholderAPI present.
- Arclight compatibility verdict is `DEGRADED`, which is expected for the first validation pass.
- Paper 1.20.1 runtime smoke is not covered yet.
- Arclight business command smoke is not covered yet because the temporary OP/permission setup in the test cell failed to load cleanly.
