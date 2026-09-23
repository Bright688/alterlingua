"""Speech: speech-to-text behind a provider abstraction, and the audio translation pipeline (CLAUDE.md 6.17, 24).

Audio is only ever held in a private temporary file while it is processed, and is deleted afterwards.
"""
