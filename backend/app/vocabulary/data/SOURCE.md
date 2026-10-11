# Where this data comes from

The word lists in this folder (`en.txt`, `fr.txt`, `es.txt`, `de.txt`, `it.txt`, `nl.txt`, `zh.txt`, `ja.txt`) are
the top ~3,000 words, by frequency, from:

**Hermit Dave, [FrequencyWords](https://github.com/hermitdave/FrequencyWords)** — word frequency lists generated
from the OpenSubtitles 2018 corpus. Licensed **CC BY-SA 4.0**.

Source files used: `content/2018/{lang}/{lang}_50k.txt` for en, fr, es, de, it, nl, zh_cn; `content/2018/ja/ja_full.txt`
for Japanese (no pre-cut 50k file exists for Japanese in that repository).

## What was done to them

Downloaded once (2026-10-11), then, per language:
- dropped any entry containing a digit or a punctuation/symbol character;
- for Latin-script languages (en, fr, es, de, it, nl): dropped single-letter entries and entries containing an
  apostrophe (elision fragments such as French `l'`, `j'`, `qu'`, which are not words on their own);
- for Chinese: kept single-character entries (many are real, common words — `的`, `我`, `你`);
- for Japanese: dropped single-character entries (the source list is heavily stemmed; many single-character entries
  are fragments, particles, or bare punctuation, not complete words);
- deduplicated, kept the top 3,000 surviving entries per language, in frequency order.

**Known limitation, Japanese specifically:** the source corpus's tokenization strips inflectional endings, so some
entries that survived the length filter are still verb or adjective stems rather than complete dictionary forms
(for example `分か`, the stem of `分かる`, "to understand"). This was not otherwise fixed — doing so properly needs a
real Japanese morphological dictionary, which is out of scope here. Flagged honestly rather than silently shipped as
if it were clean.

## Attribution

Per CC BY-SA 4.0: this data is adapted from Hermit Dave's FrequencyWords (OpenSubtitles 2018 corpus), itself licensed
CC BY-SA 4.0. The adaptation (filtering described above) is also offered under CC BY-SA 4.0. See the open-source
licences screen in the Android app (Settings → Licences) for the in-app attribution shown to users.
