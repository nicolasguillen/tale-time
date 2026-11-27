# Narration: how the clips are made

Nothing in the repo, the pipeline or the app synthesizes speech. Narration clips are made **outside the repo, only when the user asks**, with the tool the user chose for that language. They're delivered into `content/stories/<id>/audio/<lang>/` like any recorded clip. Each story's `REQUESTED_AUDIO.md` in that language holds the scripts, the direction and the spec.

**The end of every story** (`the-end`, `more-endings`, `all-endings`, from `content/app/narration.json`) is made the same way, once per language and not per story: the same tool, voice and recipe as that language's stories, the scripts and directions in `content/app/audio/<lang>/REQUESTED_AUDIO.md`, and the clips delivered next to it.

| Language | Tool | Model | Voice | Account |
|---|---|---|---|---|
| English (`en`) | Gemini API | `gemini-3.8-flash-tts` | **Sulafat** (prebuilt voice, the user's pick) | The user's free-tier API key, in the macOS Keychain |
| Spanish (`es`) | ElevenLabs, through the claude.ai connector | `eleven_v4` | `zl7szWVBXnpgrJmAalgz` (Lily - Spanish Storyteller, voice library) | The user's ElevenLabs plan (Starter, 40,000 credits a month) |

Rules for both:
- **The same voice for every clip in a language**, in every story.
- **One take per clip.** Redo a clip only when it fails the checks below, or when the user asks.
- **Send each script exactly as written** in `REQUESTED_AUDIO.md`.

## English: Gemini 3.8 Flash TTS, voice Sulafat

### Limits: 10 clips a day, 1 a minute

- The free tier allows **10 clips a day**. The daily count resets at midnight Pacific time.
- **Wait at least a minute** between clips.
- A 40-clip story takes four days. Make each day's 10 in reading order, starting with the first clip still ⬜ in the story's `audio/en/REQUESTED_AUDIO.md`.
- The three lines at the end of every story (`content/app/audio/en/`) count against the same 10 a day.
- A request rejected for the limit (HTTP 429) is not a bad take. Stop and carry on the next day.

### Keep it free

- The key belongs to a Google project **with no billing**. AI Studio's API keys page shows it as "Free tier".
- Never set up billing for that project, and never make the key in `tale-time-b537d`: that project has billing, so its keys are paid.
- On the free tier, Google may use what is sent (the story text) and the audio it makes to improve its products. The user accepted this.

### The key

- It's stored in the macOS Keychain, service `gemini-api-key`. The user stored it with `security add-generic-password -a "$USER" -s gemini-api-key -U -w`.
- Read it inside the command that uses it, `GEMINI_API_KEY="$(security find-generic-password -s gemini-api-key -w)"`. Never print it or write it to a file, and never put it in the repo.
- Send it as the `x-goog-api-key` header, never in a URL.
- The script that calls the API is a throwaway in the scratchpad. The repo has no code that calls a model API (AGENTS.md, "No API keys").

### The request

One clip per request: `POST https://generativelanguage.googleapis.com/v1beta/interactions` with `Content-Type: application/json` and this body:

```json
{
  "model": "gemini-3.8-flash-tts",
  "input": [{"type": "user_input", "content": [{"type": "text", "text": "<the clip's script, exactly>"}]}],
  "response_format": {"type": "audio"},
  "generation_config": {"speech_config": [{"voice": "Sulafat"}]}
}
```

- The input has to be a list of steps (`"type": "user_input"`). A `"role": "user"` turn is rejected: "use step_list input format instead of turn_list".
- **The response** is JSON. The audio is the last item with `"type": "audio"` in `steps[].content[]`. Its `data` is a base64 WAV: 24 kHz, mono, 16-bit.
- **No style and no tags so far.** A short style goes in `"annotations": [{"type": "speech_metadata", "style": "slowly, with a little wonder"}]`. Use one only if the user asks, e.g. for a title that comes out too quick.
  - Google warns against long "director's notes", and against putting age, gender or accent in the style.
  - Inline tags use angle brackets (`<short pause>`).
- **Certificates:** python.org's Python on this Mac doesn't find the system certificates. Run it with `SSL_CERT_FILE=/etc/ssl/cert.pem`.

## Spanish: ElevenLabs, voice Lily

- Use the ElevenLabs connector's `creative_generate_speech` with `eleven_v4` and voice `zl7szWVBXnpgrJmAalgz`.
- Always set `generations_count: 1`. The connector's default is two takes, which doubles the credits.
- **Send words only.** The text sent is the clip's script exactly as written, with its tags in front, and nothing else. The voice reads every symbol aloud: the slashes of `/tuk/` (IPA) came out as "barra" in *El zorro y el farolito*. So never send slashes, IPA, phonetic spellings, quotes or parentheses around a name, notes, clip ids or markdown.
- **Tags:** start each script with a `[warmly]` tag, and add `[curious]` on questions. The end of every story's `the-end` ("¡Fin!") starts with `[cheerfully]` instead: it's joyful.
- **Names:** a name the voice would read wrong is respelled in plain Spanish letters, following its `pronounce` hint in `translations/es.json`: Tuck is sent as `Tuk`. Names that Spanish already reads as their hint (Toc, Nibs) stay as written.
- **Check before sending:** compare the text with the script. Only the tags and the plain-letter respellings above may differ. A `/`, `\`, `*`, `_`, `#`, `<`, `>`, `{`, `}` or `|` that isn't in the script means stop and fix it.
- Credits: 1 per character per take.

## Checks before delivery (both)

- **Pace:** words per minute close to the story's pace (`voice.json`: 140 for ages 4–7; the end of every story, the slowest: 115). Slower is better than faster.
- **Endings:** no clip is cut off, and none has a long gap or added words.
- **Noise:** the noise floor is about −60 dBFS or lower.
- **Listening:** the user listens for wrong or skipped words and how the names come out. Claude can't hear the clips.

## Mastering and delivery (both)

1. Pad the silence to about 0.3 s before the first word and 0.5 s after the last. Don't trim or edit anything else.
2. Set the gain to about −16 LUFS integrated, with a fast peak limiter, so the true peak is at most −1 dBTP.
3. Write a mono 24-bit WAV, at 48 kHz from Gemini (upsampled from 24 kHz) or 44.1 kHz from ElevenLabs.
4. Save it as `content/stories/<id>/audio/<lang>/<clip id>.wav`, or `content/app/audio/<lang>/<clip id>.wav` for the end of every story.
5. Add a `narration` step to the story's `provenance.json` with its `language`, `via`, `model`, `voice`, `date`, `clips` and notes. The end of every story has none.
6. Run `cd tools && uv run story assets` (the ⬜ entries turn ✅).
7. Run `uv run story originals push` (a private backup, no ask needed).
8. Only if the user asks, run `uv run story publish draft` so debug builds get the clips.
