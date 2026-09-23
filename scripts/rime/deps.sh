#!/usr/bin/env bash
# Cross-builds librime's dependencies (static) for one Android ABI. Usage: deps.sh x86_64|arm64-v8a
set -euo pipefail
ABI="$1"
NDK=/home/root8401/Android/Sdk/ndk/28.2.13676358
CM=/home/root8401/Android/Sdk/cmake/3.22.1/bin/cmake
SRC=$HOME/engine-spike/librime/deps
OUT=$HOME/engine-spike/rime/out/$ABI
mkdir -p "$OUT" $HOME/engine-spike/rime/build/$ABI
COMMON=(-DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=$ABI -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=$OUT -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DBUILD_SHARED_LIBS=OFF -DCMAKE_MAKE_PROGRAM=make)
build() { # name srcdir extra...
  local name=$1 dir=$2; shift 2
  local b=$HOME/engine-spike/rime/build/$ABI/$name
  rm -rf "$b"; mkdir -p "$b"
  $CM -S "$dir" -B "$b" "${COMMON[@]}" "$@" -G "Unix Makefiles" > "$b.cfg.log" 2>&1 || { tail -20 "$b.cfg.log"; exit 1; }
  $CM --build "$b" -j4 > "$b.build.log" 2>&1 || { tail -30 "$b.build.log"; exit 1; }
  $CM --install "$b" > "$b.install.log" 2>&1
  echo "built $name"
}
case "${2:-all}" in
  all|yaml) build yaml-cpp $SRC/yaml-cpp -DYAML_CPP_BUILD_TESTS=OFF -DYAML_CPP_BUILD_TOOLS=OFF -DYAML_CPP_BUILD_CONTRIB=OFF -DYAML_BUILD_SHARED_LIBS=OFF ;;&
  all|leveldb) build leveldb $SRC/leveldb -DLEVELDB_BUILD_TESTS=OFF -DLEVELDB_BUILD_BENCHMARKS=OFF ;;&
  all|marisa) build marisa $SRC/marisa-trie -DENABLE_TOOLS=OFF ;;&
  all|opencc)
     b=$HOME/engine-spike/rime/build/$ABI/opencc; rm -rf "$b"; mkdir -p "$b"
     $CM -S $SRC/opencc -B "$b" "${COMMON[@]}" -DUSE_SYSTEM_MARISA=ON -DCMAKE_PREFIX_PATH=$OUT -DCMAKE_FIND_ROOT_PATH=$OUT -DENABLE_GTEST=OFF -DCMAKE_CXX_FLAGS=-I$OUT/include -DBUILD_DOCUMENTATION=OFF -DOPENCC_ENABLE_INSTALL=OFF -DBUILD_OPENCC_JIEBA_PLUGIN=OFF -G "Unix Makefiles" > "$b.cfg.log" 2>&1 || { tail -20 "$b.cfg.log"; exit 1; }
     $CM --build "$b" --target libopencc -j4 > "$b.build.log" 2>&1 || { tail -30 "$b.build.log"; exit 1; }
     mkdir -p $OUT/include/opencc; cp $SRC/opencc/src/*.hpp $SRC/opencc/src/*.h $OUT/include/opencc/ ; cp $b/src/*.h $OUT/include/opencc/ 2>/dev/null || true
     find "$b" -name "libopencc*.a" -exec cp {} $OUT/lib/libopencc.a \;
     echo "built opencc" ;;
esac
