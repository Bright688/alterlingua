# Chinese and Japanese input engines (decision record)

**Status: Mozc RUNS in the app's test on an emulator (see "Mozc from Kotlin"); librime not tried; the keyboard uses Mozc for 日本語 (emulator-tested); librime/中文 not started; the app's keyboard still shows QWERTY for 中文 and 日本語.
The owner chose "any open-source engine" (September 2026) after the design board showed a pinyin keyboard and a 12-key kana keyboard.
Licence facts below were read from the projects' own pages on 2026-09-21; the exact third-party notices still have to be re-read from
the files we would actually ship.

## Recommendation

| Language | Engine | Engine licence | Data licence | Notes |
|---|---|---|---|---|
| 日本語 | Mozc (`libmozc.so`) | BSD-3-Clause | Dictionary derived from IPAdic/NAIST (permissive, keep notices), Okinawa dictionary (public domain), ICOT-derived entries (no-warranty notice) | The Mozc repository has an Android library build (its own docs: "How to build Mozc for Android"). |
| 中文 | librime (Rime) with a pinyin schema | BSD-3-Clause | `rime-luna-pinyin` is LGPL-3.0 | Community Android frontends exist (Trime, fcitx5-android, others). |

Not chosen: fcitx5-android is a complete keyboard app under LGPL-2.1 and loads plugins from other APKs, so it cannot be embedded in
AlterLingua's own keyboard. Its Japanese support is Anthy, not Mozc.

## What integrating means (why this is its own milestone)

1. **Toolchain.** Neither engine is pure Kotlin. This machine has the Android SDK but **no NDK and no CMake** (fcitx5-android's build
   notes ask for NDK 25 and CMake 3.22.1; Mozc's build differs and must be read from its own docs). Installing them is a decision for the owner.
2. **Native libraries per ABI** (arm64-v8a at least), wrapped in a small Kotlin interface: `compose(keys) -> candidates`, `commit(candidate)`.
   That interface is where the tests go; the engines stay behind it so a third one can be added later.
3. **Data files.** Mozc's dictionary and Rime's schema and dictionary ship with the app (tens of MB). Size, install time and updates need a look.
4. **Keyboard UI.** Candidate bar, composing text (an underlined pinyin or kana string in the field via `setComposingText`), pinyin keyboard
   with `，。？！`, and the 12-key flick kana keyboard, as drawn in the design board (`Language and localization screens`).
5. **Licence compliance.** An in-app "Open source licences" screen with the BSD, IPAdic, Okinawa and LGPL notices; LGPL data must stay
   replaceable (kept as separate data files, not merged into code).
6. **Privacy.** Both engines run on the phone. Composing text, candidates and any learning history stay on the device, are never logged
   and never sent to the translation backend until the user presses Translate (as with any other typing). Any engine "user dictionary"
   or learning feature is off or stored locally only.

## Order of work (proposed)

1. Owner approves installing NDK and CMake on this computer.
2. Spike: build each engine for arm64-v8a and call it from a unit/instrumented test. Record real sizes and build times here.
3. Kotlin interface plus fake engine and tests; candidate bar and composing text in the keyboard.
4. Pinyin layout, then kana layout; licences screen; docs.
5. Real-device check (the only way to judge feel, latency and candidate quality).

Nothing here is verified by running code.

## Spike results (2026-09-21)

- **Installed on this computer:** Android NDK 28.2.13676358 and CMake 3.22.1 (SDK). Mozc also downloaded its own NDK r29 and Qt sources
  into its build tree (`~/engine-spike`, outside this repository).
- **Mozc builds.** `bazelisk build package --config oss_android --config release_build` (Bazel 9.0.2, Mozc commit `13c9898`)
  finished successfully: 1,144 actions after resuming a first run stopped at 1,698 of 2,839. Build with `--jobs=4` on a
  computer with about 6 GB of free memory worked.
- **Output:** `native_libs.zip`, 15.8 MB, holding `libmozc.so` for four ABIs: arm64-v8a 16.2 MB, armeabi-v7a 12.9 MB, x86_64 15.2 MB,
  x86 14.1 MB (uncompressed). The size suggests the dictionary is inside the library; **not yet checked**.
- **Not done:** calling the library from Kotlin. Mozc's own Android app code was removed from the repository, so the native session API
  has to be wrapped by us (the last revision with Android client code is named in Mozc's build guide). Not done: librime, the
  candidate bar, any keyboard change. **Nothing has been run on a phone.**

## Mozc from Kotlin (2026-09-21)

- **Added:** `keyboard/engine/CandidateEngine` (interface), `CompositionController` (underlined composing text, candidates, space/enter/backspace while composing; 9 unit tests with a stand-in dictionary), and `MozcEngine` (the real engine). Protobuf classes for Mozc's `commands.proto` were generated with protoc 4.29.3 into `app/src/mozc/java` (committed; runtime `protobuf-javalite` 4.29.3), plus `MozcJni.java` (fixed package name required by the library). `TextTarget.setComposingText` was added.
- **Binaries, not committed:** `app/src/mozc/jniLibs/{arm64-v8a,x86_64}/libmozc.so` (16.2 and 15.2 MB) and `app/src/mozc/assets/mozc/mozc.data` (19.0 MB, the dictionary; it is a separate file, not inside the library). Rebuild with `scripts/build-mozc.sh`. The debug APK with both ABIs is 43.7 MB; shipping arm64 only will be smaller.
- **Privacy:** the engine is told to run incognito with no history learning; nothing is logged or sent.
- **Emulator test (real Mozc, x86_64 Android 36 emulator on this computer):** `MozcEngineTest`, 4 of 4 pass: library and data load, romaji "kyou" gives きょう with candidates, kana input きょう offers 今日 and choosing it commits 今日, backspace removes the last kana. **This is an emulator, not a phone**; the arm64 library has not been run, and quality and speed on real hardware are unchecked.
- **Not done:** using `MozcEngine` in the keyboard (composition wiring in the service, candidate bar, 12-key kana layout, the Japanese punctuation page), an opt-in for engine data size, the open-source licences screen, librime for 中文 (nothing tried), a Rime data licence review (LGPL-3.0 files).

## 日本語 in the keyboard (2026-09-21)

- **Layout:** 12-key kana (あ か さ / た な は / ま や ら / わ 、 。) with backspace, 空白 (space), enter, `?123`, and the keyboard switch key. Holding a key offers the rest of its row, the small forms and the voiced forms (か: か き く け こ が ぎ ぐ げ ご). 「」 are on the second symbol page.
- **Behaviour:** while the source language is 日本語 and Mozc has loaded, kana keys go to `CompositionController`: the typed kana is underlined in the field, a candidate strip above the keys shows the kana and the kanji candidates, tapping a candidate commits it, space commits the first candidate, enter keeps the kana (it never sends). Symbols, page switches and the toolbar actions (Translate, voice) first keep whatever is typed. If Mozc cannot start, the kana keys type kana directly.
- **Loading** (library and the 19 MB data file copied once) happens off the main thread when the language is chosen.
- **Emulator tests (real Mozc, Android 36 x86_64):** `MozcEngineTest` 4/4 and `JapaneseTypingTest` 2/2 (typing きょう into a real `EditText` shows きょう underlined, choosing 今日 fills the field with 今日; space converts, enter keeps kana). Unit tests for the layout (14 layout tests) pass.
- **Not verified:** the keyboard service and candidate strip on screen (no device test drives the real service), tapping and long-pressing kana keys, an arm64 phone, speed and quality of candidates on real hardware. Modifier behaviour is by long-press only (no ゛゜ key). No learning history is kept (incognito).

## 中文 with librime (2026-09-21)

- **Built:** librime (commit `82bb921`, BSD-3-Clause) cross-compiled for arm64-v8a and x86_64 with the NDK, with yaml-cpp, LevelDB, marisa-trie and OpenCC built as static libraries and Boost headers (1.87.0); logging, tests, plugins and OpenCC's dictionary data are off. `librime.so` is 4.7 MB stripped per ABI. Our own JNI layer is `native/rime/rimejni.cpp` (37 KB per ABI). Rebuild everything with `scripts/build-rime.sh` (work directory `~/engine-spike`, paths inside `scripts/rime/*.sh` are for this computer and may need editing).
- **Data:** the small `pinyin_simp` schema (Simplified characters, Apache-2.0, derived from Android's own Pinyin IME) with the default, key-binding, punctuation and symbol files from `rime-prelude` (LGPL-3.0). Source files are committed in `android/app/src/rime/assets/rime/shared/`; they are compiled on the phone the first time (a few seconds, off the main thread). The schema's stroke reverse-lookup was removed. **Quality caveat:** this dictionary is small ("袖珍", pocket-sized): single characters and common words, no sentence model. A bigger schema (for example luna_pinyin with OpenCC simplification, or a community schema) is a later improvement and needs its data licence reviewed.
- **Keyboard:** 中文 keeps QWERTY letters (pinyin), with full-width ，。 beside the space bar, Chinese quotes, colon, semicolon, exclamation and question marks on the first symbol page, and 《》 on the second. Lowercase letters go to the conversion; capitals, punctuation and symbols are typed as they are. Same candidate strip, space, enter and backspace behaviour as 日本語.
- **Emulator tests (real librime, x86_64 Android 36):** `RimeEngineTest` 3/3 and `ChineseTypingTest` 2/2 pass: "nihao" offers 你好 and choosing it fills the field; space converts "xie"; enter keeps the typed letters. The whole instrumented suite (74 tests) passes on the emulator.
- **Open source licences:** Settings now has "Open source licences" (translated into all eight languages) showing the licence texts of Mozc, librime, yaml-cpp, LevelDB, marisa-trie, OpenCC, Boost, the pinyin_simp schema and Protocol Buffers, from `assets/licenses/NOTICES.txt`. Not yet checked by eye; no test opens it.
- **Not verified:** any of this on a phone (the arm64 libraries were built and not run), on-screen typing, candidate quality for real sentences, first-start delay on a slow phone, memory use with both engines loaded, and whether the schema files copied on first start survive an app update correctly (they are recopied when the app updates). The librime data compiled on the phone is not shared between ABIs.
- **APK:** the debug build with both ABIs of both engines is about 59 MB before splitting by ABI.
