# Task: task-001-dual-artifact-1201

## Role

Generator

## Goal

Implement dual Gradle artifacts for LmVIP and add runtime compatibility diagnostics for 1.12.2 and 1.20.1 targets.

## Success Criteria

- `:lmvip-legacy` builds `LmVIP-1.12.2.jar` with Java 8 target.
- `:lmvip-modern` builds `LmVIP-1.20.1.jar` with Java 17 target and Paper 1.20.1 compile API.
- Both artifacts keep plugin name `LmVIP`.
- Modern artifact declares Bukkit API version `1.20`; legacy artifact does not.
- Startup logs include artifact target, Java runtime, server type/version, dependency states, and compatibility verdict.

## Allowed Paths

- `build.gradle.kts`
- `settings.gradle.kts`
- `src/main/**`
- `src/test/**`
- `src/modern/**`
- `docs/workflow/**`
- `README.md`
- `knowledge/**`

## Denied Paths

- No production server directories.
- No database migration files outside the existing repository schema logic.
- No global memories.

## Acceptance Commands

- `F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP :lmvip-legacy:test :lmvip-modern:test --stacktrace`
- `F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP clean build --stacktrace`

Modern tests run on Java 17. The build resolves this from `LMVIP_JAVA17_HOME`, `JAVA17_HOME`, `JDK17_HOME`, or the first `java.exe` on `PATH`.

## Stop Rules

- Stop and report if modern compile requires changing public API or database behavior.
- Stop and report if TabooLib cannot generate separate plugin metadata per module.
