#!/usr/bin/env bash
# Builds librimejni.so for one ABI and strips librime.so. Usage: jni.sh <abi> <output-dir>
set -euo pipefail
ABI="$1"; DEST="$2"
NDK=/home/root8401/Android/Sdk/ndk/28.2.13676358
BIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
case "$ABI" in x86_64) T=x86_64-linux-android26;; arm64-v8a) T=aarch64-linux-android26;; esac
LIB=$HOME/engine-spike/rime/build/$ABI/librime/lib
mkdir -p "$DEST"
$BIN/clang++ --target=$T -shared -fPIC -O2 -std=c++17 -static-libstdc++ -I$HOME/engine-spike/librime/src \
  $HOME/Desktop/alterlingua/native/rime/rimejni.cpp -L$LIB -lrime -Wl,-rpath,'$ORIGIN' -Wl,--build-id=none -llog -o "$DEST/librimejni.so"
$BIN/llvm-strip --strip-unneeded -o "$DEST/librime.so" "$LIB/librime.so"
$BIN/llvm-strip --strip-unneeded "$DEST/librimejni.so"
ls -la "$DEST" | awk '{print $5, $9}'
