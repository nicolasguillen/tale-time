@AGENTS.md

## Claude-specific notes

- `AGENTS.md` (imported above) holds the shared instructions for every coding agent. Put project knowledge **there**, not here.
- The image and audio rule in AGENTS.md is absolute: never call an image-generation or text-to-speech tool or API, and never draw illustrations in code, even when a step of plan.md says "generate" or "narration". Write the request into the sources (`shots.json`, `bible.json`, the story's `style/style.json`, `story/<lang>.json`, `translations/<lang>.json`, `voice.json`, `content/app/assets.json`) and run `uv run story assets` instead.
- Build and test output is long. Redirect it to a log file in the scratchpad and grep for `BUILD`, `e: `, `error:` or `FAILED` instead of dumping it into the context. Allow up to 10 minutes for Gradle and Xcode builds.
- After visual changes, take an emulator or simulator screenshot and actually look at it before reporting done. Shut down any emulator or simulator you booted.
