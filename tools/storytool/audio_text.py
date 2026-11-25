"""The wording of each language's REQUESTED_AUDIO.md. A story's narration in one language is requested in that
language, so its narrator can read every word of the file: the labels and explanations come from here, the narrator
from content/style/voice.json (`narrators.<lang>`), the names, refrains and moods from the bible and shots.json (the
first language) or translations/<lang>.json (the others).

A language can be requested once it's here and has a narrator in voice.json; `story check` asks for both.
"""

TEXT: dict[str, dict] = {
    "en": {
        "languages": {"en": "English", "es": "Latin American Spanish"},
        "title": "Requested audio: {title} ({language})",
        "intro": "Every narration clip *{title}* needs in {language}, with its exact script and voice direction. "
                 "**No audio in this project is generated: no text-to-speech and no voice models run in the repo or "
                 "the pipeline.** Each clip is recorded or produced outside the repo and dropped into this folder, "
                 "named exactly as shown.",
        "draft": "**The {language} text is a draft,** waiting for a native speaker to read every path (the story's "
                 "`review.md`). Record it once the translation is approved (`\"status\": \"approved\"` in "
                 "`translations/{lang}.json`), so no clip has to be made twice.",
        "generated": "This file is generated. To change a request, edit this story's `story/{lang}.json` (the words), "
                     "{sources}, or `content/style/voice.json` (the narrator), then run `cd tools && uv run story "
                     "assets`. Run it "
                     "again after delivering files, and the ⬜ entries turn ✅. The pictures, the same in every "
                     "language, are requested in [REQUESTED_ASSETS.md]({assets}); every story and language is listed "
                     "in the [index]({index}).",
        "sources-first": "`bible.json` (names and personalities) or `shots.json` (each page's mood)",
        "sources-translation": "`translations/{lang}.json` (names, refrains, personalities and moods in {language})",
        "columns": ["Title", "Pages", "Questions", "Choice labels", "Ending titles", "Total"],
        "folder": "Folder",
        "pace": "Pace",
        "pace-value": "about {wpm} words per minute ({band}).",
        "tone": "Tone",
        "script": "Script",
        "script-value": "{clips} clips, {words} words, {chars} characters, {duration} in all.",
        "narrator": "Narrator: {name} ({status})",
        "status": {"proposed": "proposed, not yet approved", "approved": "approved"},
        "narrator-fields": {"voice": "Voice", "accent": "Accent", "delivery": "Delivery", "characters": "Characters",
                            "soundWords": "Sound words", "pauses": "Pauses"},
        "narrator-pace": "**Pace** (words per minute; slower is better than faster): {pace}.",
        "never": "**Never, in any clip:**",
        "delivery": "How to deliver",
        "delivery-rules": [
            "**Name** each file after its clip id, in this folder: `<clip id>.wav` (or `.flac` / `.m4a`).",
            "**One narrator** for a whole story, in one session if possible, so every clip sounds the same.",
            "**Word timings aren't needed.** The pipeline will align each clip with its text to light up the words "
            "(plan.md §5.2, step 8).",
            "**Packaging** turns the delivered files into mono AAC `.m4a` at about 48 kbps and evens out the loudness. "
            "Nothing else changes: no edits, no effects.",
            "**Licenses**: keep the release or license for every voice next to the files (a recorded person's written "
            "release, or a voice tool's terms that allow commercial distribution in an app), and note the source in "
            "the story's `provenance.json`.",
        ],
        "tech-header": "| | Every clip |",
        "tech": [
            ("Format", "WAV (preferred) or FLAC. AAC `.m4a` at 128 kbps or more only if nothing else is possible."),
            ("Channels", "Mono."),
            ("Sample rate and depth", "48 kHz (44.1 kHz is fine), 24-bit (16-bit is fine)."),
            ("Loudness", "About −16 LUFS integrated, true peak at most −1 dBTP, the same level for every clip. "
                         "Packaging evens out the last dB."),
            ("Silence", "About 0.3 s before the first word and 0.5 s after the last. No fade over a word."),
            ("Room", "Quiet and dry: no hum, hiss, echo or reverb (noise floor below about −60 dBFS)."),
            ("Content", "Exactly the script in its entry, read once, and nothing else."),
        ],
        "who": "Who's in it",
        "who-intro": "Names first, then how each one's lines should color the narrator's voice.",
        "say": "say: {pronounce}",
        "refrain": "Refrain, the same way every time: \"{refrain}\"",
        "clips": "Clips",
        "clips-intro": "In reading order. A fork's question and its two choice labels follow its page.",
        "kinds": {"title": "Title", "page": "Page", "question": "Question", "option": "Choice label",
                  "ending": "Ending title"},
        "page-fork": "Page (before a choice)",
        "page-ending": "Page (an ending)",
        "file": "File",
        "file-or": "(or `.flac` / `.m4a`)",
        "direction": "Direction",
        "mood": "Mood: {mood}.",
        "question": "The two choices: \"{a}\" or \"{b}\".",
        "option": "Choice {letter} for *{question}* The other label: \"{other}\".",
        "ending": "This ending is {kind}: {how}.",
        "ending-kinds": {"best": "the best one", "happy": "happy", "cozy": "cozy", "silly": "silly"},
        "length": "Length",
        "length-value": "{words} {unit}, {duration}.",
        "word": ("word", "words"),
        "seconds": "about {n} s",
        "minutes": "about {n} min",
        "bands": {"3-5": "ages 3–5", "4-7": "ages 4–7", "7-10": "ages 7–10"},
        "thousands": ",",
    },
    "es": {
        "languages": {"en": "inglés", "es": "español latinoamericano"},
        "title": "Narración solicitada: {title} ({language})",
        "intro": "Cada clip de narración que necesita *{title}* en {language}, con su guion exacto y su indicación de "
                 "voz. **En este proyecto no se genera audio: ni texto a voz ni modelos de voz, ni en el repositorio "
                 "ni en el proceso.** Cada clip se graba o se produce fuera del repositorio y se deja en esta carpeta, "
                 "con el nombre exacto que aparece aquí.",
        "draft": "**El texto en {language} es un borrador:** falta que una persona nativa lea todos los caminos (el "
                 "`review.md` del cuento). Grábalo cuando la traducción esté aprobada (`\"status\": \"approved\"` en "
                 "`translations/{lang}.json`), para no tener que hacer ningún clip dos veces.",
        "generated": "Este archivo se genera solo. Para cambiar un pedido, edita el `story/{lang}.json` de este cuento (las "
                     "palabras), {sources}, o `content/style/voice.json` (la voz), y luego ejecuta `cd tools && uv run "
                     "story assets`. Vuelve a ejecutarlo después de entregar archivos y los ⬜ pasan a ✅. Las "
                     "ilustraciones, las mismas en todos los idiomas, se piden en [REQUESTED_ASSETS.md]({assets}); "
                     "todos los cuentos e idiomas están en el [índice]({index}).",
        "sources-first": "`bible.json` (nombres y personalidades) o `shots.json` (el ánimo de cada página)",
        "sources-translation": "`translations/{lang}.json` (nombres, estribillos, personalidades y ánimo de cada "
                               "página en {language})",
        "columns": ["Título", "Páginas", "Preguntas", "Opciones", "Títulos de final", "Total"],
        "folder": "Carpeta",
        "pace": "Ritmo",
        "pace-value": "unas {wpm} palabras por minuto ({band}).",
        "tone": "Tono",
        "script": "Guion",
        "script-value": "{clips} clips, {words} palabras, {chars} caracteres, {duration} en total.",
        "narrator": "Voz: {name} ({status})",
        "status": {"proposed": "propuesta, aún sin aprobar", "approved": "aprobada"},
        "narrator-fields": {"voice": "Voz", "accent": "Acento", "delivery": "Lectura", "characters": "Personajes",
                            "soundWords": "Onomatopeyas", "pauses": "Pausas"},
        "narrator-pace": "**Ritmo** (palabras por minuto; mejor más lento que más rápido): {pace}.",
        "never": "**Nunca, en ningún clip:**",
        "delivery": "Cómo entregar",
        "delivery-rules": [
            "**Nombra** cada archivo con el id de su clip, en esta carpeta: `<id del clip>.wav` (o `.flac` / `.m4a`).",
            "**Una sola voz** para todo el cuento, en una sola sesión si se puede, para que todos los clips suenen "
            "igual.",
            "**No hacen falta los tiempos de cada palabra.** El proceso alinea cada clip con su texto para iluminar "
            "las palabras (plan.md §5.2, paso 8).",
            "**Al empaquetar**, los archivos entregados se convierten a AAC mono `.m4a`, a unos 48 kbps, y se empareja "
            "el volumen. Nada más cambia: ni cortes, ni efectos.",
            "**Licencias**: guarda la autorización o la licencia de cada voz junto a los archivos (la autorización "
            "escrita de la persona grabada, o los términos de una herramienta de voz que permitan distribuirla en una "
            "app comercial), y anota el origen en el `provenance.json` del cuento.",
        ],
        "tech-header": "| | Cada clip |",
        "tech": [
            ("Formato", "WAV (preferido) o FLAC. AAC `.m4a` de 128 kbps o más, solo si no hay otra opción."),
            ("Canales", "Mono."),
            ("Frecuencia y profundidad", "48 kHz (44,1 kHz está bien), 24 bits (16 bits está bien)."),
            ("Volumen", "Unos −16 LUFS integrados, pico real de −1 dBTP como máximo, el mismo nivel en cada clip. Al "
                        "empaquetar se ajusta el último dB."),
            ("Silencio", "Unos 0,3 s antes de la primera palabra y 0,5 s después de la última. Sin fundidos sobre una "
                         "palabra."),
            ("Sala", "Silenciosa y seca: sin zumbido, siseo, eco ni reverberación (ruido de fondo por debajo de unos "
                     "−60 dBFS)."),
            ("Contenido", "Exactamente el guion de su entrada, leído una vez, y nada más."),
        ],
        "who": "Quién aparece",
        "who-intro": "Primero los nombres, luego cómo las frases de cada personaje deben teñir la voz del narrador.",
        "say": "se dice: {pronounce}",
        "refrain": "Estribillo, igual cada vez: \"{refrain}\"",
        "clips": "Clips",
        "clips-intro": "En orden de lectura. La pregunta de una página con opciones y sus dos opciones van después de "
                       "la página.",
        "kinds": {"title": "Título", "page": "Página", "question": "Pregunta", "option": "Opción",
                  "ending": "Título del final"},
        "page-fork": "Página (antes de elegir)",
        "page-ending": "Página (un final)",
        "file": "Archivo",
        "file-or": "(o `.flac` / `.m4a`)",
        "direction": "Indicación",
        "mood": "Ánimo: {mood}.",
        "question": "Las dos opciones: \"{a}\" o \"{b}\".",
        "option": "Opción {letter} de *{question}* La otra opción: \"{other}\".",
        "ending": "Este final es {kind}: {how}.",
        "ending-kinds": {"best": "el mejor", "happy": "feliz", "cozy": "tranquilo", "silly": "chistoso"},
        "length": "Duración",
        "length-value": "{words} {unit}, {duration}.",
        "word": ("palabra", "palabras"),
        "seconds": "unos {n} s",
        "minutes": "unos {n} min",
        "bands": {"3-5": "de 3 a 5 años", "4-7": "de 4 a 7 años", "7-10": "de 7 a 10 años"},
        "thousands": " ",
    },
}


def text(lang: str) -> dict:
    return TEXT[lang]
