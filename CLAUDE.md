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

## Shipping
Standing permission (SH-1): commit and push to `main` once `docker compose -f docker/compose.yaml
run --build --rm check` passes on the tree being committed. Never force-push or skip hooks (SH-6).

## Never in this repository (it is public)
The child's name, drawings, photos or any data from the device; parent-supplied art (that lives in
`../private-assets/`); signing keys.
