# Drawing

A finger-drawing and coloring app for a child's tablet: no ads, no purchases, no network.

## Build and check

Needs Docker only.

    docker compose run --build --rm check

runs every check offline: format, lint (detekt and Android Lint), unit tests, licences, a release
build and its merged manifest. Other services: `plants` (each check's planted bug), `vulns`
(known vulnerabilities; needs a network), `ship-tests`, and `tools/emulator <scenario>` (a
headless Android 16 emulator; needs /dev/kvm).

## Play it on this computer

    tools/play            # the tablet as it was left: drawings, stars and both apps kept
    tools/play --clean    # a tablet fresh from the box

An emulated tablet shaped like hers (1920x1200 landscape, Android 16) opens in a window on this
X desktop, scaled to fit the screen, with the app built and installed: the mouse is her finger.
It needs Docker, /dev/kvm and an X display; it changes nothing on this machine. Its state lives in
~/.local/share/odclcslkrpb5k-android/tablet, shared with the crossing game's tools/play.

## Configure

Nothing reads the environment. Device: Samsung Galaxy Tab A11+ (Android 16, 1920×1200, landscape).

## Install and operate

The release key is made once by a person with `tools/release/make-key`, kept outside every
repository and backed up offline in two places: a lost key means uninstalling the app, and its
drawings, to update it. Installation steps follow the install rehearsal on the tablet.

Parent setup on the tablet: the first time the app opens full screen, Android shows a one-time
"Viewing full screen" notice; dismiss it with "Got it".
