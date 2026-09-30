# OTP Relay builder image: JDK 17 + Android SDK + Gradle 8.13
#
# Build the image (one time):
#   docker build -t otp-relay-builder .
#
# Then build the APK without installing anything locally — see BUILD.md.
# Secrets are passed as env vars at *run* time, never baked into the image.

FROM eclipse-temurin:17-jdk-jammy

ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/opt/gradle-home \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/gradle-8.13/bin:${PATH}

RUN apt-get update \
 && apt-get install -y --no-install-recommends unzip wget \
 && rm -rf /var/lib/apt/lists/*

# Android SDK command-line tools + the packages this project needs
RUN mkdir -p /opt/android-sdk/cmdline-tools \
 && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdtools.zip \
 && unzip -q /tmp/cmdtools.zip -d /opt/android-sdk/cmdline-tools \
 && mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest \
 && rm /tmp/cmdtools.zip \
 && yes | sdkmanager --licenses > /dev/null \
 && sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"

# Gradle 8.13 (the repo has no gradle-wrapper.jar, so ./gradlew cannot be used)
RUN wget -q https://services.gradle.org/distributions/gradle-8.13-bin.zip -O /tmp/gradle.zip \
 && unzip -q /tmp/gradle.zip -d /opt \
 && rm /tmp/gradle.zip

WORKDIR /src
ENTRYPOINT ["gradle"]
