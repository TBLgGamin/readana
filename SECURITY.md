# Security policy

## Supported versions

Only the latest release gets security fixes. Update to the newest version
before you report a problem.

## Reporting a vulnerability

Report through GitHub private vulnerability reporting: open the **Security**
tab of this repository and choose **Report a vulnerability**.

Never open a public issue for a vulnerability, and leave real credentials and
personal data out of the report. Include the steps to reproduce it and what an
attacker could do with it.

You can expect an acknowledgement within 7 days and a fix or a published
advisory within 90 days. There is no bug bounty; reporters are credited in the
advisory unless they prefer otherwise.

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
