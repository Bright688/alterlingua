#!/usr/bin/env bash
set -euo pipefail
ABI="$1"
NDK=/home/root8401/Android/Sdk/ndk/28.2.13676358
CM=/home/root8401/Android/Sdk/cmake/3.22.1/bin/cmake
OUT=$HOME/engine-spike/rime/out/$ABI
B=$HOME/engine-spike/rime/build/$ABI/librime
rm -rf "$B"; mkdir -p "$B"
$CM -S $HOME/engine-spike/librime -B "$B" -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=$ABI -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX=$OUT -DCMAKE_PREFIX_PATH=$OUT -DCMAKE_FIND_ROOT_PATH="$OUT;$HOME/engine-spike/boost-inc" -DCMAKE_MAKE_PROGRAM=make \
  -DBoost_INCLUDE_DIR=$HOME/engine-spike/boost-inc -DBoost_NO_BOOST_CMAKE=ON -DBUILD_STATIC=ON -DBUILD_SHARED_LIBS=ON -DENABLE_LOGGING=OFF -DBUILD_TEST=OFF \
  -DBUILD_DATA=OFF -DBUILD_MERGED_PLUGINS=OFF -DENABLE_THREADING=ON -G "Unix Makefiles" > "$B.cfg.log" 2>&1 || { tail -25 "$B.cfg.log"; exit 1; }
$CM --build "$B" --target rime -j4 > "$B.build.log" 2>&1 || { grep -E "error|Error" "$B.build.log" | head -15; exit 1; }
find "$B" -name "librime*.so" | head; echo done
