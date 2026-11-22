# Drawing app — agent instructions

A finger-drawing and coloring app for one child's Samsung Galaxy Tab A11+ (Android 16). Durable
context (plan, decisions, notes) lives in the private workspace repo, `../claude-notes/`; read
`../claude-notes/index.md` first.

## Standard
This project follows [Paranoid TypeScript](https://github.com/odclcslkrpb5k/paranoid-typescript),
last conformed to commit `04c926637d15`, as carried into Kotlin by the draft appendix in
`../paranoid-android/kotlin-android.md` (RP-6).

Deviations, each with its reason:
- **RP-3:** notes live in the workspace repo's `claude-notes/`, shared by every app, not in this repo.
- **SDK:** `targetSdk 36` with the large-screen resizability opt-out and a landscape lock; lint's
  `OldTargetApi`, `DiscouragedApi` and `NonResizeableActivity` are suppressed for that reason only.
- Controls not yet built are listed in `../claude-notes/drawing.md` under "Gate status".

## Checks
- `docker compose run --build --rm check` — every check, offline: casts, vendored files, ktfmt,
  detekt, unit tests, lint, release build, the release APK's manifest, and the build-warning list.
- `docker compose run --build --rm plants` — each control's planted bug must be caught (TS-5).
- `docker compose run --build --rm dev tools/checks/relock` — after a dependency change: rewrites
  lockfiles and verification metadata from an empty Gradle home. Review every new checksum.
- Never run `tools/checks/warm` outside the image build; it deletes sources (it refuses to).

## Shipping (chapter 10)
The ship tool is vendored in `tools/ship/` (manifest `tools/vendor-manifest.tsv`; RP-4). Its
path is the tool's own, not `vendor/`, because its scripts and tests name `tools/ship/`; that is a
deviation from RP-4's layout, not its content rule. Enable hooks once per clone:
`git config core.hooksPath .githooks`. A ship:

    tools/ship/timed --start
    tools/ship/steps                         # gate, check, then plants / ship-tests by path
    tools/ship/commit-and-push <tree> <message-file>
    tools/ship/close-ship --nothing | --sent "<candidates for the standard>"
    tools/ship/timed --report

Standing permission (SH-1): the agent commits and pushes to `main` through this cycle without
asking. Never force-push or skip hooks (SH-6). Harvest into `../claude-notes/` before committing.

## Never in this repository (it is public)
The child's name, drawings, photos or any data from the device; parent-supplied art (that lives in
`../private-assets/`); signing keys.
