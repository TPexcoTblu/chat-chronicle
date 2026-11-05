# Chat Chronicle — release workflow

Run `gradlew.bat build`, then `python tools/verify_matrix.py` (Python 3.9+). Installable files are under `build/releases/GAME/LOADER/`. Publish each file with its exact game version and loader on the same project. The matching source archive is `build/distributions/chat-chronicle-VERSION-source.zip`. Record game testing separately from successful compilation.

## Publish the matrix to Modrinth

The publisher reads the version from `gradle.properties` and the game targets from `gradle/targets.json`. It requires the full verified matrix and checks each JAR's size and SHA-256 before making any requests. Every game and loader pair gets its own Modrinth version number, such as `0.2.1+mc26.3.fabric`. The JAR's internal mod version remains `0.2.1`.

Create `.modrinth.local.json` in the repository root. Git ignores this file and the source archive excludes it. Use the eight-character project ID and its current slug. Paste your [Modrinth personal access token](https://modrinth.com/settings/pats) with the `VERSION_CREATE` scope into the local file, or leave `token` empty and set `MODRINTH_TOKEN` in the environment.

```json
{
  "project_id": "AABBCCDD",
  "expected_slug": "your-project-slug",
  "token": ""
}
```

```powershell
# Offline preview. The default channel is beta.
python tools/publish_modrinth.py

# Read the configured project and compare the matrix with its published versions.
python tools/publish_modrinth.py --check-remote

# Publish after reviewing the preview and making matching source available.
python tools/publish_modrinth.py --publish --channel beta --changelog .tools/release-notes.md --source-url 'https://github.com/OWNER/REPO/releases/download/TAG/chat-chronicle-VERSION-source.zip'
```

Use the same channel for preview and publication. Supply a Markdown file containing the English notes for this release and replace the example source URL with the actual matching source. The publisher uses the [Modrinth version API](https://docs.modrinth.com/api/operations/createversion/) and prints each resulting version link.

For a trial upload, add `--game 26.3 --loader fabric`. The publisher still verifies the complete matrix and only sends the selected JAR.

Run one publisher at a time for a project. If an upload stops halfway through, run the same command again. The publisher skips identical, listed uploads with matching game version, loader and channel. It reports any conflict before uploading further files. For a private or unpublished project, the token must also be able to see the project and its existing versions. HTTP 404 during the remote check can mean that access is missing or that the ID is incorrect.

## Release checklist

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
