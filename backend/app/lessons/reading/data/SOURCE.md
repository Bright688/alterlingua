# Where this data comes from

The sentence lists in this folder (`en.txt`, `fr.txt`, `es.txt`, `de.txt`, `it.txt`, `nl.txt`, `zh.txt`, `ja.txt`)
are real, natural sentences from:

**[Tatoeba](https://tatoeba.org)** — a crowd-contributed multilingual sentence corpus. Tatoeba's bulk sentence
exports are released under **CC BY 2.0 FR**.

Source files used: `per_language/{iso3}/{iso3}_sentences.tsv.bz2` for eng, fra, spa, deu, ita, nld, cmn, jpn
(downloaded 2026-10-18).

## Attribution

Per Tatoeba's own FAQ guidance for bulk text reuse, a collective credit is sufficient (no per-sentence author
citation is required for text, only for reused audio, which this app does not use from Tatoeba): "Example
sentences from [Tatoeba](https://tatoeba.org), licensed CC BY 2.0 FR." See the open-source licences screen in the
Android app (Settings → Licences) for the in-app attribution shown to users, the same place `FrequencyWords`
(the Vocabulary curriculum's own source) is credited.

## What was done to them

Downloaded once (2026-10-18), then, per language:

- kept only sentences between 8 and 140 characters (long enough to be worth reading, short enough to stay
  approachable; Tatoeba also has one-word fragments and some very long compound quotes, neither of which suit a
  short reading exercise);
- dropped any sentence containing a digit, a URL, an `@` mention, or a stray control character;
- deduplicated;
- **sampled across the length range rather than simply taking the shortest sentences overall**: for busy languages
  (English alone has over two million candidate sentences after filtering) just taking the "top N shortest" collapses
  entirely into trivial one-liners with no difficulty progression. Sentences were bucketed into 10 equal-width bands
  between 8 and 140 characters, then up to 200 per band were kept (sorted shortest-first within each band), so the
  final file still runs genuinely easy to hard;
- capped at 2,000 sentences per language. Chinese (1,011) and Japanese (1,419) came up short of that cap — there
  simply weren't 200 surviving candidates in every length band for those two languages in this corpus. This is an
  honest reflection of what's actually available, not a bug.

**Known limitation**: sentence length in characters is used as the sole difficulty proxy, the same spirit as the
Vocabulary curriculum's frequency-rank proxy (CLAUDE.md's own "frequency-based CEFR proxy, not an officially
validated placement" caveat applies here too). This is rougher for Chinese and Japanese, where each character
carries more information than a Latin-script character, so a length-matched Chinese/Japanese sentence may read as
more or less complex than its length alone suggests. Not otherwise corrected — a proper per-language difficulty
model is out of scope here, same as the vocabulary data's own documented limitations.

**Not narrative passages**: these are standalone sentences, not a connected story — Tatoeba sentences are
independently contributed and not written to follow on from one another. A reading exercise is several of these
shown together (see `ReadingService`/`GET /v1/reading/{language}`, paged the same way the Vocabulary curriculum is),
each a real, complete, natural sentence worth reading and translating on its own, not fabricated or AI-generated
filler text.
