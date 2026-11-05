#!/bin/sh
set -eu
cd "$(dirname "$0")"
BUILD_DIR="$(mktemp -d)"
trap 'rm -rf "$BUILD_DIR"' EXIT
if [ -n "${JAVA_HOME:-}" ]; then
  JAVAC="$JAVA_HOME/bin/javac"
  JAVA="$JAVA_HOME/bin/java"
else
  JAVAC=javac
  JAVA=java
fi
"$JAVAC" -encoding UTF-8 -d "$BUILD_DIR" app/src/main/java/com/gentv/fishlauncher/FishingSession.java app/src/main/java/com/gentv/fishlauncher/KoiPose.java app/src/main/java/com/gentv/fishlauncher/TurtlePose.java app/src/main/java/com/gentv/fishlauncher/PondWorld.java app/src/test/java/com/gentv/fishlauncher/FishingSessionTest.java app/src/test/java/com/gentv/fishlauncher/PondWorldTest.java app/src/test/java/com/gentv/fishlauncher/KoiPoseTest.java app/src/test/java/com/gentv/fishlauncher/TurtleTest.java
"$JAVA" -cp "$BUILD_DIR" com.gentv.fishlauncher.PondWorldTest
"$JAVA" -cp "$BUILD_DIR" com.gentv.fishlauncher.KoiPoseTest
"$JAVA" -cp "$BUILD_DIR" com.gentv.fishlauncher.TurtleTest

"$JAVA" -cp "$BUILD_DIR" com.gentv.fishlauncher.FishingSessionTest
