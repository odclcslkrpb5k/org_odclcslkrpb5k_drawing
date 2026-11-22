# The whole build environment: the workstation needs only Docker (TC-9).

# resvg, from source: its prebuilt Linux binary needs a newer glibc than the build image has.
# --locked builds the crate's own Cargo.lock; crates.io checksums every crate it fetches.
FROM rust:1-slim-bookworm@sha256:7c7d4eb4d05b7781187a327809466cfddf29753f6defdc3d19a0e291951b7a37 AS resvg
RUN cargo install resvg --version 0.50.0 --locked --root /out

FROM eclipse-temurin:21-jdk-noble@sha256:a09e64f4976df9c4f2029c6abe64fc26f069a342ec9a7f147dce7b1933ebda16

RUN apt-get update \
 && apt-get install --yes --no-install-recommends unzip \
      libpulse0 libnss3 libxcomposite1 libxcursor1 libxdamage1 libxi6 libxtst6 libasound2t64 \
      libgl1 libxkbfile1 libdbus-1-3 \
 && rm -rf /var/lib/apt/lists/*

RUN useradd --create-home --uid 1000 --non-unique builder
USER builder
ENV ANDROID_HOME=/home/builder/android-sdk \
    GRADLE_USER_HOME=/home/builder/.gradle
ENV PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH

# cmdline-tools 23.0; Google publishes SHA-1 only, the SHA-256 was recorded at first download.
RUN mkdir -p $ANDROID_HOME/cmdline-tools \
 && cd /tmp \
 && curl -fsSLo tools.zip https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip \
 && echo "e025545c62a8e64c7559119566a569fb1dec5f60  tools.zip" | sha1sum --check --strict \
 && unzip -q tools.zip -d $ANDROID_HOME/cmdline-tools \
 && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
 && rm tools.zip

# sdkmanager checks each package against the repository's published checksum.
RUN yes | sdkmanager --licenses >/dev/null \
 && sdkmanager --install "platforms;android-37.0" "build-tools;36.1.0" "platform-tools" >/dev/null

# The emulator and an Android 16 (API 36) image, matching the tablet, for instrumented tests. Only
# the emulator service uses them; they sit in their own layer, which changes rarely.
RUN sdkmanager --install "emulator" "system-images;android-36;google_apis;x86_64" >/dev/null

# sdkmanager installs the newest revision of a package and takes no version; the build fails when
# what it installed is not the revision recorded here, so a rebuild cannot drift silently.
RUN sdkmanager --list_installed 2>/dev/null \
   | awk '/^  [a-z]/ && $1 != "cmdline-tools/latest" { print $1, $2 }' | sort >/tmp/sdk \
 && printf '%s\n' "build-tools/36.1.0 36.1.0" "emulator 37.3.10" "platform-tools 37.0.3" \
      "platforms/android-37.0 2.0.0" "system-images/android-36/google_apis/x86_64 7.0.0" \
    | sort >/tmp/want \
 && diff /tmp/want /tmp/sdk && rm /tmp/sdk /tmp/want

# resvg renders the coloring pages' SVGs at build time (tools/templates); built in its own stage.
COPY --from=resvg /out/bin/resvg /home/builder/resvg/resvg
ENV RESVG=/home/builder/resvg/resvg
RUN test "$($RESVG --version)" = "0.50.0"

WORKDIR /repo

# Warm Gradle's caches from the build files and placeholder sources, so a source change does not
# refetch; the offline check is what judges completeness (TS-1).
COPY --chown=builder gradlew gradle.properties settings.gradle.kts build.gradle.kts \
     gradle.lockfile settings-gradle.lockfile buildscript-gradle.lockfile ./
COPY --chown=builder gradle gradle
COPY --chown=builder core/build.gradle.kts core/gradle.lockfile core/
COPY --chown=builder app/build.gradle.kts app/gradle.lockfile app/
COPY --chown=builder app/src/main/AndroidManifest.xml app/src/main/
COPY --chown=builder app/src/main/res app/src/main/res
COPY --chown=builder tools/templates/build.gradle.kts tools/templates/gradle.lockfile tools/templates/
COPY --chown=builder tools/checks/warm tools/checks/
COPY --chown=builder config config
RUN WARM_IN_IMAGE_BUILD=1 tools/checks/warm

COPY --chown=builder . /repo
CMD ["tools/checks/check"]
