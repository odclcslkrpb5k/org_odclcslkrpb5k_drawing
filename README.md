# Drawing

A finger-drawing and coloring app for a child's tablet: no ads, no purchases, no network.

## Build and check

Needs Docker only.

    docker compose -f docker/compose.yaml run --build --rm check

runs every check offline: unit tests, Android Lint, and a release build.

## Configure

Nothing reads the environment. Device: Samsung Galaxy Tab A11+ (Android 16, 1920×1200, landscape).

## Install and operate

Release signing and installation are documented once the release key and the install rehearsal
are done (planned before the first install on the tablet).
