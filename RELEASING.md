# Chat Chronicle — release workflow

Keep these files current for every version:

1. Update `gradle.properties` and build the matching JAR.
2. Maintain only two README files: `README.md` **in English** for GitHub and Modrinth, and `README.ru.md` in Russian. Include the current version, implemented features, controls, requirements, limitations, and build instructions. Keep release notes in `CHANGELOG.md`.
3. Update `CHANGELOG.md`, keeping previous releases.
4. Update the primary GitHub `README.md` **in English**, including the current JAR path and portable build instructions. Keep the optional Russian guide and this computer's local build paths separately in `README.ru.md`.
5. Update `PROGRESS.md`: completed work, remaining work, and prioritised improvements. Keep `PLAN.md` local and outside Git.
6. Add meaningful regression coverage for changed behaviour and update `TESTING.md` for manual checks.
7. Run `build-mod.bat`; inspect the test report and confirm the JAR contains both language catalogs.

8. Run `gradlew releaseCheck` once for a release candidate, and again only if a changed storage/search behaviour affects its results. It uses temporary archives, a million generated messages and a child JVM that is forcibly terminated; it never targets a running player's client.
9. Inspect the installable JAR and matching `build/distributions/chat-chronicle-VERSION-source.zip`. Both must contain LICENSE/NOTICE. The source ZIP includes project sources, build files, Gradle wrapper and the bundled libraries' Maven source JARs. Publish matching source access alongside binary downloads. Preserve the third-party notices in `licenses/`.

The project license is **GPL-3.0-only**, copyright **TPexcoTblu**. This does not change the licenses of bundled libraries. Write publication text around verified behaviour. Distinguish automated tests, client startup, and visual/server/modpack verification. Do not mark a build stable just because it compiles or a release task passes.

English is the primary language for the GitHub landing page and Modrinth description. Do not replace those files with Russian text or append a full Russian copy to them; link to the separate Russian documents where appropriate.
