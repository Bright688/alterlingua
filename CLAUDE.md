# ALTERLINGUA — PROJECT INSTRUCTIONS FOR CLAUDE CODE

## 1. Purpose of This File

This file is the project-wide source of instructions for Claude Code while building AlterLingua.

Read this file completely before implementing any milestone.

Do not contradict these product, architecture, privacy, UX, or development constraints unless explicitly instructed to update them.

Implement only the requested milestone.

Do not automatically proceed to the next milestone.

---

# 2. Product

Product name:

**AlterLingua**

AlterLingua is an Android-first multilingual communication and language-learning layer that works with existing messaging applications, initially WhatsApp.

AlterLingua is NOT another messaging application.

The user continues using WhatsApp normally.

AlterLingua provides:

* an Android keyboard;
* outgoing text translation;
* outgoing voice-to-translated-text;
* incoming text translation assistance;
* incoming voice-note translation;
* a Personal Language Map;
* contextual language learning;
* daily micro-lessons;
* pronunciation practice;
* progress tracking;
* progressively reduced translation assistance.

The recipient does NOT need AlterLingua installed.

---

# 3. Core Promise

**You write in your language. They receive theirs.**

Example:

The user speaks English.

The recipient speaks French.

The user opens WhatsApp and types:

> Are you coming tomorrow?

using the AlterLingua keyboard.

AlterLingua translates this to:

> Tu viens demain ?

The French translation is inserted into the WhatsApp composer.

The user reviews it.

The user presses the normal WhatsApp Send button.

AlterLingua NEVER automatically sends the message.

---

# 4. Product Differentiation

AlterLingua is not simply a translation keyboard.

Its core intelligence is:

**real communication → translation → learning signals → Personal Language Map → micro-learning → increased mastery → reduced translation assistance**

Every useful communication event can contribute to language learning.

The objective is not to make the user permanently dependent on translation.

The objective is to progressively reduce translation dependence.

---

# 5. Core Product Loop

```text
REAL COMMUNICATION
        ↓
TRANSLATION
        ↓
LINGUISTIC SIGNAL EXTRACTION
        ↓
PERSONAL LANGUAGE MAP
        ↓
DAILY MICRO-LESSON
        ↓
KNOWLEDGE INCREASES
        ↓
ALTERLINGUA REMOVES SOME ASSISTANCE
        ↓
MORE REAL-LIFE PRACTICE
        ↓
LESS TRANSLATION
        ↓
FUNCTIONAL INDEPENDENCE
```

This loop is central to the product.

Do not implement translation and learning as unrelated products.

---

# 6. Initial Product Scope

Initial platform:

**Android**

Initial messaging application:

**WhatsApp**

Initial language pair:

**English ↔ French**

The architecture should support additional languages later.

Do not unnecessarily hard-code English and French into core domain logic.

# 6. Language Support

AlterLingua is a **multilingual communication and language-learning product**.

It must NOT be architected as an English/French translation application.

English ↔ French may be used as the first end-to-end development and testing configuration, but this is only a **test configuration**, not a product limitation.

From the beginning, the Android app, AlterLingua keyboard, backend, translation APIs, speech services, Personal Language Map, learning engine, database and UI must be designed for multiple languages.

---

## 6.1 Initial Supported Languages

AlterLingua must initially support at minimum:

| Code | User-facing name |
| ---- | ---------------- |
| `en` | English          |
| `fr` | Français         |
| `es` | Español          |
| `de` | Deutsch          |
| `it` | Italiano         |
| `nl` | Nederlands       |
| `zh` | 中文               |
| `ja` | 日本語              |

These are the initial supported languages, not a permanent closed list.

The architecture must allow additional languages to be added later without redesigning the core application.

Do not hard-code the eight initial languages throughout business logic.

Use a centralized language configuration/catalog.

---

## 6.2 Native Language Names in the UI

When displaying languages in a language selector, prefer the language's native name.

Use:

```text
English
Français
Español
Deutsch
Italiano
Nederlands
中文
日本語
```

Do NOT display:

```text
French
Spanish
German
Italian
Dutch
Chinese
Japanese
```

as the primary language names in the language selector when the native names above are available.

Internally, use standard language codes.

---

## 6.3 User Language Selection

During onboarding, the user selects at least:

**My language**

and

**Language I want to communicate and learn in**

Example:

```text
My language
English

Language I want to learn
Français
```

Another user could select:

```text
My language
Français

Language I want to learn
English
```

or:

```text
My language
English

Language I want to learn
日本語
```

or:

```text
My language
Español

Language I want to learn
Deutsch
```

Do not assume:

```text
source = English
target = French
```

Language selection must be dynamic.

---

## 6.4 Translation Direction

Translation must use the user's current language configuration.

Examples include:

```text
English → Français
English → Español
English → Deutsch
English → Italiano
English → Nederlands
English → 中文
English → 日本語

Français → English
Español → English
Deutsch → English
Italiano → English
Nederlands → English
中文 → English
日本語 → English
```

The architecture should also support combinations between non-English languages when supported by the configured translation provider.

Examples:

```text
Español → Français
Français → Deutsch
Deutsch → Italiano
Nederlands → Français
日本語 → English
中文 → Français
```

Do not create separate application logic for each language pair.

---

## 6.5 Language Codes

Use standardized language identifiers internally.

Initial base language codes:

```text
en = English
fr = Français
es = Español
de = Deutsch
it = Italiano
nl = Nederlands
zh = 中文
ja = 日本語
```

Use appropriate BCP-47 locale identifiers when regional or script-specific behavior is required.

Examples may include:

```text
en-US
en-GB
fr-FR
fr-CI
es-ES
es-MX
de-DE
it-IT
nl-NL
zh-CN
zh-TW
ja-JP
```

Do not unnecessarily tie core domain models to a specific regional locale.

Language and locale are related but should not be treated as identical concepts.

---

## 6.6 Central Language Catalog

Create a centralized language model/configuration rather than duplicating language information across screens and services.

Conceptually:

```text
Language
├── code
├── nativeName
├── displayName
├── locale information
├── translationSupported
├── speechToTextSupported
├── textToSpeechSupported
├── learningSupported
└── writingSystem
```

Example concept:

```text
code: es
nativeName: Español
translationSupported: true
learningSupported: true
```

Feature availability may differ by language and provider.

Do not assume that translation, speech recognition, TTS and pronunciation have identical language coverage.

---

## 6.7 Automatic Source-Language Detection

AlterLingua should support automatic source-language detection where the translation provider supports it.

The keyboard may therefore display:

```text
AUTO → FR
```

or:

```text
AUTO → ES
AUTO → DE
AUTO → IT
AUTO → NL
AUTO → ZH
AUTO → JA
```

These are dynamic states.

`AUTO → FR` is NOT a permanent AlterLingua configuration.

The target language must reflect the user's current selection.

The user must be able to change the target language from the AlterLingua keyboard.

---

## 6.8 Keyboard Language Selector

The keyboard language control must be dynamic.

Example:

```text
AUTO → FR     ✨ Translate     🎤     ⚙
```

If the user changes the target language to Español:

```text
AUTO → ES     ✨ Translate     🎤     ⚙
```

If the user changes it to Japanese:

```text
AUTO → JA     ✨ Translate     🎤     ⚙
```

Tapping the language control should open a compact language selector.

Example:

```text
Translate to

Français
Español
Deutsch
Italiano
Nederlands
中文
日本語
English

Search languages
```

The currently selected language should be visually identifiable.

Do not permanently place every language on the keyboard toolbar.

---

## 6.9 Keyboard Input Language vs Translation Language

Do not assume that the physical/input keyboard layout and translation target are the same thing.

The AlterLingua translation layer must be conceptually separate from Android keyboard layout/input handling.

For example:

```text
User types English
Target = 日本語
```

AlterLingua translates the message to Japanese.

This does not mean AlterLingua should automatically convert the user's typing keyboard into a Japanese IME.

Complex native input methods for Chinese and Japanese should use appropriate Android/platform behavior rather than attempting to recreate mature language input systems unnecessarily.

---

## 6.10 Unicode and Writing Systems

AlterLingua must support Unicode correctly throughout the entire stack.

The initial languages include different writing systems.

Latin script:

```text
English
Français
Español
Deutsch
Italiano
Nederlands
```

Chinese:

```text
中文
```

Japanese:

```text
Kanji
Hiragana
Katakana
```

Do not build assumptions that:

* one character equals one byte;
* one character equals one linguistic token;
* words are always separated by spaces;
* capitalization exists in every language;
* punctuation behaves identically across languages;
* every language uses Latin characters.

Text storage, APIs, UI, analytics and learning systems must be Unicode-safe.

---

## 6.11 Translation API

Translation APIs must accept dynamic source and target languages.

Example:

```json
{
  "text": "Are you coming tomorrow?",
  "source": "auto",
  "target": "es",
  "context": "messaging",
  "tone": "natural"
}
```

Possible response:

```json
{
  "translation": "¿Vienes mañana?",
  "source_language": "en",
  "target_language": "es"
}
```

Japanese example:

```json
{
  "text": "Are you coming tomorrow?",
  "source": "auto",
  "target": "ja",
  "context": "messaging",
  "tone": "natural"
}
```

Do not create endpoints such as:

```text
/translate-english-to-french
/translate-english-to-spanish
```

Use one language-aware translation service.

---

## 6.12 Translation Provider Abstraction

AlterLingua must not tightly couple multilingual support to one translation provider.

Use a translation-provider abstraction.

Conceptually:

```text
AlterLingua
        ↓
Translation Service
        ↓
Provider Abstraction
        ↓
Configured Translation Provider
```

The translation layer should be able to determine whether a requested language pair is supported.

If a provider cannot support a requested language or pair, return a controlled unsupported-language state.

Do not silently translate using the wrong language.

---

## 6.13 Personal Language Maps

Personal Language Maps must be language-specific.

A user's knowledge of a word in Français must not automatically imply knowledge of its equivalent in Español, Deutsch or another language.

Conceptually:

```text
USER
│
├── Français Language Map
│
├── Español Language Map
│
├── Deutsch Language Map
│
├── Italiano Language Map
│
├── Nederlands Language Map
│
├── 中文 Language Map
│
└── 日本語 Language Map
```

Each map independently tracks states such as:

```text
UNKNOWN
LEARNING
FAMILIAR
MASTERED
```

A user may therefore be:

```text
Français: INTERMEDIATE
Español: BEGINNER
Deutsch: BEGINNER
日本語: NO EXPERIENCE
```

without these learning states interfering with one another.

---

## 6.14 Learning Engine

The learning engine must be language-aware.

Do not create a French-specific learning engine.

Example for a user learning Français:

```text
devis
avant midi
je vous tiens au courant
```

For a user learning Español:

```text
presupuesto
antes del mediodía
te mantendré informado
```

For a user learning Deutsch:

```text
Angebot
vor Mittag
ich halte dich auf dem Laufenden
```

For other languages, the learning engine must respect the language's linguistic characteristics.

Language-specific tokenization, segmentation, morphology or NLP behavior should be isolated behind appropriate language-aware components.

---

## 6.15 Chinese and Japanese Learning

Do not assume that learning-unit extraction for 中文 or 日本語 can use the same simple whitespace-based word splitting used for many Latin-script languages.

The learning/NLP layer must support language-appropriate segmentation.

For Japanese, learning content may involve:

* words;
* phrases;
* Kanji;
* Hiragana;
* Katakana;
* readings;
* contextual expressions.

For Chinese, learning content may involve:

* characters;
* words;
* multi-character expressions;
* phrases;
* appropriate pronunciation information.

Do not implement naive whitespace tokenization as the universal vocabulary extraction strategy.

---

## 6.16 Assistance Modes Are Multilingual

All three assistance modes must work independently of the selected language.

### Full Support

Provide maximum translation assistance.

### Adaptive

Use the Personal Language Map for the user's currently selected learning language to determine what assistance can be reduced.

### On-demand

Keep target-language content visible and provide translation when requested.

Do not make Adaptive mode dependent on French-specific rules.

Language-specific processing should be encapsulated in language-aware services.

---

## 6.17 Speech-to-Text

Speech recognition must use dynamic language configuration.

Examples:

```text
English speech
→ Français text

English speech
→ Español text

English speech
→ Deutsch text

English speech
→ Italiano text

English speech
→ Nederlands text

English speech
→ 中文 text

English speech
→ 日本語 text
```

The source speech language may also be non-English.

Example:

```text
Español speech
→ English text
```

or:

```text
日本語 speech
→ Français text
```

where supported.

Do not hard-code English speech recognition.

---

## 6.18 Text-to-Speech

TTS must also be language-aware.

The speech provider abstraction must expose whether a suitable voice exists for the requested language/locale.

Do not assume every language has identical voice options.

Where multiple voices or locales exist, the architecture should permit future user selection.

---

## 6.19 Pronunciation

Pronunciation functionality must be language-aware.

Do not assume pronunciation guidance can be generated using English-style phonetic approximations.

Different languages may require different representations.

Examples may include:

* appropriate pronunciation guidance for Français;
* appropriate pronunciation guidance for Español;
* appropriate pronunciation guidance for Deutsch;
* Pinyin where appropriate for Mandarin Chinese;
* readings/romanization where appropriate for Japanese.

The implementation must preserve the original target-language text.

Pronunciation metadata should supplement the target language rather than replace it.

---

## 6.20 Language Switching

Users must be able to change their active target language.

Example:

```text
Current:
English → Français

Change target

Français
Español
Deutsch
Italiano
Nederlands
中文
日本語
```

Switching from Français to Español must:

* change the translation target;
* load the Español Personal Language Map;
* use Español learning history;
* generate Español learning content;
* use appropriate speech settings;
* preserve the Français Personal Language Map.

Do NOT erase the previous language's learning history.

---

## 6.21 Future Multiple-Language Learning

The architecture should permit a user to maintain learning profiles for multiple languages.

Example:

```text
My languages

Français
67% learning progress
Active

Español
31% learning progress

Deutsch
12% learning progress

日本語
New
```

Only one language may be the active communication/learning target at a given moment in the initial product if this simplifies the MVP.

However, the data model must not prevent multiple language profiles.

---

## 6.22 Initial Development and Testing

For engineering simplicity, the first end-to-end implementation may use:

```text
English ↔ Français
```

This is only the **first test configuration**.

It must never become an architectural restriction.

Once the core translation pipeline is operational, multilingual verification must include at minimum:

```text
English → Français
English → Español
English → Deutsch
English → Italiano
English → Nederlands
English → 中文
English → 日本語
```

Also test at least one non-English source configuration.

For example:

```text
Español → English
```

and at least one non-Latin-script source configuration when the relevant provider functionality is implemented.

For example:

```text
日本語 → English
```

Do not mark the multilingual translation architecture as verified merely because English → Français works.

---

## 6.23 Core Multilingual Rule

At no point should Claude implement AlterLingua as:

```text
English App
      ↓
French Translator
```

The correct model is:

```text
USER'S SELECTED / DETECTED LANGUAGE
                ↓
        ALTERLINGUA ENGINE
                ↓
      SELECTED TARGET LANGUAGE
                ↓
 COMMUNICATION + LANGUAGE LEARNING
```

English ↔ Français is a development configuration.

**Multilingual communication and multilingual learning are product requirements.**



---

# 7. Main Technology Stack

## Android

Use:

* Kotlin
* Jetpack Compose
* native Android APIs
* Android Studio
* Gradle
* DataStore
* Room where appropriate

System components may include:

* InputMethodService
* NotificationListenerService
* Android Share intents
* FileProvider
* audio recording/playback
* background processing where appropriate

Do not replace the native Android architecture with React Native, Flutter, Capacitor, or another cross-platform framework unless explicitly instructed.

---

# 8. Backend

Use:

**Python + FastAPI**

Initial backend architecture:

**modular monolith**

Do not create microservices unless there is a demonstrated technical need.

Potential modules:

```text
backend/app/

api/
auth/
users/
translation/
speech/
learning/
vocabulary/
lessons/
mastery/
entitlements/
usage/
database/
core/
```

Use PostgreSQL when persistent backend storage is required.

---

# 9. Recommended Repository Structure

```text
alterlingua/
│
├── android/
│
│   └── app/
│       └── src/main/java/com/alterlingua/
│
│           ├── ui/
│           │   ├── onboarding/
│           │   ├── home/
│           │   ├── learn/
│           │   ├── words/
│           │   ├── progress/
│           │   └── settings/
│           │
│           ├── keyboard/
│           ├── notifications/
│           ├── translation/
│           ├── voice/
│           ├── learning/
│           ├── entitlements/
│           ├── storage/
│           ├── network/
│           └── share/
│
├── backend/
│
│   ├── app/
│   │   ├── api/
│   │   ├── auth/
│   │   ├── users/
│   │   ├── translation/
│   │   ├── speech/
│   │   ├── learning/
│   │   ├── vocabulary/
│   │   ├── lessons/
│   │   ├── mastery/
│   │   ├── entitlements/
│   │   ├── usage/
│   │   ├── database/
│   │   └── core/
│
├── docs/
│   ├── architecture.md
│   ├── product.md
│   ├── privacy.md
│   ├── entitlements.md
│   ├── progress.md
│   └── build-log.md
│
├── CLAUDE.md
└── README.md
```

Do not create unnecessary layers or abstractions merely to reproduce this structure.

---

# 10. Google Stitch

Google Stitch is the visual design source for AlterLingua.

Claude may access Stitch through MCP when configured.

Before implementing a screen from Stitch:

1. identify the relevant Stitch screen;
2. inspect its design;
3. understand its layout and states;
4. implement it natively using Jetpack Compose;
5. preserve the approved AlterLingua visual system.

Do NOT convert Stitch output into React.

Do NOT treat generated web code as the Android implementation.

Stitch defines the visual intent.

The final implementation must be native Android.

---

# 11. Main AlterLingua Application

The standalone AlterLingua application has five primary areas:

1. Home
2. Learn
3. Words
4. Progress
5. Settings

WhatsApp remains the primary communication interface.

The standalone AlterLingua application primarily handles:

* onboarding;
* learning;
* vocabulary;
* Personal Language Map;
* progress;
* preferences;
* privacy;
* account;
* subscription management later.

---

# 12. Onboarding

The initial onboarding flow should collect:

1. native language;
2. target language;
3. reason for learning;
4. current proficiency;
5. assistance mode;
6. daily reminder preference;
7. AlterLingua keyboard activation;
8. incoming translation / notification access;
9. microphone permission when appropriate;
10. setup completion.

Do not require payment during initial onboarding.

The user should experience AlterLingua before being asked to subscribe.

---

# 13. Assistance Modes

AlterLingua has three assistance modes.

## Full Support

Designed primarily for beginners.

Provide full translation assistance by default.

Known and unknown target-language content may be translated.

Goal:

**immediate comprehension and communication**

---

## Adaptive

This is a major AlterLingua differentiator.

AlterLingua uses the user's Personal Language Map to progressively reduce translation assistance.

Known/mastered vocabulary can increasingly remain in the target language.

Unknown or weak vocabulary receives assistance.

Example progression:

```text
I'll send you the quotation before noon.

I'll envoyer le devis avant midi.

Je vais vous envoyer le devis before noon.

Je vais vous envoyer le devis avant midi.
```

The exact adaptive behavior must be based on learning evidence rather than arbitrary word substitution.

Goal:

**learn while communicating**

---

## On-demand

Target-language content remains visible.

Translation is supplied when the user explicitly requests assistance.

Goal:

**functional independence**

---

# 14. Personal Language Map

The Personal Language Map is a dynamic user-specific model of language knowledge.

It is not merely a CEFR level.

Example:

```text
bonjour
meaning: hello
exposures: 38
status: MASTERED

demain
meaning: tomorrow
exposures: 24
status: MASTERED

réunion
meaning: meeting
exposures: 17
status: FAMILIAR

devis
meaning: quotation
exposures: 8
status: LEARNING

acompte
meaning: deposit
exposures: 2
status: UNKNOWN
```

Primary learning states:

```text
UNKNOWN
LEARNING
FAMILIAR
MASTERED
```

Potential signals include:

* exposure frequency;
* recency;
* translation requests;
* lesson completion;
* successful recognition;
* pronunciation performance;
* repeated use;
* contextual relevance.

Do not treat exposure count alone as proof of mastery.

---

# 15. Daily Learning

AlterLingua should not attempt to teach every word encountered.

The learning engine should identify useful learning opportunities.

The normal daily target is approximately:

**3 words or phrases per day**

Examples:

```text
devis
avant midi
je vous tiens au courant
```

Lessons can contain:

* meaning;
* context;
* listen;
* repeat;
* pronunciation;
* previous exposure;
* spaced repetition.

Learning content should be connected to real communication where privacy constraints permit.

---

# 16. AlterLingua Android Keyboard

The AlterLingua keyboard is a first-class product surface.

It is not merely an accessory to the standalone application.

Implement it using:

**InputMethodService**

It should operate as a real Android input method.

Basic functionality must include:

* QWERTY typing;
* shift;
* backspace;
* space;
* enter;
* punctuation;
* symbols;
* InputConnection integration.

---

# 17. Keyboard Translation Toolbar

The keyboard should support a toolbar concept similar to:

```text
AUTO → FR | Translate | Microphone | Settings
```

AUTO represents automatic source-language detection.

FR represents the target language.

The user must be able to change the target language.

Translation must not occur character-by-character.

Manual Translate is the initial trusted behavior.

More automation may be considered later.

---

# 18. Outgoing Text Translation

Example:

User types in WhatsApp:

```text
Are you coming tomorrow?
```

The user taps:

```text
Translate
```

AlterLingua:

1. obtains composer text through InputConnection where supported;
2. sends the text to the translation service;
3. receives the French translation;
4. allows preview where configured;
5. replaces/inserts:

```text
Tu viens demain ?
```

6. makes Undo available.

The user then presses WhatsApp Send.

CRITICAL:

**AlterLingua must NEVER automatically send the message.**

---

# 19. Translation Undo

When AlterLingua replaces original composer text with a translation, preserve the original text temporarily so Undo can restore it.

Example:

```text
ORIGINAL
Are you coming tomorrow?

TRANSLATED
Tu viens demain ?

UNDO
Are you coming tomorrow?
```

Translation should never unnecessarily destroy user input.

---

# 20. Voice Translation from Keyboard

The AlterLingua microphone is different from WhatsApp's voice-note microphone.

AlterLingua microphone means:

```text
Speak
↓
Transcribe
↓
Translate
↓
Review
↓
Insert as text
```

Example:

User says:

```text
Tell him I'll send the quotation tomorrow morning.
```

Speech-to-text:

```text
Tell him I'll send the quotation tomorrow morning.
```

Translation:

```text
Dites-lui que j'enverrai le devis demain matin.
```

User can:

* review;
* edit;
* listen;
* record again;
* insert as text.

Do not automatically send.

Temporary voice files should be deleted after processing unless explicit retention is required and authorized.

---

# 21. Incoming WhatsApp Text

Incoming translation is separate from the keyboard.

Initial implementation should use:

**NotificationListenerService**

Initially target:

```text
com.whatsapp
```

Where notification text is available:

```text
Marie:
Tu viens demain ?
```

AlterLingua can provide:

```text
Marie:
Are you coming tomorrow?

Translated from French
```

Handle:

* duplicate notifications;
* grouped notifications;
* hidden notification content;
* missing text;
* permissions;
* errors.

Do not claim that AlterLingua replaces the original WhatsApp message bubble with translated text.

---

# 22. Incoming Voice Notes

Do not attempt to access private WhatsApp internal storage.

Initial supported flow:

```text
WhatsApp voice note
↓
Android Share
↓
AlterLingua
↓
content URI
↓
speech-to-text
↓
language detection
↓
translation
↓
translated transcript
↓
learning signals where appropriate
```

Optional:

```text
Listen to translation
```

Do not use unofficial WhatsApp APIs.

Do not reverse-engineer WhatsApp.

---

# 23. Translation Backend

Initial endpoint:

```text
POST /v1/translate
```

Example request:

```json
{
  "text": "Tu viens demain ?",
  "source": "auto",
  "target": "en",
  "context": "messaging",
  "tone": "natural"
}
```

Example response:

```json
{
  "translation": "Are you coming tomorrow?",
  "source_language": "fr"
}
```

Create a translation-provider abstraction.

Do not tightly couple the application domain to one provider.

API credentials must remain server-side.

---

# 24. Speech Backend

Potential endpoints:

```text
POST /v1/audio/transcribe
POST /v1/audio/translate
POST /v1/audio/speak
```

Create a speech-provider abstraction.

Keep:

* STT;
* translation;
* TTS

logically separable.

Do not expose provider secrets in the Android application.

---

# 25. Privacy Principles

AlterLingua processes private communication.

Privacy is a core architecture constraint.

Preferred processing model:

```text
Message
↓
Translation
↓
Linguistic analysis
↓
Useful learning units extracted
↓
Personal Language Map updated
↓
Full message discarded where technically feasible
```

Prefer storing:

```text
devis
encountered 7 times
learning status: LEARNING
```

rather than storing:

```text
Jean told the user at 14:32 to send invoice XYZ...
```

Default principles:

* do not permanently retain complete private conversations unless required and explicitly authorized;
* do not put private message contents into production logs;
* minimize personal data;
* delete temporary audio after processing where technically feasible;
* retain only learning/operational data necessary for the product;
* provide privacy controls;
* separate analytics from private communication content.

---

# 26. Subscription-Ready Architecture

Actual payments are postponed until after the core MVP.

However, the architecture must be subscription-ready now.

Use three entitlement tiers:

```text
FREE
STANDARD
PREMIUM
```

Do not scatter:

```text
isPremium
```

checks throughout the application.

Use a centralized entitlement abstraction.

---

# 27. Feature Entitlements

Features should be represented independently from subscription tiers.

Examples:

```text
TEXT_TRANSLATION
INCOMING_NOTIFICATION_TRANSLATION
PERSONAL_LANGUAGE_MAP
DAILY_LESSONS
FULL_SUPPORT
ADAPTIVE_MODE
ON_DEMAND_MODE
VOICE_TRANSLATION
INCOMING_VOICE_NOTE_TRANSLATION
PRONUNCIATION
TTS_SHARING
ADVANCED_PROGRESS
```

Tier policy determines which features are available.

This allows commercial rules to change without rewriting product logic.

---

# 28. Quotas

Feature availability and usage quotas are separate concepts.

Potential quotas include:

```text
text translations / billing period
voice translations / billing period
voice-note minutes / billing period
pronunciation usage where applicable
```

Do not hard-code commercial limits throughout the codebase.

Use a centralized quota policy.

---

# 29. Proposed Tier Model

## Free

Designed to let users experience AlterLingua's core differentiation.

Potential access:

* normal keyboard;
* limited text translation;
* limited incoming translation;
* basic Personal Language Map;
* daily three-item lessons;
* Full Support;
* limited Adaptive;
* On-demand;
* very limited voice translation.

Do not cripple the core learning loop.

---

## Standard

Primarily for frequent text communication and continued learning.

Potential access:

* higher/fair-use text translation;
* incoming translation;
* full Personal Language Map;
* daily lessons;
* Full Support;
* Adaptive;
* On-demand;
* monthly voice quota;
* limited incoming voice-note translation;
* basic pronunciation;
* advanced progress/history.

---

## Premium

Designed for the complete AlterLingua experience and more expensive AI/audio functionality.

Potential access:

* highest/fair-use translation allowance;
* large voice quota;
* incoming voice-note translation;
* full pronunciation;
* translated voice/TTS sharing;
* advanced progress/history;
* all major assistance modes.

Exact commercial limits and prices are NOT fixed in this file.

They must remain configurable.

---

# 30. Subscription Architecture

Future architecture:

```text
Google Play Billing
        ↓
Backend purchase verification
        ↓
AlterLingua entitlement service
        ↓
FREE / STANDARD / PREMIUM
        ↓
Feature + quota policy
        ↓
Android app / IME / backend services
```

The backend should ultimately be authoritative for paid entitlement state.

Android may cache entitlement information for responsive UX.

Never trust client-only purchase state for paid entitlement.

---

# 31. Payment Timing

Do NOT implement Google Play Billing during the early MVP unless explicitly instructed.

Do NOT put a mandatory subscription/paywall screen in initial onboarding.

Users should first experience AlterLingua.

Later upgrade prompts should be contextual.

Example:

```text
Voice limit reached

You've used your included voice translations
for this billing period.

[ View plans ]

[ Continue typing ]
```

Normal keyboard typing must remain functional even if a paid AI feature is unavailable.

---

# 32. Future Billing Requirements

When the billing milestone is explicitly started, support:

* Google Play Billing;
* Standard;
* Premium;
* monthly subscriptions;
* annual subscriptions;
* backend purchase verification;
* entitlement activation;
* restore/reconcile purchases;
* renewal;
* cancellation;
* expiration;
* applicable grace/account-hold states;
* upgrade/downgrade behavior;
* subscription management.

Before implementing billing, consult current official Google Play Billing documentation.

Do not rely on outdated billing APIs.

---

# 33. Main App Subscription UX

Subscription functionality should eventually integrate naturally into:

**Settings → Plan**

Potential screens:

* Plans;
* comparison;
* Standard;
* Premium;
* usage;
* quota status;
* restore purchases;
* manage subscription;
* successful upgrade;
* cancelled but active;
* expired;
* purchase error.

Do not turn Home into a permanent sales page.

---

# 34. Keyboard Subscription UX

Do not place permanent subscription advertising in the keyboard toolbar.

When a gated feature is requested, use a compact contextual state.

Example:

```text
Voice limit reached

You've used your included voice translations.

[ View plans ]
[ Continue typing ]
```

The keyboard must remain functional.

Never destroy the user's current composer text because a quota has been reached.

---

# 35. Authentication

Account/authentication infrastructure should eventually support:

* secure user identity;
* device/account association;
* entitlement synchronization;
* usage synchronization;
* Personal Language Map synchronization where appropriate;
* safe migration from local/anonymous use;
* logout/session handling.

Do not introduce authentication complexity before required by the milestone.

---

# 36. Design Principles

AlterLingua should feel:

* modern;
* premium;
* calm;
* intelligent;
* minimal;
* trustworthy.

Avoid:

* childish gamification;
* excessive animations;
* clutter;
* aggressive paywalls;
* unnecessary modals;
* manipulative subscription patterns.

Learning can be motivating without resembling a game.

---

# 37. Android Boundaries

Use supported Android APIs.

Do not:

* modify WhatsApp;
* access WhatsApp's private internal database;
* use unofficial WhatsApp APIs;
* bypass Android security;
* pretend arbitrary third-party message bubbles can be directly rewritten;
* automatically send messages;
* implement technically impossible behavior merely because a mockup implies it.

If a requested design conflicts with Android platform constraints, explain the conflict before implementing an unsafe workaround.

---

# 38. Incoming Translation Boundary

Notification translation can only process content Android exposes to AlterLingua.

Do not assume every WhatsApp message will expose complete notification text.

Respect:

* notification privacy settings;
* hidden previews;
* Android restrictions;
* user permissions.

---

# 39. Accessibility / Overlay Features

AccessibilityService or overlay-based functionality may be investigated later.

Do not make it an early dependency.

Before implementing such functionality:

* verify current Android behavior;
* verify current Google Play policy;
* document why the permission is required;
* minimize permission scope.

---

# 40. Offline Behavior

Normal keyboard typing must continue working when the backend is unavailable.

If translation requires connectivity and connectivity is unavailable:

* preserve original text;
* explain the error;
* offer Retry where appropriate;
* do not disable the keyboard.

---

# 41. Error Handling

Errors must preserve user work.

For translation errors:

```text
Couldn't translate.

[ Retry ]
```

Original composer text remains unchanged.

For speech errors:

```text
I couldn't understand that.

[ Try again ]
```

If partial transcription exists, allow the user to inspect/use it where appropriate.

---

# 42. Loading States

Do not block the entire messaging experience unnecessarily.

Examples:

```text
Translating to French...
```

```text
Understanding your message...
```

```text
Translating to French...
```

Prefer compact keyboard-attached states.

---

# 43. Testing

Each milestone must include appropriate testing.

Use:

* unit tests;
* integration tests;
* Android instrumentation/UI tests where useful;
* backend tests;
* manual device testing for Android system integration.

System-level features such as IME, notifications and Share flows must ultimately be tested on a real Android device.

---

# 44. Build Discipline

For every implementation milestone:

1. read CLAUDE.md;
2. inspect relevant existing code;
3. inspect relevant Stitch designs where applicable;
4. state the implementation plan;
5. implement ONLY the requested milestone;
6. compile/build;
7. run appropriate automated tests;
8. fix failures;
9. report exactly what changed;
10. provide manual test instructions;
11. update docs/progress.md after verification;
12. append a milestone entry to docs/build-log.md (see section 46);
13. stop.

Do not automatically start the next milestone.

---

# 45. Do Not Overbuild

Do not add unrelated functionality because it may be useful later.

In particular, do not prematurely implement:

* iOS;
* complex overlays;
* contact intelligence;
* group translation intelligence;
* enterprise administration;
* complex social features;
* microservices;
* subscriptions before their milestone;
* unnecessary AI agents.

Build the smallest robust version that satisfies the current milestone.

---

# 46. Documentation

Maintain:

```text
docs/product.md
docs/architecture.md
docs/privacy.md
docs/entitlements.md
docs/progress.md
docs/build-log.md
```

`docs/build-log.md` is a running narrative record of how AlterLingua is built, so the project owner has a readable history of its construction.

Append an entry at the end of every milestone, and also whenever a significant decision is made or a significant problem is solved.

Each entry should record:

* date and milestone;
* the goal that was requested;
* what was built (files and components added or changed);
* decisions made and why, including any conflict with CLAUDE.md, Stitch or Android constraints;
* problems encountered and how they were fixed;
* verification performed, keeping "implemented" clearly separate from "manually verified";
* manual test instructions for the project owner.

Entries are appended, not rewritten. Never put secrets, API keys or private message content in the build log.

Create `docs/build-log.md` at the start of the first implementation milestone.

`docs/progress.md` should distinguish:

```text
NOT STARTED
IN PROGRESS
IMPLEMENTED
MANUALLY VERIFIED
BLOCKED
```

Do not mark functionality complete merely because code was generated.

---

# 47. Development Milestones

Follow approximately this order unless explicitly instructed otherwise:

```text
1. Environment and repository
2. Native Android foundation
3. Onboarding
4. Android system setup and permissions
5. FastAPI backend
6. Basic AlterLingua IME
7. Translation toolbar
8. Outgoing WhatsApp text translation
9. Backend voice translation
10. Keyboard microphone
11. Incoming WhatsApp text translation
12. Learning event extraction
13. Personal Language Map
14. Words UI
15. Daily micro-lessons
16. Full Support
17. Adaptive
18. On-demand
19. Incoming voice-note Share flow
20. Pronunciation
21. Progress dashboard
22. Translated outgoing voice
23. Privacy/security hardening
24. Pilot analytics
```

Subscription-ready entitlement abstractions may be established before billing, but actual payments remain postponed.

Post-MVP:

```text
25. Authentication & Accounts
26. Subscription Infrastructure
27. Paywalls & Usage Quotas
28. Monetization Analytics
```

The exact numbering can be synchronized with docs/progress.md if the build guide uses different numbering.

---

# 48. Mandatory Product Gates

## Gate A — Real Keyboard

```text
Install APK
↓
Enable AlterLingua keyboard
↓
Open WhatsApp
↓
Select AlterLingua
↓
Type normally
✓
```

Do not proceed as though the keyboard works until this has been manually verified.

---

## Gate B — Real Translation

```text
Open WhatsApp
↓
Type:
Are you coming tomorrow?
↓
Tap Translate
↓
Receive:
Tu viens demain ?
↓
Translation appears in composer
↓
User presses WhatsApp Send
✓
```

AlterLingua must not press Send.

---

## Gate C — Real Voice Translation

```text
Open WhatsApp
↓
Open AlterLingua keyboard
↓
Tap AlterLingua microphone
↓
Speak English
↓
Transcribe
↓
Translate
↓
French text appears
↓
Insert into composer
✓
```

Do not consider the core communication MVP complete before these gates work on a real device.

---

# 49. Security

Never commit:

* API keys;
* passwords;
* access tokens;
* signing secrets;
* production database credentials.

Use environment variables and appropriate secret-management mechanisms.

Ensure `.gitignore` excludes local secret/configuration files where appropriate.

---

# 50. Logging

Production logging must avoid private communication content.

Prefer:

```text
translation_request_completed
source_language=fr
target_language=en
latency_ms=...
```

rather than logging:

```text
Tu viens demain ?
Are you coming tomorrow?
```

The same principle applies to voice transcripts.

---

# 51. Analytics

Analytics must focus on product events, not private message content.

Potential safe events:

* translation initiated;
* translation completed;
* translation failed;
* voice translation initiated;
* lesson completed;
* vocabulary item mastered;
* assistance mode changed;
* quota reached;
* upgrade screen viewed.

Do not send full private messages or transcripts into analytics.

---

# 52. User Control

Users should be able to understand and control:

* language preferences;
* assistance mode;
* notification translation;
* microphone access;
* learning behavior;
* privacy preferences;
* account;
* plan;
* subscription later.

Avoid hidden behavior.

---

# 53. Source of Truth Priority

When requirements conflict, use this priority:

1. explicit current instruction from the project owner;
2. this CLAUDE.md;
3. approved product documentation;
4. approved Stitch design;
5. existing implementation.

If an approved Stitch design depicts behavior that violates a technical or privacy constraint in this file, do not blindly implement it.

Explain the discrepancy.

---

# 54. Claude Code Behavior

Before changing code:

* inspect first;
* understand dependencies;
* avoid unnecessary rewrites;
* preserve working functionality.

After changing code:

* compile;
* test;
* inspect errors;
* fix relevant failures;
* summarize modified files.

Never claim something works unless it has actually passed the relevant test.

Distinguish:

**implemented**

from:

**manually verified**

---

# 55. Current Development Philosophy

AlterLingua should be built progressively.

Priority:

```text
COMMUNICATION
↓
TRANSLATION
↓
VOICE
↓
LEARNING SIGNALS
↓
PERSONAL LANGUAGE MAP
↓
ADAPTIVE LEARNING
↓
PROGRESS
↓
MONETIZATION
```

Do not prioritize monetization over proving the communication and learning loop.

---

# 56. Final Rule

AlterLingua succeeds if it helps users communicate immediately while gradually needing AlterLingua less for translation.

Every major product decision should support that objective.
