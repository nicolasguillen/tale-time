# Tale Time

A picture-book choose-your-own-adventure app for kids. Each page shows a picture and a short paragraph read aloud, and at each fork the child picks one of two illustrated choices. Every story has several endings to find. Some stories are free, others are a one-time purchase.

- **Plan:** [plan.md](plan.md)
- **Story and catalog format:** [docs/story-format.md](docs/story-format.md)
- **Images still needed:** [REQUESTED_ASSETS.md](REQUESTED_ASSETS.md). **Narration still needed:** [REQUESTED_AUDIO.md](REQUESTED_AUDIO.md). Those two are indexes: they hold the status and link each story's own requests, its images in `content/stories/<id>/REQUESTED_ASSETS.md` and its narration once per language in `content/stories/<id>/audio/<lang>/REQUESTED_AUDIO.md`, written in that language. Nothing in the repo generates them: each image is requested with a prompt, generated with an AI image tool outside the repo and dropped in; each clip is requested there and delivered by hand. Until then, static placeholders stand in for pictures and stories play without narration.
- **Stories:** `content/stories/`, on the shelf through `content/catalog.json` (free or paid), in English and Latin American Spanish. Write and translate them in Claude Code with `/new-story` (no API key: the CLI never calls a model). The app's language is set in its parents area.

## Content pipeline

```
cd tools
uv run story check                         # validate the catalog and every story
uv run story status                        # which steps are done, what's next
uv run story paths fox-and-the-lantern     # read every page and choice (add --lang es for the Spanish)
uv run story assets                        # regenerate the REQUESTED_ASSETS.md and REQUESTED_AUDIO.md files (root, per story, per language)
uv run pytest -q
```

## App (Kotlin Multiplatform, Compose Multiplatform)

- `shared/`: all the app code and UI. `androidApp/` and `iosApp/` are thin hosts.
- **Android:** `./gradlew :androidApp:installDebug`, or run `androidApp` from Android Studio.
- **iOS:** open `iosApp/iosApp.xcodeproj` in Xcode and run. The project is generated from `iosApp/project.yml` with XcodeGen (`cd iosApp && xcodegen generate`).
- **Tests:** `./gradlew :shared:allTests` (JVM + iOS simulator), or `./gradlew :shared:testAndroidHostTest` for the JVM only. **Lint:** `./gradlew ktlintCheck`.
- No story ships inside the app: each is downloaded in the language a reader picks, kept for offline reading and updated when the app opens online. `story package` builds the downloads from `content/`, `story serve` serves them to debug builds, `story publish` uploads them (see AGENTS.md, "Distribution").

Firebase project: `tale-time-b537d` (Storage for story downloads, rules in `backend/`).
