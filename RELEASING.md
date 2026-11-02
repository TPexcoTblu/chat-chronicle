# Chat Chronicle — release workflow

Keep these files current for every version:

1. Update `gradle.properties` and build the matching JAR.
2. Update `MODRINTH_README.md`: current version, implemented features, controls, requirements, limitations, and the current release notes. This is the public Markdown description for Modrinth; keep both language sections accurate.
3. Update `CHANGELOG.md`, keeping previous releases.
4. Update the local developer `README.md`, including the current JAR path.
5. Update `PLAN.md` and `PROGRESS.md`: completed work, remaining work, and prioritised improvements.
6. Add meaningful regression coverage for changed behaviour and update `TESTING.md` for manual checks.
7. Run `build-mod.bat`; inspect the test report and confirm the JAR contains both language catalogs.

Write publication text around verified behaviour. Distinguish automated tests, client startup, and visual/server/modpack verification. Keep unspecified publication metadata, such as the project licence, as an open decision instead of inventing it.
