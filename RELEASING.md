# Chat Chronicle — release workflow

Keep these files current for every version:

1. Update `gradle.properties` and build the matching JAR.
2. Maintain only two README files: `README.md` **in English** for GitHub and Modrinth, and `README.ru.md` in Russian. Include the current version, implemented features, controls, requirements, limitations, and build instructions. Keep release notes in `CHANGELOG.md`.
3. Update `CHANGELOG.md`, keeping previous releases.
4. Update the primary GitHub `README.md` **in English**, including the current JAR path and portable build instructions. Keep the optional Russian guide and this computer's local build paths separately in `README.ru.md`.
5. Update `PLAN.md` and `PROGRESS.md`: completed work, remaining work, and prioritised improvements.
6. Add meaningful regression coverage for changed behaviour and update `TESTING.md` for manual checks.
7. Run `build-mod.bat`; inspect the test report and confirm the JAR contains both language catalogs.

Write publication text around verified behaviour. Distinguish automated tests, client startup, and visual/server/modpack verification. Keep unspecified publication metadata, such as the project licence, as an open decision instead of inventing it.

English is the primary language for the GitHub landing page and Modrinth description. Do not replace those files with Russian text or append a full Russian copy to them; link to the separate Russian documents where appropriate.
