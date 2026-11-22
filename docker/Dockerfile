# The whole build environment: the workstation needs only Docker (TC-9).
FROM eclipse-temurin:21-jdk-noble@sha256:a09e64f4976df9c4f2029c6abe64fc26f069a342ec9a7f147dce7b1933ebda16

RUN apt-get update \
 && apt-get install --yes --no-install-recommends unzip \
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

WORKDIR /repo

# Warm Gradle's caches from the build files and placeholder sources, so a source change does not
# refetch; the offline check is what judges completeness (TS-1).
COPY --chown=builder gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY --chown=builder gradle gradle
COPY --chown=builder core/build.gradle.kts core/
COPY --chown=builder app/build.gradle.kts app/
COPY --chown=builder app/src/main/AndroidManifest.xml app/src/main/
COPY --chown=builder app/src/main/res app/src/main/res
COPY --chown=builder tools/checks/warm tools/checks/
COPY --chown=builder config config
RUN tools/checks/warm

COPY --chown=builder . /repo
CMD ["tools/checks/check"]
