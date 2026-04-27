# DeerTitle Agent Guide

## Project Snapshot

- DeerTitle is a Paper/Folia title plugin for Minecraft 1.20.1.
- Runtime target is Java 17+, but the Gradle toolchain uses JDK 21.
- Main entrypoint: [src/main/java/cn/lunadeer/deertitle/DeerTitlePlugin.java](src/main/java/cn/lunadeer/deertitle/DeerTitlePlugin.java).
- Core project overview and command surface live in [README.md](README.md).

## Build And Validation

- Prefer the Gradle wrapper from the repo root.
- Main build: `./gradlew shadowJar`
- Full clean build: `./gradlew 'Clean&Build'`
- Fast compile-only check: `./gradlew compileJava`
- There is no established automated test suite in this repo. For behavior changes, validate with a narrow compile at minimum.

## Architecture Anchors

- `configuration/`: config and language loading. Start with [src/main/java/cn/lunadeer/deertitle/configuration/ConfigService.java](src/main/java/cn/lunadeer/deertitle/configuration/ConfigService.java).
- `database/`: connection setup, schema migration, repository layer. Start with [src/main/java/cn/lunadeer/deertitle/database/DatabaseManager.java](src/main/java/cn/lunadeer/deertitle/database/DatabaseManager.java) and [src/main/java/cn/lunadeer/deertitle/database/repository/RepositoryRegistry.java](src/main/java/cn/lunadeer/deertitle/database/repository/RepositoryRegistry.java).
- `service/`: business logic for titles, shop, and title cards. Prefer changing behavior here instead of inside commands or listeners.
- `command/`: command routing. Start with [src/main/java/cn/lunadeer/deertitle/command/TitleCommand.java](src/main/java/cn/lunadeer/deertitle/command/TitleCommand.java).
- `ui/` and `listener/`: inventory GUI flow and interaction events.
- `display/` and `placeholder/`: PlaceholderAPI integration and fallback player display refresh logic.

## Repo-Specific Rules

- Preserve Folia compatibility. When scheduling or touching player-thread-sensitive work, use the scheduler abstraction in `utils/scheduler/` instead of wiring raw scheduler calls.
- Preserve optional dependency fallbacks. PlaceholderAPI and Vault are soft integrations, and the plugin must still work without them.
- If you change database shape or SQL, keep [src/main/java/cn/lunadeer/deertitle/database/SchemaStatements.java](src/main/java/cn/lunadeer/deertitle/database/SchemaStatements.java), migration code, and repository queries aligned.
- Database tables use the `mplt_` prefix. Do not introduce inconsistent table names.
- Some config fields are declared but not fully wired into runtime behavior yet. Confirm usage in code before assuming a config key is active.
- Keep user-facing text and docs consistent with the existing Chinese-first project documentation unless the task requires otherwise.

## Useful Edit Anchors

- Plugin wiring: [src/main/java/cn/lunadeer/deertitle/DeerTitlePlugin.java](src/main/java/cn/lunadeer/deertitle/DeerTitlePlugin.java)
- Config model and loading: [src/main/java/cn/lunadeer/deertitle/configuration/PluginConfig.java](src/main/java/cn/lunadeer/deertitle/configuration/PluginConfig.java)
- Runtime services: [src/main/java/cn/lunadeer/deertitle/service](src/main/java/cn/lunadeer/deertitle/service)
- Data access: [src/main/java/cn/lunadeer/deertitle/database](src/main/java/cn/lunadeer/deertitle/database)
- Menu flow: [src/main/java/cn/lunadeer/deertitle/ui](src/main/java/cn/lunadeer/deertitle/ui)

## When Updating Instructions

- Link to existing docs instead of copying large sections from [README.md](README.md).
- Keep additions short and behavior-oriented so future agents can load this file cheaply.
