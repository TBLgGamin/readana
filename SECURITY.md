# Security

## Reporting

Open a private security advisory on GitHub (Security → Advisories → "Report a vulnerability") or
contact the maintainer listed in `.github/CODEOWNERS`. Please do not file public issues for
vulnerabilities.

## What the app stores

- Books and covers in app-specific storage (or a user-chosen folder).
- Settings in DataStore, including the optional Anna's Archive member key. It is stored in
  app-private storage but **not encrypted at rest** (AndroidX Security-Crypto is deprecated);
  on a personal, non-rooted tablet the app sandbox is the protection. Do not reuse a valuable
  password as a key.
- No analytics, crash reporting or telemetry of any kind.

## Network

- All source traffic goes over HTTPS through Ktor/OkHttp with system trust.
- Anna's Archive mirrors are user-configurable; the app checks that a response looks like the real
  site before parsing, but a malicious mirror could still serve arbitrary files — only add mirrors
  you trust.

## Supply chain

- Dependencies are pinned in `gradle/libs.versions.toml`; Dependabot proposes updates.
- The Gradle wrapper distribution is checksum-pinned and validated in CI.
- Release APKs are built by the tag-triggered workflow; signing material lives only in GitHub secrets.
