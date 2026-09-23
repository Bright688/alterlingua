#!/usr/bin/env bash
# Builds Mozc's Android library and data file and copies them into the app. Needs: git, python3 (3.12+), bazelisk, a C++ compiler,
# about 6 GB of free memory and 10 GB of disk. Mozc downloads its own NDK (r29) and Qt sources. Takes 30-60 minutes.
# Usage: scripts/build-mozc.sh [work-dir]   (default: ~/engine-spike)
set -euo pipefail
WORK="${1:-$HOME/engine-spike}"
APP="$(cd "$(dirname "$0")/.." && pwd)/android/app/src/mozc"
mkdir -p "$WORK"; cd "$WORK"
[ -d mozc ] || git clone --depth 1 https://github.com/google/mozc.git
cd mozc/src
python3 build_tools/update_deps.py
bazelisk build package --config oss_android --config release_build --jobs=4 --local_resources=memory=5500
bazelisk build //data_manager/oss:mozc_dataset_for_oss --config release_build --jobs=4 --local_resources=memory=5500
mkdir -p "$APP/jniLibs" "$APP/assets/mozc"
rm -rf /tmp/mozclibs && unzip -q -o bazel-bin/android/jni/native_libs.zip -d /tmp/mozclibs
cp -r /tmp/mozclibs/libs/arm64-v8a /tmp/mozclibs/libs/x86_64 "$APP/jniLibs/"
cp bazel-bin/data_manager/oss/mozc.data "$APP/assets/mozc/mozc.data"
chmod -R u+w "$APP"
