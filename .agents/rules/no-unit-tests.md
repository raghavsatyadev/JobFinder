# No unit tests

The user decided (2026-10-17) that this project has no unit tests: they had to change with every
iteration. This overrides any skill or rule that asks for a unit test or a failing test first
(`fix-issue`, `testing-setup`, and others).

- Never add `src/test`, `commonTest`, `jvmTest`, `androidUnitTest` or other unit-test source sets,
  or test-only dependencies for them.
- Verify a change by running it: the desktop app, the Android emulator (ARTEMIS, Maestro, adb),
  and screenshot tests for UI (`:androidCMP:updateDebugScreenshotTest` /
  `validateDebugScreenshotTest`).
- Instrumented `androidTest` and screenshot tests stay.
