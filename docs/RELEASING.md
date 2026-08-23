# Releasing

A release is a git tag. `.github/workflows/release.yml` does the rest: it runs the same gates CI
runs, builds a signed release APK, and publishes a GitHub Release with notes taken from the
changelog.

## One-time setup: the signing key

Without these secrets the workflow still works, but the APK is **debug-signed** — fine for
side-loading onto your own tablet, useless for anything else. The job logs a warning saying so.

```bash
keytool -genkeypair -v -keystore release.jks -alias readana \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks          # on macOS: base64 -i release.jks
```

Add four repository secrets (**Settings → Secrets and variables → Actions**):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the base64 output above |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `readana` |
| `KEY_PASSWORD` | the key password |

Keep `release.jks` somewhere safe and off GitHub. Losing it means every future build is a different
app as far as Android is concerned, and an installed copy can no longer be upgraded — only
uninstalled and replaced, which takes the library with it. `.gitignore` already covers `*.jks` and
`keystore.properties`.

To sign a release **locally** instead, put a git-ignored `keystore.properties` next to
`settings.gradle.kts` with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. That is the
same file the workflow writes, so there is one code path for both.

## Cutting a release

1. **Roll the changelog over.** Rename `## [Unreleased]` to `## [X.Y.Z] - YYYY-MM-DD`, add a fresh
   empty `## [Unreleased]` above it, and update the link definitions at the bottom of the file.
   The release notes are this section verbatim, so read it as the announcement it is about to
   become.
2. **Commit it** — a Conventional Commit like `chore(release): 0.2.0`.
3. **Dry run first.** Actions → Release → *Run workflow*, with the version you are about to tag.
   Nothing is published; the APK, `mapping.txt` and the rendered notes come back as an artifact,
   and the log says whether the APK was really signed.
4. **Tag and push.**
   ```bash
   git tag -a v0.2.0 -m "Readana 0.2.0"
   git push origin v0.2.0
   ```
5. The workflow publishes the release. Check that the notes read properly and that `mapping.txt` is
   attached — without it, a crash report from a release build is unreadable, since R8 runs.

## Version numbers

`versionName` comes from the tag; nothing is edited by hand. `versionCode` is derived from it —
`1.2.3` → `10203` — so it can only ever go up, which is what Android requires of an upgrade. A
build with no `-PreadanaVersion` uses the fallback in `app/build.gradle.kts`, which is every build
that is not a release.

Versions follow [SemVer](https://semver.org/). While the app is pre-1.0, a release that changes how
something already stored is read (the database, DataStore) is worth a minor bump, not a patch.

## Baseline profiles

The startup profile in `app/src/release/generated/baselineProfiles/` is generated on a real device,
because CI has none. Regenerate it when startup or the reader changes shape, before cutting a
release:

```bash
./gradlew :app:generateBaselineProfile     # with the tablet attached; see docs/DEVICE.md
```

Commit the regenerated `baseline-prof.txt` with the release commit.
