# Spec: LmVIP Dual Artifact 1.20.1 Compatibility

## Goal

Produce two LmVIP artifacts from the same business codebase:

- `LmVIP-1.12.2.jar` keeps the existing Java 8 / 1.12.2 baseline.
- `LmVIP-1.20.1.jar` uses Java 17 bytecode and targets Paper + Arclight 1.20.1 validation.

## Constraints

- Do not change database schema or VIP business behavior for this split.
- Do not change public `LmVipApi` signatures.
- Keep plugin name `LmVIP` in both artifacts so data folders, configs, and service lookup remain stable.
- Put version differences in build configuration, generated metadata, and internal compatibility diagnostics.

## Acceptance

- Both Gradle test tasks pass.
- `clean build` creates both jar files.
- Legacy classes are Java 8 major version 52.
- Modern classes are Java 17 major version 61.
- README and knowledge docs describe the support matrix and verification commands.
