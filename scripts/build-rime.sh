#!/usr/bin/env bash
# Builds librime (Chinese input) and its JNI layer for Android and copies them into the app.
# Needs: git, curl, Android SDK with NDK 28.2.13676358 and CMake 3.22.1, about 6 GB of disk. Takes 20-40 minutes.
# The sub-scripts in scripts/rime/ hold the details; they use ~/engine-spike as the work directory and the paths inside them
# (NDK and CMake locations) may need editing for another machine.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$HOME/engine-spike"
mkdir -p "$WORK/rime"; cd "$WORK"
[ -d librime ] || git clone --depth 1 https://github.com/rime/librime.git
(cd librime && git submodule update --init --depth 1 deps/leveldb deps/marisa-trie deps/opencc deps/yaml-cpp)
if [ ! -d boost-inc/boost ]; then   # Boost headers only (Boost Software License)
  curl -fL -o boost.tar.gz https://archives.boost.io/release/1.87.0/source/boost_1_87_0.tar.gz
  mkdir -p boost-inc && tar -xzf boost.tar.gz --strip-components=1 -C boost-inc boost_1_87_0/boost && rm boost.tar.gz
fi
cp "$HERE"/rime/*.sh "$WORK/rime/"
for ABI in x86_64 arm64-v8a; do
  "$WORK/rime/deps.sh" "$ABI" all
  cp "$WORK/librime/deps/opencc/src/"*.h "$WORK/rime/out/$ABI/include/opencc/"
  "$WORK/rime/rime.sh" "$ABI"
  "$WORK/rime/jni.sh" "$ABI" "$HERE/../android/app/src/rime/jniLibs/$ABI"
done
