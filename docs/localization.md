# Localization and the three language settings

Status: **foundation implemented, screens only partly migrated. Nothing has been checked on a phone.** This document says exactly
what is done, what is not, and how to finish it. It implements CLAUDE.md-style rule 19 of the product brief: interface language,
source language and target language are three independent settings.

## 1. The three settings

| Setting | Field in `UserSettings` | Meaning | Used for |
| --- | --- | --- | --- |
| **App language** | `appLanguage`, `appLanguageChosen` | The language AlterLingua itself is shown in | Which `values-xx` string resources are used: screens, the keyboard's own labels and errors, AlterLingua's notifications |
| **Source / default language** | `nativeLanguage`, `detectSourceAutomatically` | What the user normally writes or speaks; with detection on, the language of each message is detected (AUTO) | The source sent with a translation request (`auto` or the fixed code), the toolbar's left code, the language incoming messages are translated **into**, the spoken language of keyboard voice |
| **Target language** | `targetLanguage` | What messages are translated into and what is being learned | The translation target, the active Personal Language Map, lessons, pronunciation |

`UserSettings.languagePreferences` presents them as `UserLanguagePreferences` (app language, source language, source detection
`AUTO` or `FIXED`, active target language, and the interface `locale`), with `requestSource` and the keyboard's `directionLabel`.
The field `nativeLanguage` is the source/default language under its older name. Keyboard layout preferences are **not** stored yet
(the keyboard has one layout per language family).

**Rules, each covered by a test:** changing the app language changes nothing else (not the source, the target, levels, progress or
any Personal Language Map); changing the source or target never changes the app language; no code assumes app language equals
source language or that the source is English; one map per target language is untouched.

## 2. How the app language is applied

- `AppLanguage.wrap(context, language)` returns a context whose resources use that language's locale. Nothing else is affected.
- Every AlterLingua screen extends `LocalizedActivity`, which wraps its context before the screen is built and recreates the screen if
  the app language changes while it is open. (The language is read once, synchronously, from the local settings file, because a
  screen's resources must be chosen before its first frame.)
- The keyboard builds its view in the app language and rebuilds it when the language changes. AlterLingua's translated-message
  notification follows it too.
- Until the user has chosen an app language (first launch), the phone's language is used. A user who finished onboarding before this
  change is never asked and keeps the phone's language until they choose one in Settings.
- Language names are always shown in their **own** language (Español, Deutsch, 中文, 日本語), from the language catalogue, in every
  interface language.

## 3. What is localized today

- **String resources for all eight languages:** `values` (English), `values-fr`, `-es`, `-de`, `-it`, `-nl`, `-zh`, `-ja`. 105 translatable
  strings each (keyboard toolbar, translation and voice status and errors, navigation labels, the translated notification, the
  Share-sheet name, and the new language screens). The four brand strings (`app_name`, `keyboard_name`, `notification_listener_name`,
  `notif_public_title`) are marked not translatable. `LocalizationResourcesTest` checks that every language has the same keys, the same
  placeholders, no empty or untranslated values, and the exact wording the brief lists (the eight "Choose your language" prompts,
  Translate, Settings).
- **First launch, L1:** `AppLanguageScreen` lists the eight languages by their own names with "Choose your language" written in each,
  preselects the phone's language, applies the choice on Continue, then continues to the existing onboarding.
- **Settings, Languages:** App language, Source / default language, Detect source automatically (toggle), Target language, and the note
  that the three are independent. All of it is resource-based and localized.
- **Keyboard:** the toolbar shows `AUTO → FR` with detection on and the source code (`ES → FR`) with a fixed source; Translate sends `auto`
  or the fixed source. Its labels and messages follow the app language.

## 4. NOT done (this is the remaining work)

1. **Screen strings are migrated** (about 350 further strings, 453 per language). Left in English on purpose: placeholder sample data
   (`SampleContent`, Words/Home samples), engine and diagnostic explanations, the pilot report and previews. The seven translations are
   AI-written and have not been reviewed by native speakers. There is no ratchet test yet against new literals.
2. **Onboarding order is done:** app language (L1), source with AUTO toggle, target, reason, level, mode, reminder, keyboard, incoming,
   microphone, complete (11 steps). Not run on a phone; the earlier `OnboardingFlowTest` tail may have been lost in the rewrite.
3. **Keyboard preferences** are not in the data model or in Settings.
4. **Keyboard voice** uses the source language (never AUTO); incoming messages always use AUTO. Neither changes with the toggle.
5. **Chinese and Japanese typing.** As the brief says, AlterLingua localizes its own toolbar and sheets only; the layout for typing 中文
   and 日本語 is the phone's own input method's job and is not replaced.
6. **Sample content** in Home, Learn and Words follows the selected target language; other samples were not audited.
7. **Design:** in Stitch only L2 and L3 (source and target choice) were confirmed as created; the requests for L1, L4, L5 and L6 timed out
   and L7 was never sent, so L1 and the Settings section follow the written brief, not an approved visual.
8. **Device checks:** the language switch, the recreated screens, the keyboard rebuild and the notification wording were never run on a
   phone (an instrumented test for the resource choice is compile-checked only).

## 5. How to finish a screen

1. Replace each literal with `stringResource(R.string.some_key)` (or `context.getString` outside Compose).
2. Add the English text to `values/strings.xml`; add the seven translations to `values-xx/strings.xml`.
3. Run the unit tests: `LocalizationResourcesTest` fails until every language has the key with matching placeholders.
4. New screens extend `LocalizedActivity` (already true for all four activities).

## 6. Keyboard letters follow the typing language

The letter layout follows the user's own language (the source/default language chosen in onboarding), not the app language or the
target: someone writing in Français gets AZERTY, Deutsch QWERTZ with ü ö ä ß, Español QWERTY with ñ, everyone else QWERTY.
Accented letters (é è ê ç, á í ó ú, à ì ò ù, ë ï and so on) are on long press. Digits are 0-9 in all eight languages. ¿ ¡ (Español),
« » (Français) and „ “ (Deutsch) are on the second symbol page. 中文 and 日本語 keep the QWERTY keys used to type pinyin and romaji:
AlterLingua does not rebuild those input methods (CLAUDE.md 6.9), so a Chinese or Japanese writer should use the phone's own keyboard
for native characters. Not built: Italian and Dutch dedicated accent keys, per-language spacing rules (for example the space before
? ! : ; in French), and choosing a layout different from the source language. Implemented and unit-tested; not run on a phone
(the long-press strip in particular needs a real device).

## 7. A row of the language's own characters (added after phone feedback)

Where the letters alone did not make a keyboard look like its language, every language except English now gets a slim extra row above the keys, on every page: Français é è ê à ù ç ô î ï œ; Español á é í ó ú ü ñ ¿ ¡ €; Deutsch € „ “ ‚ ‘ » « – § °; Italiano à è é ì ò ù ó á € «; Nederlands é ë ï ó ö ü á è € ’; 中文 ，。？！、：；“”（; 日本語 「」ー〜・…？！（）. The layout still follows "My language". Chinese letter keys stay Latin because pinyin is typed with Latin letters; Zhuyin, Wubi, stroke or 9-key layouts are not built, and no keyboard-style setting exists yet. Implemented and unit-tested; the on-screen look on the phone has not been checked (the phone screen was locked).

**Seen on the owner's phone (Infinix, Android 15, arm64), September 2026:** with "My language" set to 中文 the keyboard shows the extra row (，。？！、：；“”（), pinyin letters, ，。 beside a space bar labelled 中文, and the toolbar in Chinese; typing "nihao" underlines it and shows candidates 你好, 你, 拟, 尼, 呢, 泥 from the real librime engine. Committing a candidate, the other seven languages, Japanese kana, and long-press accents were not checked on the phone.

## 8. Typing styles for 中文 and 日本語 (revised after owner feedback)

The default keys of 中文 and 日本語 are their own characters, not Latin letters. Each language has more than one common way to type, chosen in onboarding (right after "My language") and in Settings, where the choices are listed **only for the language typed in ("My language"), and only when it is 中文 or 日本語** (`UserSettings.keyboardStyles`, saved per language; a style from another language is ignored).
- **中文, Strokes 笔画 (default):** the keys are the five basic strokes ⼀ ⼁ ⼃ ⼂ ⼄ (sent to the engine as h s p n z) and Chinese punctuation ，。？！、：. Candidates are Simplified characters, ordered by frequency. Data: the stroke codes of rime-stroke (LGPL-3.0) filtered to the characters of the pinyin dictionary, with frequencies from rime-essay (LGPL-3.0): `stroke_simp.dict.yaml`, 28k entries for 16k characters.
- **中文, Zhuyin 注音:** the keys are the 37 bopomofo symbols (ㄅ ㄆ ㄇ ㄈ ...) on the standard (Daqian) key positions; five rows. The tone marks are **not** typed (the pinyin dictionary has no tones), so give the initial and final and choose. Simplified characters, from the pinyin dictionary through rime-bopomofo's spelling rules (LGPL-3.0, `zhuyin.yaml`).
- **中文, Pinyin (QWERTY):** the earlier keyboard with Latin letters and the extra row of Chinese marks.
- **日本語, Kana (flick) (default):** the 12-key kana keyboard; flick a key left, up, right or down for the next kana of its row (あ then い う え お; や then （ ゆ ） よ; わ then を ん ー 〜), hold for the small and voiced forms.
- **日本語, Romaji (QWERTY):** Latin letters converted by Mozc, with 、。 and the extra row of Japanese marks.
Removed after feedback: pinyin on nine number keys (T9). Not built: handwriting, Wubi, Cangjie, tone keys for Zhuyin, tap-to-cycle kana, a style switch key on the keyboard (removed at the owner's request). Voice input is the keyboard's existing microphone.
Engine details: the Rime engine returns the raw typed input and a separate text to show ("ni hao" with the syllables split); Enter keeps the raw input. Tests: unit tests for the layouts (stroke keys, 37 Zhuyin keys on the standard positions, every row adds up), the saved styles and the onboarding step (12 steps for 中文 and 日本語, 11 for the others, skipped both ways); instrumented tests on an emulator with the real engine (strokes "hs" offer 十, Zhuyin "su" offers 你 and "sucl" offers 你好, pinyin as before; 76 of 76 pass). Flick gestures, the Zhuyin five-row height and the new screens have not been looked at on a phone.

## 9. Handwriting for every language (added after owner request)

A pen key next to the space bar (all layouts, all eight languages) opens a drawing pad in place of the keys: candidates on top, a pad to write on with backspace and enter beside it, and ABC / globe / space / period below (the space bar says "Handwriting" in the app language). Strokes are recognised a moment (0.6 s) after the pen is lifted, all strokes so far together, so multi-stroke characters work; tapping a candidate types it and clears the pad; backspace first wipes the drawing, then deletes text; ABC returns to the keys. Recognition uses Google ML Kit Digital Ink Recognition (on the phone; language models downloaded once, about 20 MB each; needs Google Play services), chosen by the owner over an open-source-only option that would not cover the Latin languages. Recognition languages: English, Français, Español, Deutsch, Italiano, Nederlands, 中文 (Simplified), 日本語. See docs/privacy.md.
Tested: the pad logic with a stand-in recogniser (8 unit tests: pause, several strokes, choosing, clearing, downloads and retry); the real ML Kit on the emulator (the model downloads and two strokes of a T are read as "T"); every layout has exactly one pen key. Not verified: writing with a finger on a phone, Chinese or Japanese recognition quality, the pad's look, behaviour without Google Play services or offline.
