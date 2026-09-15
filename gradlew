#!/bin/sh
# Gradle wrapper script that downloads gradle if needed

GRADLE_VERSION=8.2
GRADLE_DIR="$HOME/.gradle/wrapper/dists/gradle-$GRADLE_VERSION"

if [ ! -d "$GRADLE_DIR" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    mkdir -p "$GRADLE_DIR"
    curl -sL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o /tmp/gradle.zip
    unzip -q /tmp/gradle.zip -d "$GRADLE_DIR"
    rm /tmp/gradle.zip
fi

exec "$GRADLE_DIR/gradle-$GRADLE_VERSION/bin/gradle" "$@"
