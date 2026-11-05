## What

<!-- One or two sentences: what changes and why. Link the issue/milestone if there is one. -->

## How to verify

<!-- Commands run, screens tapped on the Pad 8, screenshots for UI changes. -->

## Checklist

- [ ] PR title is a Conventional Commit (it becomes the squash commit message)
- [ ] `./gradlew spotlessApply detekt` is clean
- [ ] `./gradlew testDebugUnitTest test` passes
- [ ] `./gradlew :app:lintDebug` passes
- [ ] Docs/ADRs/CHANGELOG updated when behaviour or architecture changed
