"""The standard Reading exercise: a long, language-wide list of real sentences, independent of any one user's
conversations -- the Reading counterpart to the Vocabulary curriculum (app/vocabulary/service.py), same shape and
same spirit. Sentences only: no translations are stored here (meanings are supplied the same way every other
piece of target-language text in this app gets them, from the translation provider, on the Android side). See
data/SOURCE.md for where these sentences come from and how they were chosen.
"""

from functools import lru_cache
from pathlib import Path

from app.core.errors import UnsupportedLanguageError

_DATA_DIR = Path(__file__).parent / "data"


@lru_cache
def _load(language: str) -> tuple[str, ...]:
    path = _DATA_DIR / f"{language}.txt"
    if not path.is_file():
        raise UnsupportedLanguageError(f"No reading practice is available for '{language}' yet.", language=language)
    return tuple(line.strip() for line in path.read_text(encoding="utf-8").splitlines() if line.strip())


class ReadingService:
    """Pages through one language's sentence list. Stateless: the caller (the app) keeps its own position in it."""

    def languages(self) -> tuple[str, ...]:
        return tuple(sorted(p.stem for p in _DATA_DIR.glob("*.txt")))

    def page(self, language: str, offset: int, limit: int) -> tuple[list[str], int]:
        sentences = _load(language)
        return list(sentences[offset : offset + limit]), len(sentences)
