"""The standard curriculum: a long, language-wide word list, independent of any one user's conversations.

Unlike the Personal Language Map (built only from what a user actually meets in real messages), this is the same
fixed list for everyone learning a given language — a frequency-ranked core vocabulary, so a learner with little or
no message history yet still has something substantial to work through. Counts and ranks only: no example sentences,
no translations are stored here (meanings are supplied the same way the daily micro-lesson already gets them, from
the translation provider, on the Android side).
"""

from functools import lru_cache
from pathlib import Path

from app.core.errors import UnsupportedLanguageError

_DATA_DIR = Path(__file__).parent / "data"


@lru_cache
def _load(language: str) -> tuple[str, ...]:
    path = _DATA_DIR / f"{language}.txt"
    if not path.is_file():
        raise UnsupportedLanguageError(f"No curriculum is available for '{language}' yet.", language=language)
    return tuple(line.strip() for line in path.read_text(encoding="utf-8").splitlines() if line.strip())


class CurriculumService:
    """Pages through one language's word list. Stateless: the caller (the app) keeps its own position in it."""

    def languages(self) -> tuple[str, ...]:
        return tuple(sorted(p.stem for p in _DATA_DIR.glob("*.txt")))

    def page(self, language: str, offset: int, limit: int) -> tuple[list[str], int]:
        words = _load(language)
        return list(words[offset : offset + limit]), len(words)
