"""Turns the language a Whisper model reports into an ISO 639-1 code.

Groq's Whisper answers with the language's English name ("french"); Cloudflare's answers with a code ("fr"). The rest of
AlterLingua uses codes, and the catalogue decides which of them are supported, so a language outside the catalogue
(Portuguese, say) is still reported as its code and refused later as unsupported rather than mistaken for another one.
"""

_NAMES: dict[str, str] = {
    "english": "en", "chinese": "zh", "german": "de", "spanish": "es", "russian": "ru", "korean": "ko", "french": "fr",
    "japanese": "ja", "portuguese": "pt", "turkish": "tr", "polish": "pl", "catalan": "ca", "dutch": "nl", "arabic": "ar",
    "swedish": "sv", "italian": "it", "indonesian": "id", "hindi": "hi", "finnish": "fi", "vietnamese": "vi", "hebrew": "he",
    "ukrainian": "uk", "greek": "el", "malay": "ms", "czech": "cs", "romanian": "ro", "danish": "da", "hungarian": "hu",
    "tamil": "ta", "norwegian": "no", "thai": "th", "urdu": "ur", "croatian": "hr", "bulgarian": "bg", "lithuanian": "lt",
    "latin": "la", "maori": "mi", "malayalam": "ml", "welsh": "cy", "slovak": "sk", "telugu": "te", "persian": "fa",
    "latvian": "lv", "bengali": "bn", "serbian": "sr", "azerbaijani": "az", "slovenian": "sl", "kannada": "kn",
    "estonian": "et", "macedonian": "mk", "breton": "br", "basque": "eu", "icelandic": "is", "armenian": "hy",
    "nepali": "ne", "mongolian": "mn", "bosnian": "bs", "kazakh": "kk", "albanian": "sq", "swahili": "sw", "galician": "gl",
    "marathi": "mr", "punjabi": "pa", "sinhala": "si", "khmer": "km", "shona": "sn", "yoruba": "yo", "somali": "so",
    "afrikaans": "af", "occitan": "oc", "georgian": "ka", "belarusian": "be", "tajik": "tg", "sindhi": "sd", "gujarati": "gu",
    "amharic": "am", "yiddish": "yi", "lao": "lo", "uzbek": "uz", "faroese": "fo", "haitian creole": "ht", "pashto": "ps",
    "turkmen": "tk", "nynorsk": "nn", "maltese": "mt", "sanskrit": "sa", "luxembourgish": "lb", "myanmar": "my",
    "tibetan": "bo", "tagalog": "tl", "malagasy": "mg", "assamese": "as", "tatar": "tt", "hawaiian": "haw", "lingala": "ln",
    "hausa": "ha", "bashkir": "ba", "javanese": "jv", "sundanese": "su", "cantonese": "zh",
}


def confidence_of(segments: object) -> float | None:
    """The mean ``avg_logprob`` of Whisper's segments (weighted by their length when known), or None if none report it."""
    if not isinstance(segments, list):
        return None
    total = weight = 0.0
    for segment in segments:
        if not isinstance(segment, dict):
            continue
        logprob = segment.get("avg_logprob")
        if not isinstance(logprob, (int, float)) or isinstance(logprob, bool):
            continue
        start, end = segment.get("start"), segment.get("end")
        length = (end - start) if isinstance(start, (int, float)) and isinstance(end, (int, float)) and end > start else 1.0
        total += logprob * length
        weight += length
    return total / weight if weight else None


def to_code(reported: object) -> str | None:
    """"French", "fr" or "fr-FR" -> "fr"; None when nothing usable was reported."""
    if not isinstance(reported, str):
        return None
    value = reported.strip().lower().replace("_", "-")
    if not value:
        return None
    if value in _NAMES:
        return _NAMES[value]
    base = value.split("-")[0]
    if base in _NAMES.values():
        return base
    return None
